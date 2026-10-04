package com.novacamera.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Multi-frame JPEG fusion for HDR and Night modes.
 *
 * - Frames are aligned to the reference with a coarse global-translation
 *   search on 1/8-scale luma (handheld shake is mostly translation).
 * - Merging runs in horizontal bands decoded with [BitmapRegionDecoder], so
 *   full-resolution output needs only one output bitmap plus a few bands —
 *   memory stays flat no matter how many frames are fused.
 * - HDR: per-pixel exposure-fusion weights (well-exposedness) across the bracket.
 * - Night: aligned mean stack with outlier rejection against the reference
 *   (moving subjects are taken from the reference instead of ghosting).
 *
 * Output pixels stay in sensor orientation and the reference frame's EXIF
 * orientation is copied across, exactly like a normal CameraX JPEG.
 */
object FrameFusion {
    enum class Mode { HDR, NIGHT }

    private const val BAND_ROWS = 128
    private const val SEARCH_RADIUS = 6 // thumbnail px (≈ ±48 px at full res)
    private const val THUMB_SAMPLE = 8
    private const val NIGHT_OUTLIER_SUM = 3 * 36

    /** Well-exposedness weight by 8-bit luma; peaks at mid-grey. */
    private val WEIGHT_LUT = IntArray(256) { v ->
        val d = v / 255.0 - 0.5
        (exp(-(d * d) / 0.08) * 1000).roundToInt().coerceAtLeast(1)
    }

    /** Fuses [frames] into [out]. Returns false (leaving [out] untouched) if fusion is impossible. */
    fun fuse(frames: List<File>, mode: Mode, out: File, refIndex: Int = frames.size / 2): Boolean {
        if (frames.size < 2) return false
        val ref = frames[refIndex.coerceIn(0, frames.lastIndex)]
        val bounds = bounds(ref) ?: return false
        val (w, h) = bounds
        val usable = frames.filter { bounds(it) == bounds }
        if (usable.size < 2) return false
        val refUsable = usable.indexOf(ref).coerceAtLeast(0)

        val shifts = alignAll(usable, refUsable, w)
        val decoders = usable.map { openDecoder(it) ?: return false }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            var y0 = 0
            while (y0 < h) {
                val rows = minOf(BAND_ROWS, h - y0)
                val refBand = readBand(decoders[refUsable], 0, 0, y0, rows, w, h, opts, null)
                val bands = usable.indices.map { i ->
                    if (i == refUsable) refBand
                    else readBand(decoders[i], shifts[i].first, shifts[i].second, y0, rows, w, h, opts, refBand)
                }
                val merged = when (mode) {
                    Mode.HDR -> mergeHdr(bands)
                    Mode.NIGHT -> mergeNight(bands, refBand)
                }
                result.setPixels(merged, 0, w, 0, y0, w, rows)
                y0 += rows
            }
            FileOutputStream(out).use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            copyExif(ref, out)
            true
        } catch (_: Throwable) {
            false
        } finally {
            decoders.forEach { runCatching { it.recycle() } }
            result.recycle()
        }
    }

    // ---- merging ---------------------------------------------------------

    private fun mergeHdr(bands: List<IntArray>): IntArray {
        val n = bands[0].size
        val out = IntArray(n)
        for (p in 0 until n) {
            var sr = 0L; var sg = 0L; var sb = 0L; var sw = 0L
            for (band in bands) {
                val c = band[p]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = (r * 77 + g * 150 + b * 29) shr 8
                val wt = WEIGHT_LUT[luma].toLong()
                sr += r * wt; sg += g * wt; sb += b * wt; sw += wt
            }
            out[p] = (0xFF shl 24) or ((sr / sw).toInt() shl 16) or ((sg / sw).toInt() shl 8) or (sb / sw).toInt()
        }
        return out
    }

    private fun mergeNight(bands: List<IntArray>, ref: IntArray): IntArray {
        val n = ref.size
        val out = IntArray(n)
        for (p in 0 until n) {
            val rc = ref[p]
            val rr = (rc shr 16) and 0xFF
            val rg = (rc shr 8) and 0xFF
            val rb = rc and 0xFF
            var sr = rr; var sg = rg; var sb = rb; var count = 1
            for (band in bands) {
                if (band === ref) continue
                val c = band[p]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (abs(r - rr) + abs(g - rg) + abs(b - rb) <= NIGHT_OUTLIER_SUM) {
                    sr += r; sg += g; sb += b; count++
                }
            }
            out[p] = (0xFF shl 24) or ((sr / count) shl 16) or ((sg / count) shl 8) or (sb / count)
        }
        return out
    }

    /**
     * Reads output rows [y0, y0+rows) of a frame shifted by (dx, dy). Pixels
     * the shifted frame doesn't cover are filled from [fallback] (the reference).
     */
    private fun readBand(
        decoder: BitmapRegionDecoder,
        dx: Int, dy: Int,
        y0: Int, rows: Int, w: Int, h: Int,
        opts: BitmapFactory.Options,
        fallback: IntArray?,
    ): IntArray {
        val out = fallback?.copyOf() ?: IntArray(w * rows)
        val src = Rect(dx, y0 + dy, w + dx, y0 + rows + dy)
        if (!src.intersect(0, 0, w, h)) return out
        val bmp = decoder.decodeRegion(src, opts) ?: return out
        try {
            val offX = src.left - dx
            val offY = src.top - (y0 + dy)
            val row = IntArray(bmp.width)
            for (r in 0 until bmp.height) {
                bmp.getPixels(row, 0, bmp.width, 0, r, bmp.width, 1)
                System.arraycopy(row, 0, out, (offY + r) * w + offX, bmp.width)
            }
        } finally {
            bmp.recycle()
        }
        return out
    }

    // ---- alignment -------------------------------------------------------

    private class Thumb(val luma: IntArray, val w: Int, val h: Int, val mean: Float)

    private fun alignAll(files: List<File>, refIdx: Int, fullW: Int): List<Pair<Int, Int>> {
        val thumbs = files.map { thumb(it) }
        val ref = thumbs[refIdx] ?: return files.map { 0 to 0 }
        val scale = fullW.toFloat() / ref.w
        return thumbs.mapIndexed { i, t ->
            if (i == refIdx || t == null || t.w != ref.w || t.h != ref.h) {
                0 to 0
            } else {
                val (sx, sy) = bestShift(ref, t)
                (sx * scale).roundToInt() to (sy * scale).roundToInt()
            }
        }
    }

    private fun thumb(file: File): Thumb? {
        val opts = BitmapFactory.Options().apply { inSampleSize = THUMB_SAMPLE }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val luma = IntArray(px.size)
        var sum = 0L
        for (i in px.indices) {
            val c = px[i]
            val l = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            luma[i] = l; sum += l
        }
        val t = Thumb(luma, bmp.width, bmp.height, sum.toFloat() / luma.size)
        bmp.recycle()
        return t
    }

    /** Shift (sx, sy) such that cur(x+sx, y+sy) ≈ ref(x, y), by zero-mean SAD. */
    private fun bestShift(ref: Thumb, cur: Thumb): Pair<Int, Int> {
        val r = SEARCH_RADIUS
        var best = Float.MAX_VALUE
        var bx = 0
        var by = 0
        val w = ref.w
        val h = ref.h
        for (sy in -r..r) for (sx in -r..r) {
            var sad = 0f
            var n = 0
            var y = maxOf(0, -sy)
            val yEnd = minOf(h, h - sy)
            while (y < yEnd) {
                var x = maxOf(0, -sx)
                val xEnd = minOf(w, w - sx)
                while (x < xEnd) {
                    val a = ref.luma[y * w + x] - ref.mean
                    val b = cur.luma[(y + sy) * w + (x + sx)] - cur.mean
                    sad += abs(a - b)
                    n++
                    x += 2
                }
                y += 2
            }
            if (n > 0) {
                val score = sad / n
                if (score < best) { best = score; bx = sx; by = sy }
            }
        }
        return bx to by
    }

    // ---- helpers ---------------------------------------------------------

    /** The one-arg newInstance(String) only exists on API 31+; the two-arg form works everywhere. */
    @Suppress("DEPRECATION")
    private fun openDecoder(f: File): BitmapRegionDecoder? =
        BitmapRegionDecoder.newInstance(f.absolutePath, false)

    private fun bounds(f: File): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    private fun copyExif(from: File, to: File) {
        runCatching {
            val src = ExifInterface(from.absolutePath)
            val dst = ExifInterface(to.absolutePath)
            for (tag in listOf(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
            )) {
                src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            }
            dst.saveAttributes()
        }
    }
}
