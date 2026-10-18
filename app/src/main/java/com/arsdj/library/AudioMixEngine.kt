package com.arsdj.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioMixEngine(private val context: Context) {

    suspend fun mix(
        tracks: List<AudioTrack>,
        outputFile: File,
        crossfadeSeconds: Int = 4
    ): File = withContext(Dispatchers.IO) {

        require(tracks.isNotEmpty()) {
            "لطفاً حداقل یک آهنگ انتخاب کنید."
        }

        val inputFiles = mutableListOf<File>()

        try {
            tracks.forEachIndexed { index, track ->
                val inputFile = File(
                    context.cacheDir,
                    "ars_dj_input_$index.audio"
                )

                val inputStream =
                    context.contentResolver.openInputStream(track.uri)
                        ?: throw IllegalStateException(
                            "خواندن آهنگ ممکن نیست: ${track.title}"
                        )

                inputStream.use { source ->
                    inputFile.outputStream().use { destination ->
                        source.copyTo(destination)
                    }
                }

                inputFiles.add(inputFile)
            }

            outputFile.parentFile?.mkdirs()

            val inputs = inputFiles.joinToString(" ") { file ->
                "-i ${quote(file.absolutePath)}"
            }

            val filters = mutableListOf<String>()

            inputFiles.indices.forEach { index ->
                filters.add(
                    "[$index:a]aresample=44100," +
                    "aformat=sample_fmts=fltp:channel_layouts=stereo" +
                    "[src$index]"
                )
            }

            val fade = crossfadeSeconds.coerceIn(1, 8)
            var finalLabel = "src0"

            if (inputFiles.size > 1) {
                var previousLabel = "src0"

                for (index in 1 until inputFiles.size) {
                    val outputLabel = "mix$index"

                    filters.add(
                        "[$previousLabel][src$index]" +
                        "acrossfade=d=$fade:c1=tri:c2=tri" +
                        "[$outputLabel]"
                    )

                    previousLabel = outputLabel
                }

                finalLabel = previousLabel
            }

            val command = buildString {
                append("-y ")
                append(inputs)
                append(" -filter_complex ")
                append(quote(filters.joinToString(";")))
                append(" -map ")
                append(quote("[$finalLabel]"))
                append(" -c:a libmp3lame -q:a 2 ")
                append(quote(outputFile.absolutePath))
            }

            suspendCancellableCoroutine { continuation ->
                FFmpegKit.executeAsync(command) { session ->
                    if (!continuation.isActive) {
                        return@executeAsync
                    }

                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        continuation.resume(outputFile)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "ساخت میکس ناموفق بود. کد خطا: $returnCode"
                            )
                        )
                    }
                }
            }
        } finally {
            inputFiles.forEach { it.delete() }
        }
    }

    private fun quote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
