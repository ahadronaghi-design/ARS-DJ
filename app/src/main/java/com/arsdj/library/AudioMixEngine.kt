package com.arsdj.library

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioMixEngine(private val context: Context) {

    suspend fun mix(
        tracks: List<AudioTrack>,
        outputFile: File,
        crossfadeSeconds: Int = 4,
        volume: Float = 1f,
        bassDb: Float = 0f
    ): File = withContext(Dispatchers.IO) {

        require(tracks.isNotEmpty()) {
            "لطفاً حداقل یک آهنگ انتخاب کنید."
        }

        val inputs = mutableListOf<File>()

        try {
            // کپی آهنگ‌ها به حافظه موقت برنامه
            tracks.forEachIndexed { index, track ->
                val file = File(
                    context.cacheDir,
                    "ars_dj_input_$index.audio"
                )

                val stream = context.contentResolver
                    .openInputStream(track.uri)
                    ?: error("خواندن آهنگ ممکن نیست: ${track.title}")

                stream.use { source ->
                    file.outputStream().use { target ->
                        source.copyTo(target)
                    }
                }

                inputs.add(file)
            }

            outputFile.parentFile?.mkdirs()

            val inputArgs = inputs.joinToString(" ") {
                "-i ${quote(it.absolutePath)}"
            }

            val filters = mutableListOf<String>()

            // یکسان‌سازی نرخ نمونه‌برداری و کانال‌های صوتی
            inputs.indices.forEach { i ->
                filters.add(
                    "[$i:a]aresample=44100," +
                    "aformat=sample_fmts=fltp:channel_layouts=stereo[src$i]"
                )
            }

            // اتصال آهنگ‌ها با انتقال نرم
            var lastLabel = "src0"

            for (i in 1 until inputs.size) {
                val nextLabel = "mix$i"

                filters.add(
                    "[$lastLabel][src$i]" +
                    "acrossfade=d=${crossfadeSeconds.coerceIn(1, 8)}:" +
                    "c1=tri:c2=tri[$nextLabel]"
                )

                lastLabel = nextLabel
            }

            // تنظیم بیس و حجم صدای خروجی
            filters.add(
                "[$lastLabel]" +
                "bass=g=${bassDb.coerceIn(0f, 12f)}:f=110," +
                "volume=${volume.coerceIn(0.25f, 1.5f)}[out]"
            )

            val filterComplex = filters.joinToString(";")

            val command =
                "-y $inputArgs " +
                "-filter_complex ${quote(filterComplex)} " +
                "-map ${quote("[out]")} " +
                "-c:a libmp3lame -q:a 2 " +
                quote(outputFile.absolutePath)

            // اجرای موتور FFmpeg و بررسی نتیجه
            suspendCancellableCoroutine { continuation ->
                FFmpegKit.executeAsync(command) { session ->

                    if (!continuation.isActive) {
                        return@executeAsync
                    }

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        continuation.resume(outputFile)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "ساخت میکس ناموفق بود. " +
                                "کد خطا: ${session.returnCode}"
                            )
                        )
                    }
                }
            }

        } finally {
            // پاک‌سازی فایل‌های موقت
            inputs.forEach { it.delete() }
        }
    }

    private fun quote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
