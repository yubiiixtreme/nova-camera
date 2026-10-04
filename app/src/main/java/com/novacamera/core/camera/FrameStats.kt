package com.novacamera.core.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.ln

/**
 * Live per-frame exposure/focus telemetry sampled from the Y plane.
 * Drives the histogram, zebra, and peaking overlays with measured data
 * instead of placeholders.
 */
data class FrameStats(
    /** 64 luma bins, raw sample counts. */
    val lumaHist: IntArray,
    /** Fraction of sampled pixels at/near clip (>= 250). */
    val clippedFraction: Float,
    /** 0..1 normalized variance-of-Laplacian focus measure. */
    val sharpness: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameStats) return false
        return clippedFraction == other.clippedFraction &&
            sharpness == other.sharpness &&
            lumaHist.contentEquals(other.lumaHist)
    }

    override fun hashCode(): Int {
        var r = lumaHist.contentHashCode()
        r = 31 * r + clippedFraction.hashCode()
        r = 31 * r + sharpness.hashCode()
        return r
    }
}

/**
 * Lightweight analyzer: samples every Nth frame on a coarse grid so even
 * low-end devices keep 30fps preview. Always closes the proxy.
 */
class FrameStatsAnalyzer(
    private val onStats: (FrameStats) -> Unit,
    private val everyNth: Int = 6,
) : ImageAnalysis.Analyzer {
    private var seen = 0

    override fun analyze(proxy: ImageProxy) {
        try {
            if (++seen % everyNth != 0) return
            val stats = runCatching { sample(proxy) }.getOrNull() ?: return
            runCatching { onStats(stats) }
        } finally {
            proxy.close()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun sample(proxy: ImageProxy): FrameStats? {
        val img = proxy.image ?: return null
        val y = img.planes[0]
        val buf = y.buffer
        val rowStride = y.rowStride
        val w = img.width
        val h = img.height
        // Coarse grid (~160 x 90 max) keeps this under ~1ms.
        val stepX = (w / 160).coerceAtLeast(1)
        val stepY = (h / 90).coerceAtLeast(1)
        val hist = IntArray(64)
        var clipped = 0
        var count = 0
        // Small Laplacian grid for the focus measure.
        val gw = w / stepX
        val gh = h / stepY
        val grid = IntArray(gw * gh)
        var gi = 0
        var yy = 0
        while (yy < h) {
            var xx = 0
            while (xx < w) {
                val v = buf.get(yy * rowStride + xx).toInt() and 0xFF
                hist[(v * 64) ushr 8]++
                if (v >= 250) clipped++
                count++
                grid[gi++] = v
                xx += stepX
            }
            yy += stepY
        }
        if (count == 0) return null
        var lapSum = 0L
        var lapSq = 0L
        var lapN = 0
        for (r in 1 until gh - 1) {
            for (c in 1 until gw - 1) {
                val lap = 4 * grid[r * gw + c] -
                    grid[(r - 1) * gw + c] - grid[(r + 1) * gw + c] -
                    grid[r * gw + c - 1] - grid[r * gw + c + 1]
                lapSum += lap
                lapSq += lap.toLong() * lap
                lapN++
            }
        }
        val variance = if (lapN > 0) (lapSq.toDouble() / lapN - (lapSum.toDouble() / lapN).let { it * it }) else 0.0
        // Variance spans orders of magnitude; log-compress to a stable 0..1 meter.
        val sharp = (ln(1.0 + variance) / 8.0).toFloat().coerceIn(0f, 1f)
        return FrameStats(hist, clipped.toFloat() / count, sharp)
    }
}
