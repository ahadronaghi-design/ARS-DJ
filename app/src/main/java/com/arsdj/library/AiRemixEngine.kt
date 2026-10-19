package com.arsdj.library

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.abs

/**
 * ARS-DJ AI Remix v1 (offline, DSP-based).
 * Uses the reference track's estimated tempo as a guide and applies a selected
 * mastering/club-processing chain to the original. It does NOT separate vocals
 * or generate new instruments; that requires a dedicated source-separation / AI service.
 */
object AiRemixEngine {
    enum class Style(val titleFa: String) {
        MODERN_POP("پاپ مدرن"),
        DANCE("دنس پرانرژی"),
        CLUB("کلاب عمیق")
    }

    data class Report(
        val originalBpm: Int?,
        val referenceBpm: Int?,
        val appliedTempoRatio: Double,
        val style: Style,
        val intensityPercent: Int,
        val notes: List<String>
    )

    /**
     * Creates a modernized version of [original] using [reference] as a tempo/style guide.
     * Both files must be local readable files. [output] will be an MP3.
     * [intensity] is 0..1 and tempo change is deliberately limited to avoid damaging the song.
     */
    fun create(
        original: File,
        reference: File,
        output: File,
        originalAnalysis: AudioAnalysisEngine.AnalysisResult,
        referenceAnalysis: AudioAnalysisEngine.AnalysisResult,
        style: Style = Style.MODERN_POP,
        intensity: Float = 0.65f
    ): Report {
        require(original.isFile && original.length() > 0L) { "فایل آهنگ اصلی پیدا نشد." }
        require(reference.isFile && reference.length() > 0L) { "فایل مرجع پیدا نشد." }
        require(original.absolutePath != reference.absolutePath) { "آهنگ اصلی و مرجع باید دو فایل متفاوت باشند." }
        require(intensity in 0f..1f) { "شدت ریمیکس باید بین صفر و یک باشد." }
        output.parentFile?.mkdirs()

        val sourceBpm = originalAnalysis.estimatedBpm
        val referenceBpm = referenceAnalysis.estimatedBpm
        val requestedRatio = if (sourceBpm != null && referenceBpm != null && sourceBpm > 0) {
            referenceBpm.toDouble() / sourceBpm.toDouble()
        } else 1.0
        // Keep v1 conservative: at most +/- 12%, scaled by the user's intensity.
        val maxChange = 0.12 * intensity
        val ratio = requestedRatio.coerceIn(1.0 - maxChange, 1.0 + maxChange)

        val bassDb: Double
        val presenceDb: Double
        val compression: Double
        val reverb: String
        val stereo: String
        when (style) {
            Style.MODERN_POP -> {
                bassDb = 1.5 + 2.5 * intensity
                presenceDb = 1.0 + 1.5 * intensity
                compression = 2.2 + intensity * 1.4
                reverb = "aecho=0.8:0.12:35:0.10"
                stereo = "stereotools=mlev=1.0:slev=1.08"
            }
            Style.DANCE -> {
                bassDb = 3.0 + 4.0 * intensity
                presenceDb = 1.5 + 2.0 * intensity
                compression = 3.2 + intensity * 1.8
                reverb = "aecho=0.8:0.16:45|70:0.12|0.07"
                stereo = "stereotools=mlev=1.0:slev=1.16"
            }
            Style.CLUB -> {
                bassDb = 4.0 + 4.0 * intensity
                presenceDb = 0.5 + 1.5 * intensity
                compression = 3.8 + intensity * 1.8
                reverb = "aecho=0.8:0.18:55|90:0.13|0.08"
                stereo = "stereotools=mlev=1.0:slev=1.20"
            }
        }

        val filters = listOf(
            "atempo=${ratio.coerceIn(0.5, 2.0).format(4)}",
            "highpass=f=30",
            "equalizer=f=85:t=q:w=1:g=${bassDb.format(2)}",
            "equalizer=f=3200:t=q:w=1:g=${presenceDb.format(2)}",
            "acompressor=threshold=0.12:ratio=${compression.format(2)}:attack=15:release=180:makeup=1.2",
            reverb,
            stereo,
            "alimiter=limit=0.95"
        ).joinToString(",")

        val command = "-y -i ${quote(original.absolutePath)} -i ${quote(reference.absolutePath)} " +
            "-map 0:a:0 -vn -af ${quote(filters)} -ar 44100 -ac 2 " +
            "-c:a libmp3lame -b:a 320k -metadata title=${quote("ARS-DJ AI Remix - ${style.titleFa}")} " +
            "-metadata comment=${quote("ARS-DJ offline remix v1; reference used as tempo/style guide")} ${quote(output.absolutePath)}"
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !output.isFile || output.length() < 1024L) {
            output.delete()
            val details = session.failStackTrace ?: session.allLogsAsString
            throw IllegalStateException("ساخت ریمیکس انجام نشد. ${details.takeLast(700)}")
        }

        val notes = mutableListOf<String>()
        if (sourceBpm == null || referenceBpm == null) notes += "تخمین BPM یکی از آهنگ‌ها نامشخص بود؛ سرعت با تغییر محدود پردازش شد."
        if (abs(ratio - 1.0) < 0.005) notes += "سرعت آهنگ تقریباً ثابت نگه داشته شد."
        notes += "وکال موجود در آهنگ اصلی حفظ می‌شود؛ جداسازی و بازتولید مستقل وکال در این نسخه انجام نمی‌شود."
        notes += "آهنگ مرجع برای راهنمای سرعت استفاده شد؛ صدای آن وارد خروجی نمی‌شود."
        return Report(sourceBpm, referenceBpm, ratio, style, (intensity * 100).toInt(), notes)
    }

    private fun Double.format(decimals: Int): String = "%1$.${decimals}f".format(java.util.Locale.US, this)
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
