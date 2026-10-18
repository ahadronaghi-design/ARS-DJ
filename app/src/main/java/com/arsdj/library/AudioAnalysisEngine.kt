package com.arsdj.library

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.sqrt

/**
 * Lightweight offline analysis. Input is a readable local file path, not content://.
 * BPM is a heuristic estimate from the decoded amplitude envelope, not a beat grid.
 */
object AudioAnalysisEngine {
    data class AnalysisResult(
        val estimatedBpm: Int?,
        val energyPercent: Int,
        val durationSeconds: Long,
        val confidence: String,
        val recommendation: String
    )

    fun analyze(inputPath: String, cacheDir: File): AnalysisResult {
        val input = File(inputPath)
        require(input.isFile) { "فایل صوتی پیدا نشد." }
        val pcm = File(cacheDir, "arsdj_analysis_${System.nanoTime()}.pcm")
        try {
            val cmd = "-y -i ${quote(input.absolutePath)} -vn -ac 1 -ar 8000 -f s16le ${quote(pcm.absolutePath)}"
            val session = FFmpegKit.execute(cmd)
            if (!ReturnCode.isSuccess(session.returnCode) || !pcm.isFile) {
                throw IllegalStateException("تبدیل فایل صوتی برای تحلیل انجام نشد.")
            }
            val bytes = pcm.readBytes()
            val duration = (bytes.size / 2L) / 8000L
            if (bytes.size < 16000) return AnalysisResult(null, energy(bytes), duration, "کم", "فایل برای تخمین ضرب کوتاه است.")

            val env = envelope(bytes, 80)
            val (bpm, confidence) = bpmEstimate(env)
            val energy = energy(bytes)
            val note = when {
                energy >= 75 -> "انرژی بالا؛ احتمالاً مناسب بخش اوج میکس."
                energy >= 45 -> "انرژی متوسط؛ مناسب بخش میانی میکس."
                else -> "انرژی آرام‌تر؛ مناسب شروع یا بخش تنفسی میکس."
            }
            return AnalysisResult(bpm, energy, duration, confidence, note)
        } finally {
            pcm.delete()
        }
    }

    private fun envelope(b: ByteArray, block: Int): DoubleArray {
        val n = (b.size / 2) / block
        return DoubleArray(n) { k ->
            var sum = 0.0
            for (i in k * block until (k + 1) * block) {
                val j = i * 2
                val sample = (((b[j + 1].toInt() shl 8) or (b[j].toInt() and 255))).toShort().toDouble()
                sum += sample * sample
            }
            sqrt(sum / block)
        }
    }

    private fun energy(b: ByteArray): Int {
        val n = b.size / 2
        if (n == 0) return 0
        var sum = 0.0
        for (i in 0 until n) {
            val j = i * 2
            val s = (((b[j + 1].toInt() shl 8) or (b[j].toInt() and 255))).toShort().toDouble() / 32768.0
            sum += s * s
        }
        return (sqrt(sum / n) * 1000).toInt().coerceIn(0, 100)
    }

    private fun bpmEstimate(env: DoubleArray): Pair<Int?, String> {
        if (env.size < 400) return null to "کم"
        val avg = env.average()
        val x = DoubleArray(env.size) { env[it] - avg }
        var bestLag = -1
        var best = Double.NEGATIVE_INFINITY
        var second = Double.NEGATIVE_INFINITY
        for (lag in 33..86) {
            var dot = 0.0; var aa = 0.0; var bb = 0.0
            var i = 0
            while (i + lag < x.size) {
                val a = x[i]; val b = x[i + lag]
                dot += a * b; aa += a * a; bb += b * b
                i++
            }
            val den = sqrt(aa * bb)
            val score = if (den > 0.0) dot / den else 0.0
            if (score > best) { second = best; best = score; bestLag = lag }
            else if (score > second) second = score
        }
        if (bestLag < 0 || best < 0.12) return null to "کم"
        val bpm = (6000.0 / bestLag).toInt().coerceIn(60, 200)
        val confidence = when {
            best >= 0.55 && best - second >= 0.025 -> "زیاد"
            best >= 0.30 -> "متوسط"
            else -> "کم"
        }
        return bpm to confidence
    }

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
