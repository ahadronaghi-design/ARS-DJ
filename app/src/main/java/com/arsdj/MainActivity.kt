package com.arsdj

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
private val Panel2 = Color(0xFF102A46)
private val Cyan = Color(0xFF00D4FF)
private val Violet = Color(0xFF8260FF)
private val TextMain = Color(0xFFF4F8FF)
private val TextMuted = Color(0xFF9BB0C9)
private val Line = Color(0xFF24415F)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(7, 20, 38)
        window.navigationBarColor = android.graphics.Color.rgb(7, 20, 38)

        setContent {
            ARSTheme {
                val permission =
                    if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                ARSApp(
                    hasPermission = {
                        ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    },

                    requestPermission = { _ ->
                        // Permission is requested by the composable.
                    },

                    scanTracks = {
                        withContext(Dispatchers.IO) {
                            MediaStoreScanner(
                                this@MainActivity
                            ).loadAudio()
                        }
                    },

                    mixTracks = { chosenTracks ->
                        val outputDir =
                            getExternalFilesDir(
                                Environment.DIRECTORY_MUSIC
                            ) ?: filesDir

                        val outputFile = File(
                            outputDir,
                            "ARS_DJ_Mix_${System.currentTimeMillis()}.mp3"
                        )

                        AudioMixEngine(
                            this@MainActivity
                        ).mix(
                            tracks = chosenTracks,
                            outputFile = outputFile,
                            crossfadeSeconds = 4
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ARSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Violet,
            background = Navy,
            surface = Panel,
            onPrimary = Navy,
            onSecondary = TextMain,
            onBackground = TextMain,
            onSurface = TextMain
        ),
        content = content
    )
}

@Composable
private fun ARSApp(
    hasPermission: () -> Boolean,
    requestPermission: ((Boolean) -> Unit) -> Unit,
    scanTracks: suspend () -> List<AudioTrack>,
    mixTracks: suspend (List<AudioTrack>) -> File
) {
    var page by remember { mutableStateOf("home") }

    var tracks by remember {
        mutableStateOf<List<AudioTrack>>(emptyList())
    }

    var selected by remember {
        mutableStateOf<Set<Long>>(emptySet())
    }

    var status by remember {
        mutableStateOf("برای شروع، آهنگ‌هایت را انتخاب کن.")
    }

    var loading by remember { mutableStateOf(false) }
    var mixing by remember { mutableStateOf(false) }

    var outputPath by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    val permission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                loading = true

                runCatching {
                    scanTracks()
                }.onSuccess {
                    tracks = it
                    status = "${it.size} آهنگ پیدا شد."
                }.onFailure {
                    status = "خطا در خواندن آهنگ‌ها: ${it.localizedMessage}"
                }

                loading = false
            }
        } else {
            status = "برای نمایش آهنگ‌های دستگاه، مجوز موسیقی لازم است."
        }
    }

    fun loadLibrary() {
        if (!hasPermission()) {
            launcher.launch(permission)
        } else {
            scope.launch {
                loading = true

                runCatching {
                    scanTracks()
                }.onSuccess {
                    tracks = it
                    status = "${it.size} آهنگ پیدا شد."
                }.onFailure {
                    status = "خطا در خواندن آهنگ‌ها: ${it.localizedMessage}"
                }

                loading = false
            }
        }
    }

    val chosenTracks = tracks.filter {
        selected.contains(it.id)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Navy
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ARSLogo(Modifier.size(42.dp))

                    Column {
                        Text(
                            "ARS DJ",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextMain
                        )

                        Text(
                            "AI MUSIC STUDIO",
                            fontSize = 9.sp,
                            letterSpacing = 2.sp,
                            color = Cyan
                        )
                    }
                }

                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Panel)
                        .border(1.dp, Line, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", color = Violet, fontSize = 20.sp)
                }
            }

            if (page == "home") {

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF102E4D),
                                        Color(0xFF151B42),
                                        Color(0xFF241746)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color(0xFF22517A),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(22.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x3322DFFF))
                                        .padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        )
                                ) {
                                    Text(
                                        "AI MIX ENGINE",
                                        color = Cyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    "● READY",
                                    color = Color(0xFF69F0AE),
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                text = "میکس هوشمند،\nبا امضای تو",
                                fontSize = 29.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMain
                            )

                            Text(
                                "آهنگ‌ها را انتخاب کن؛ ARS DJ انتقال‌های صوتی را اجرا می‌کند.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 22.sp
                            )

                            Waveform(
                                Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                            )

                            Button(
                                onClick = {
                                    page = "library"
                                    loadLibrary()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Cyan,
                                    contentColor = Navy
                                )
                            ) {
                                Text(
                                    "شروع سریع میکس",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.width(8.dp))
                                Text("▶")
                            }

                            Text(
                                "انتخاب چند آهنگ • انتقال نرم • خروجی MP3",
                                color = TextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Text(
                        "استودیوی تو",
                        color = TextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            "کتابخانه",
                            "آهنگ‌ها و پلی‌لیست‌ها",
                            "♫",
                            Modifier.weight(1f)
                        ) {
                            page = "library"
                            loadLibrary()
                        }

                        FeatureCard(
                            "میکس هوشمند",
                            "ضرب و انتقال",
                            "≋",
                            Modifier.weight(1f)
                        ) {
                            page = "mix"
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            "صدای مصنوعی",
                            "فارسی و ترکی",
                            "◉",
                            Modifier.weight(1f)
                        ) {
                            page = "ai"
                        }

                        FeatureCard(
                            "خروجی‌ها",
                            "مدیریت فایل‌ها",
                            "⇩",
                            Modifier.weight(1f)
                        ) {
                            page = "export"
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }

            } else if (page == "library") {

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        "کتابخانه موسیقی",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        status,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    if (loading) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = Cyan
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { loadLibrary() }
                        ) {
                            Text("بارگذاری دوباره")
                        }

                        Button(
                            onClick = { page = "mix" },
                            enabled = selected.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Violet
                            )
                        ) {
                            Text("انتخاب‌ها (${selected.size})")
                        }
                    }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Panel)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.contains(track.id),
                                    onCheckedChange = { checked ->
                                        selected =
                                            if (checked) {
                                                selected + track.id
                                            } else {
                                                selected - track.id
                                            }
                                    }
                                )

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        color = TextMain,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )

                                    Text(
                                        track.artist,
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    formatDuration(track.durationMs),
                                    color = Cyan,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

            } else {

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        when (page) {
                            "mix" -> "میکس هوشمند"
                            "ai" -> "استودیوی صدای مصنوعی"
                            else -> "خروجی‌ها"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        when (page) {
                            "mix" ->
                                "آهنگ‌های انتخاب‌شده: ${selected.size}\n" +
                                "آهنگ‌های انتخابی به ترتیب انتخاب‌شده در کتابخانه میکس می‌شوند."

                            "ai" ->
                                "مدیر موسیقی ARS بین آواز، آوای بدون کلام یا عدم افزودن صدا تصمیم خواهد گرفت. زبان هدف: تشخیص خودکار فارسی یا ترکی."

                            else ->
                                outputPath ?: "فایل‌های میکس ساخته‌شده در این بخش نمایش داده خواهند شد."
                        },
                        color = TextMuted,
                        lineHeight = 24.sp
                    )

                    if (page == "mix") {
                        Waveform(
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )

                        Text(
                            status,
                            color = if (status.contains("خطا")) {
                                Color(0xFFFF7373)
                            } else {
                                Violet
                            },
                            fontSize = 12.sp
                        )

                        if (mixing) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth(),
                                color = Cyan
                            )

                            Text(
                                "در حال ساخت فایل صوتی؛ لطفاً صبر کن...",
                                color = Cyan,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    mixing = true
                                    status = "در حال آماده‌سازی آهنگ‌ها..."
                                    outputPath = null

                                    try {
                                        val result = mixTracks(chosenTracks)

                                        outputPath = result.absolutePath
                                        status = "میکس با موفقیت ساخته شد."
                                    } catch (error: Exception) {
                                        status =
                                            "خطا در ساخت میکس: " +
                                            (error.localizedMessage
                                                ?: "خطای نامشخص")
                                    } finally {
                                        mixing = false
                                    }
                                }
                            },
                            enabled = chosenTracks.isNotEmpty() && !mixing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan,
                                contentColor = Navy,
                                disabledContainerColor = Panel2,
                                disabledContentColor = TextMuted
                            )
                        ) {
                            Text(
                                if (mixing) {
                                    "در حال ساخت میکس..."
                                } else {
                                    "ساخت فایل میکس MP3"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (chosenTracks.isEmpty()) {
                            Text(
                                "ابتدا از بخش موسیقی حداقل یک آهنگ انتخاب کن.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        outputPath?.let { path ->
                            Text(
                                "مسیر فایل خروجی:",
                                color = Cyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Text(
                                path,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            page = "home"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan,
                            contentColor = Navy
                        )
                    ) {
                        Text("بازگشت به خانه")
                    }
                }
            }

            NavigationBar(
                containerColor = Color(0xFF08172A),
                contentColor = Cyan
            ) {
                listOf(
                    Triple("home", "خانه", "⌂"),
                    Triple("library", "موسیقی", "♫"),
                    Triple("mix", "میکس", "≋"),
                    Triple("ai", "هوش مصنوعی", "✧")
                ).forEach { (id, label, glyph) ->
                    NavigationBarItem(
                        selected = page == id,
                        onClick = {
                            page = id

                            if (id == "library" && tracks.isEmpty()) {
                                loadLibrary()
                            }
                        },
                        icon = {
                            Text(glyph, fontSize = 20.sp)
                        },
                        label = {
                            Text(label, fontSize = 10.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = Color(0xFF12344D),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    glyph: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x2227CFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                glyph,
                color = Cyan,
                fontSize = 23.sp
            )
        }

        Text(
            title,
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Text(
            subtitle,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ARSLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.105f

        val cyanBrush = Brush.linearGradient(
            listOf(Cyan, Color(0xFF32A8FF))
        )

        val purpleBrush = Brush.linearGradient(
            listOf(Color(0xFF7778FF), Violet)
        )

        drawLine(
            cyanBrush,
            Offset(w * .18f, h * .78f),
            Offset(w * .50f, h * .15f),
            stroke,
            cap = StrokeCap.Round
        )

        drawLine(
            purpleBrush,
            Offset(w * .50f, h * .15f),
            Offset(w * .82f, h * .78f),
            stroke,
            cap = StrokeCap.Round
        )

        drawLine(
            Color(0xFF4C7BFF),
            Offset(w * .34f, h * .48f),
            Offset(w * .66f, h * .48f),
            stroke * .72f,
            cap = StrokeCap.Round
        )

        val bars = listOf(
            .22f to .24f,
            .29f to .39f,
            .71f to .34f,
            .78f to .22f
        )

        bars.forEach { (x, top) ->
            drawLine(
                if (x < .5f) Cyan else Violet,
                Offset(w * x, h * (1 - top)),
                Offset(w * x, h * .82f),
                stroke * .48f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    val heights = listOf(
        .18f, .34f, .24f, .50f, .78f, .43f, .63f, .91f,
        .57f, .35f, .72f, .96f, .61f, .42f, .78f, .52f,
        .30f, .66f, .86f, .48f, .25f, .54f, .73f, .38f,
        .19f, .45f, .63f, .30f, .18f, .40f, .58f, .27f
    )

    Canvas(modifier) {
        val gap = size.width / (heights.size * 1.6f)
        val barWidth = gap * .55f

        heights.forEachIndexed { i, value ->
            val x = i * gap + barWidth
            val barHeight = size.height * value

            drawLine(
                brush = Brush.verticalGradient(
                    listOf(
                        Cyan,
                        if (i > heights.size * .63) {
                            Violet
                        } else {
                            Color(0xFF167CDA)
                        }
                    )
                ),
                start = Offset(x, (size.height - barHeight) / 2),
                end = Offset(x, (size.height + barHeight) / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
