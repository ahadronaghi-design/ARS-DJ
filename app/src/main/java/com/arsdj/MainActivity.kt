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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 20, 38)
        window.navigationBarColor = android.graphics.Color.rgb(7, 20, 38)
        setContent {
            ARSTheme {
                val permission = if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                ARSApp(
                    hasPermission = {
                        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                    },
                    scanTracks = {
                        withContext(Dispatchers.IO) { MediaStoreScanner(this@MainActivity).loadAudio() }
                    },
                    createMix = { chosen, fade, volume, bass, destination ->
                        val tempDir = File(cacheDir, "ars_dj_output").apply { mkdirs() }
                        val temp = File(tempDir, "ARS_DJ_Mix_${System.currentTimeMillis()}.mp3")
                        AudioMixEngine(this@MainActivity).mix(chosen, temp, fade, volume, bass)
                        withContext(Dispatchers.IO) {
                            val out = contentResolver.openOutputStream(destination, "w")
                                ?: error("امکان نوشتن در محل انتخابی وجود ندارد.")
                            out.use { target -> temp.inputStream().use { it.copyTo(target) } }
                        }
                        temp
                    },
                    playFile = { file ->
                        player?.release()
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnPreparedListener { it.start() }
                            prepareAsync()
                        }
                    },
                    togglePlayback = {
                        player?.let { p ->
                            if (p.isPlaying) p.pause() else runCatching { p.start() }
                        }
                    },
                    stopPlayback = {
                        player?.release()
                        player = null
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun ARSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, secondary = Violet, background = Navy, surface = Panel,
            onPrimary = Navy, onSecondary = TextMain, onBackground = TextMain, onSurface = TextMain
        ), content = content
    )
}

@Composable
private fun ARSApp(
    hasPermission: () -> Boolean,
    scanTracks: suspend () -> List<AudioTrack>,
    createMix: suspend (List<AudioTrack>, Int, Float, Float, android.net.Uri) -> File,
    playFile: (File) -> Unit,
    togglePlayback: () -> Unit,
    stopPlayback: () -> Unit
) {
    var page by remember { mutableStateOf("home") }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var status by remember { mutableStateOf("برای شروع، آهنگ‌هایت را انتخاب کن.") }
    var loading by remember { mutableStateOf(false) }
    var mixing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var fade by remember { mutableFloatStateOf(4f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var bass by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                loading = true
                runCatching { scanTracks() }
                    .onSuccess { tracks = it; status = "${it.size} آهنگ پیدا شد." }
                    .onFailure { status = "خطا در خواندن آهنگ‌ها: ${it.localizedMessage}" }
                loading = false
            }
        } else {
            status = "برای نمایش آهنگ‌های دستگاه، مجوز موسیقی لازم است."
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        if (uri == null) status = "ذخیره لغو شد."
        else scope.launch {
            mixing = true
            status = "در حال ساخت و ذخیره میکس..."
            try {
                outputFile = createMix(tracks.filter { selected.contains(it.id) }, fade.toInt(), volume, bass, uri)
                status = "میکس ساخته و ذخیره شد."
            } catch (e: Exception) {
                status = "خطا در ساخت میکس: ${e.localizedMessage ?: "خطای نامشخص"}"
            } finally { mixing = false }
        }
    }

    fun loadLibrary() {
        if (!hasPermission()) {
            permissionLauncher.launch(permission)
            return
        }
        scope.launch {
            loading = true
            runCatching { scanTracks() }
                .onSuccess { tracks = it; status = "${it.size} آهنگ پیدا شد." }
                .onFailure { status = "خطا در خواندن آهنگ‌ها: ${it.localizedMessage}" }
            loading = false
        }
    }

    val chosen = tracks.filter { selected.contains(it.id) }
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
                Box(Modifier.size(40.dp).clip(CircleShape).background(Panel).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✦", color = Violet, fontSize = 20.sp)
                }
            }

            when (page) {
                "home" -> Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("میکس هوشمند، با امضای تو", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text("آهنگ‌ها را انتخاب کن، تنظیمات را تغییر بده و خروجی MP3 بساز.", color = TextMuted)
                    Waveform(Modifier.fillMaxWidth().height(70.dp))
                    Button(onClick = { page = "library"; loadLibrary() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)) { Text("انتخاب آهنگ‌ها") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard("میکس", "تنظیمات و خروجی", "≋", Modifier.weight(1f)) { page = "mix" }
                        FeatureCard("هوش مصنوعی", "بخش آزمایشی", "✧", Modifier.weight(1f)) { page = "ai" }
                    }
                }
                "library" -> Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    Text("کتابخانه موسیقی", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { loadLibrary() }) { Text("بارگذاری دوباره") }
                        Button(onClick = { page = "mix" }, enabled = selected.isNotEmpty()) { Text("انتخاب‌ها (${selected.size})") }
                    }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tracks, key = { it.id }) { track ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selected.contains(track.id), onCheckedChange = { checked ->
                                    selected = if (checked) selected + track.id else selected - track.id
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = TextMain, maxLines = 1)
                                    Text(track.artist, color = TextMuted, fontSize = 12.sp)
                                }
                                Text(formatDuration(track.durationMs), color = Cyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
                else -> Column(Modifier.weight(1f).fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (page == "mix") "میکس هوشمند" else if (page == "ai") "استودیوی صدای مصنوعی" else "خروجی‌ها", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (page == "mix") {
                        Text("آهنگ‌های انتخاب‌شده: ${chosen.size}", color = TextMuted)
                        Text("مدت انتقال: ${fade.toInt()} ثانیه", color = Cyan)
                        Slider(value = fade, onValueChange = { fade = it }, valueRange = 1f..8f, steps = 6)
                        Text("میزان صدا: ${(volume * 100).toInt()}٪", color = Cyan)
                        Slider(value = volume, onValueChange = { volume = it }, valueRange = 0.25f..1.5f)
                        Text("تقویت بیس: ${bass.toInt()} dB", color = Cyan)
                        Slider(value = bass, onValueChange = { bass = it }, valueRange = 0f..12f, steps = 11)
                        Waveform(Modifier.fillMaxWidth().height(72.dp))
                        Text(status, color = if (status.contains("خطا")) Color(0xFFFF7373) else TextMuted, fontSize = 12.sp)
                        Button(
                            onClick = { saveLauncher.launch("ARS_DJ_Mix_${System.currentTimeMillis()}.mp3") },
                            enabled = chosen.isNotEmpty() && !mixing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)
                        ) { Text(if (mixing) "در حال ساخت..." else "انتخاب محل ذخیره و ساخت MP3") }
                        if (mixing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                        outputFile?.let { file ->
                            Text("فایل ساخته شده؛ برای پخش از دکمه‌ها استفاده کن.", color = Cyan)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { runCatching { playFile(file); status = "در حال آماده‌سازی پخش..." }.onFailure { status = "خطای پخش: ${it.localizedMessage}" } }) { Text("▶ پخش") }
                                OutlinedButton(onClick = { togglePlayback(); status = "پخش مکث/ادامه شد." }) { Text("⏯ مکث/ادامه") }
                                OutlinedButton(onClick = { stopPlayback(); status = "پخش متوقف شد." }) { Text("■ توقف") }
                            }
                            Text(file.absolutePath, color = TextMuted, fontSize = 10.sp)
                        }
                    } else {
                        Text(if (page == "ai") "تولید صدای مصنوعی هنوز پیاده‌سازی نشده است." else outputFile?.absolutePath ?: "هنوز فایلی ساخته نشده است.", color = TextMuted)
                    }
                    Button(onClick = { page = "home" }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)) { Text("بازگشت به خانه") }
                }
            }

            NavigationBar(containerColor = Color(0xFF08172A), contentColor = Cyan) {
                listOf(Triple("home", "خانه", "⌂"), Triple("library", "موسیقی", "♫"), Triple("mix", "میکس", "≋"), Triple("ai", "هوش مصنوعی", "✧")).forEach { (id, label, glyph) ->
                    NavigationBarItem(selected = page == id, onClick = { page = id; if (id == "library" && tracks.isEmpty()) loadLibrary() }, icon = { Text(glyph, fontSize = 20.sp) }, label = { Text(label, fontSize = 10.sp) })
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x2227CFFF)), contentAlignment = Alignment.Center) { Text(glyph, color = Cyan, fontSize = 23.sp) }
        Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(subtitle, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ARSLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = w * .105f
        drawLine(Brush.linearGradient(listOf(Cyan, Color(0xFF32A8FF))), Offset(w*.18f,h*.78f), Offset(w*.50f,h*.15f), stroke, cap = StrokeCap.Round)
        drawLine(Brush.linearGradient(listOf(Color(0xFF7778FF),Violet)), Offset(w*.50f,h*.15f), Offset(w*.82f,h*.78f), stroke, cap = StrokeCap.Round)
        drawLine(Color(0xFF4C7BFF), Offset(w*.34f,h*.48f), Offset(w*.66f,h*.48f), stroke*.72f, cap = StrokeCap.Round)
    }
}

@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    val heights = listOf(.18f,.34f,.24f,.50f,.78f,.43f,.63f,.91f,.57f,.35f,.72f,.96f,.61f,.42f,.78f,.52f,.30f,.66f,.86f,.48f,.25f,.54f,.73f,.38f,.19f,.45f,.63f,.30f,.18f,.40f,.58f,.27f)
    Canvas(modifier) {
        val gap = size.width / (heights.size * 1.6f); val bw = gap * .55f
        heights.forEachIndexed { i, v ->
            val x = i * gap + bw; val bh = size.height * v
            drawLine(Brush.verticalGradient(listOf(Cyan, if (i > heights.size*.63) Violet else Color(0xFF167CDA))), Offset(x,(size.height-bh)/2), Offset(x,(size.height+bh)/2), bw, cap = StrokeCap.Round)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
