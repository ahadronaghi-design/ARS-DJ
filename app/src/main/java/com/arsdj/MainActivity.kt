package com.arsdj

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arsdj.library.AudioAnalysisEngine
import com.arsdj.library.AudioMixEngine
import com.arsdj.library.AudioTrack
import com.arsdj.library.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF0D2038)
private val Cyan = Color(0xFF00D4FF)
private val Violet = Color(0xFF8260FF)
private val TextMain = Color(0xFFF4F8FF)
private val TextMuted = Color(0xFF9BB0C9)
private val Line = Color(0xFF24415F)
private val ErrorRed = Color(0xFFFF7373)

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var previewTempFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 20, 38)
        window.navigationBarColor = android.graphics.Color.rgb(7, 20, 38)

        setContent {
            ARSTheme {
                val permission = if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE

                ARSApp(
                    hasPermission = {
                        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                    },
                    scanTracks = {
                        withContext(Dispatchers.IO) {
                            MediaStoreScanner(this@MainActivity).loadAudio()
                        }
                    },
                    analyzeTrack = { track ->
                        withContext(Dispatchers.IO) {
                            val temp = File(cacheDir, "arsdj_analyze_${track.id}_${System.nanoTime()}.audio")
                            try {
                                val input = contentResolver.openInputStream(track.uri)
                                    ?: error("خواندن آهنگ ممکن نیست: ${track.title}")
                                input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                                AudioAnalysisEngine.analyze(temp.absolutePath, cacheDir)
                            } finally {
                                temp.delete()
                            }
                        }
                    },
                    createMix = { chosen, fade, volume, bass, destination ->
                        val tempDir = File(cacheDir, "ars_dj_output").apply { mkdirs() }
                        val temp = File(tempDir, "ARS_DJ_Mix_${System.currentTimeMillis()}.mp3")
                        try {
                            AudioMixEngine(this@MainActivity).mix(chosen, temp, fade, volume, bass)
                            withContext(Dispatchers.IO) {
                                val out = contentResolver.openOutputStream(destination, "w")
                                    ?: error("امکان نوشتن در محل انتخابی وجود ندارد.")
                                out.use { target -> temp.inputStream().use { it.copyTo(target) } }
                            }
                            temp
                        } catch (e: Exception) {
                            temp.delete()
                            throw e
                        }
                    },
                    playFile = { file -> playLocalFile(file) },
                    previewTrack = { track ->
                        val temp = File(cacheDir, "arsdj_preview_${track.id}.audio")
                        val input = contentResolver.openInputStream(track.uri)
                            ?: error("خواندن آهنگ ممکن نیست: ${track.title}")
                        input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                        previewTempFile?.delete()
                        previewTempFile = temp
                        playLocalFile(temp)
                    },
                    togglePlayback = {
                        player?.let { p ->
                            if (p.isPlaying) p.pause() else runCatching { p.start() }
                        }
                    },
                    stopPlayback = { stopPlayer() }
                )
            }
        }
    }

    private fun playLocalFile(file: File) {
        stopPlayer()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start() }
            setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; true }
            prepareAsync()
        }
    }

    private fun stopPlayer() {
        runCatching { player?.stop() }
        player?.release()
        player = null
    }

    override fun onDestroy() {
        stopPlayer()
        previewTempFile?.delete()
        super.onDestroy()
    }
}

@Composable
private fun ARSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, secondary = Violet, background = Navy, surface = Panel,
            onPrimary = Navy, onSecondary = TextMain, onBackground = TextMain, onSurface = TextMain
        ),
        content = content
    )
}

@Composable
private fun ARSApp(
    hasPermission: () -> Boolean,
    scanTracks: suspend () -> List<AudioTrack>,
    analyzeTrack: suspend (AudioTrack) -> AudioAnalysisEngine.AnalysisResult,
    createMix: suspend (List<AudioTrack>, Int, Float, Float, android.net.Uri) -> File,
    playFile: (File) -> Unit,
    previewTrack: (AudioTrack) -> Unit,
    togglePlayback: () -> Unit,
    stopPlayback: () -> Unit
) {
    var page by remember { mutableStateOf("home") }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var status by remember { mutableStateOf("برای شروع، آهنگ‌هایت را انتخاب کن.") }
    var loading by remember { mutableStateOf(false) }
    var mixing by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableIntStateOf(0) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var fade by remember { mutableFloatStateOf(4f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var useSuggestedOrder by remember { mutableStateOf(true) }
    val analysisResults = remember { mutableStateMapOf<Long, AudioAnalysisEngine.AnalysisResult>() }
    val scope = rememberCoroutineScope()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadLibrary(scope, scanTracks, { tracks = it }, { status = it }, { loading = it })
        else status = "برای نمایش آهنگ‌های دستگاه، مجوز موسیقی لازم است."
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        if (uri == null) status = "ذخیره لغو شد."
        else scope.launch {
            val selectedTracks = orderedSelectedTracks(tracks, selected, analysisResults, useSuggestedOrder)
            if (selectedTracks.isEmpty()) {
                status = "ابتدا حداقل یک آهنگ انتخاب کن."
                return@launch
            }
            mixing = true
            status = "در حال ساخت و ذخیره میکس..."
            try {
                outputFile = createMix(selectedTracks, fade.toInt(), volume, bass, uri)
                status = "میکس ساخته و در محل انتخابی ذخیره شد."
            } catch (e: Exception) {
                status = "خطا در ساخت میکس: ${e.localizedMessage ?: "خطای نامشخص"}"
            } finally {
                mixing = false
            }
        }
    }

    fun loadLibrary() {
        if (!hasPermission()) {
            permissionLauncher.launch(permission)
            return
        }
        loadLibrary(scope, scanTracks, { tracks = it }, { status = it }, { loading = it })
    }

    val chosen = orderedSelectedTracks(tracks, selected, analysisResults, useSuggestedOrder)

    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ARSLogo(Modifier.size(42.dp))
                    Column {
                        Text("ARS DJ", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextMain)
                        Text("AI MUSIC STUDIO", fontSize = 9.sp, letterSpacing = 2.sp, color = Cyan)
                    }
                }
                Text("✦", color = Violet, fontSize = 22.sp)
            }

            when (page) {
                "home" -> Column(
                    Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("میکس هوشمند، با امضای تو", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text("آهنگ‌ها را تحلیل کن، ترتیب را انتخاب کن و خروجی MP3 بساز.", color = TextMuted)
                    Waveform(Modifier.fillMaxWidth().height(70.dp))
                    Button(
                        onClick = { page = "library"; loadLibrary() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)
                    ) { Text("انتخاب آهنگ‌ها") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard("میکس", "تنظیمات و خروجی", "≋", Modifier.weight(1f)) { page = "mix" }
                        FeatureCard("هوش مصنوعی", "BPM و انرژی", "✧", Modifier.weight(1f)) { page = "ai" }
                    }
                    Text(status, color = if (status.contains("خطا")) ErrorRed else TextMuted, fontSize = 12.sp)
                }

                "library" -> Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    Text("کتابخانه موسیقی", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { loadLibrary() }) { Text("بارگذاری دوباره") }
                        Button(onClick = { page = "mix" }, enabled = selected.isNotEmpty()) {
                            Text("انتخاب‌ها (${selected.size})")
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tracks, key = { it.id }) { track ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected.contains(track.id), onCheckedChange = { checked ->
                                    selected = if (checked) selected + track.id else selected - track.id
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = TextMain, maxLines = 1)
                                    Text(track.artist, color = TextMuted, fontSize = 12.sp)
                                }
                                Text(formatDuration(track.durationMs), color = Cyan, fontSize = 11.sp)
                                IconButton(onClick = {
                                    runCatching { previewTrack(track); status = "پیش‌نمایش: ${track.title}" }
                                        .onFailure { status = "خطای پخش: ${it.localizedMessage}" }
                                }) { Text("▶", color = Cyan) }
                            }
                        }
                    }
                }

                "ai" -> Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("تحلیل هوشمند آهنگ‌ها", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "BPM و انرژی به‌صورت آفلاین و تقریبی محاسبه می‌شوند. برای تحلیل، آهنگ‌ها را انتخاب کن؛ اگر انتخابی نباشد کل کتابخانه بررسی می‌شود.",
                        color = TextMuted, fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            val targets = if (selected.isEmpty()) tracks else tracks.filter { selected.contains(it.id) }
                            if (targets.isEmpty()) {
                                status = "ابتدا کتابخانه را بارگذاری کن."
                            } else {
                                scope.launch {
                                    analyzing = true
                                    analysisProgress = 0
                                    status = "تحلیل شروع شد..."
                                    for (track in targets) {
                                        runCatching { withContext(Dispatchers.IO) { analyzeTrack(track) } }
                                            .onSuccess { analysisResults[track.id] = it }
                                            .onFailure { status = "خطا در تحلیل ${track.title}: ${it.localizedMessage}" }
                                        analysisProgress++
                                    }
                                    analyzing = false
                                    status = "تحلیل ${analysisProgress} آهنگ تمام شد."
                                }
                            }
                        },
                        enabled = !analyzing && tracks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)
                    ) { Text(if (analyzing) "در حال تحلیل..." else "تحلیل آهنگ‌های انتخاب‌شده / کتابخانه") }
                    if (analyzing) {
                        LinearProgressIndicator(
                            progress = if (tracks.isEmpty()) 0f else analysisProgress.toFloat() / (if (selected.isEmpty()) tracks.size else selected.size).coerceAtLeast(1),
                            modifier = Modifier.fillMaxWidth(), color = Cyan
                        )
                        Text("تحلیل‌شده: $analysisProgress", color = TextMuted, fontSize = 12.sp)
                    }
                    if (analysisResults.isNotEmpty()) {
                        val sorted = tracks.filter { analysisResults.containsKey(it.id) }
                            .sortedWith(compareBy<AudioTrack> { analysisResults[it.id]?.energyPercent ?: 0 }
                                .thenBy { analysisResults[it.id]?.estimatedBpm ?: 0 })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = useSuggestedOrder, onCheckedChange = { useSuggestedOrder = it })
                            Text("استفاده از ترتیب پیشنهادی بر اساس انرژی در میکس", color = TextMain, fontSize = 12.sp)
                        }
                        Text("ترتیب پیشنهادی: انرژی کمتر به بیشتر", color = Cyan, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sorted, key = { it.id }) { track ->
                                val result = analysisResults[track.id]
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(Panel).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(track.title, color = TextMain, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        "BPM: ${result?.estimatedBpm?.toString() ?: "نامشخص"}   •   انرژی: ${result?.energyPercent ?: 0}%   •   اطمینان: ${result?.confidence ?: "—"}",
                                        color = Cyan, fontSize = 12.sp
                                    )
                                    Text(result?.recommendation ?: "", color = TextMuted, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            runCatching { previewTrack(track); status = "پیش‌نمایش: ${track.title}" }
                                                .onFailure { status = "خطای پخش: ${it.localizedMessage}" }
                                        }) { Text("▶ پیش‌نمایش") }
                                        TextButton(onClick = {
                                            selected = selected + track.id
                                            page = "mix"
                                        }) { Text("افزودن به میکس") }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("هنوز نتیجه‌ای وجود ندارد.", color = TextMuted)
                        Spacer(Modifier.weight(1f))
                    }
                    Text(status, color = if (status.contains("خطا")) ErrorRed else TextMuted, fontSize = 11.sp)
                }

                else -> Column(
                    Modifier.weight(1f).fillMaxWidth().padding(2
