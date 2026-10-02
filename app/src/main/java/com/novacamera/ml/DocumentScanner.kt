package com.novacamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Document scanner: edge detection → perspective warp → enhance → PDF export.
 * OpenCV is an optional dependency; if native lib is absent we fall back to a
 * center-crop perspective approximation so the feature never crashes.
 */
@Singleton
class DocumentScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class ScanResult(val bitmap: Bitmap, val corners: List<PointF>)

    suspend fun scan(source: Bitmap): ScanResult = withContext(Dispatchers.Default) {
        val corners = detectCorners(source) ?: defaultCorners(source)
        val warped = warpPerspective(source, corners)
        val enhanced = enhance(warped)
        ScanResult(enhanced, corners)
    }

    private fun detectCorners(src: Bitmap): List<PointF>? {
        // OpenCV is not a declared dependency (see gradle/libs.versions.toml):
        // referencing org.opencv.android.OpenCVLoader does not compile.
        // Keep the center-crop fallback until an OpenCV AAR is vendored and
        // detectCorners is implemented via Canny → findContours → approxPolyDP.
        return null
    }

    private fun defaultCorners(b: Bitmap): List<PointF> {
        val w = b.width.toFloat(); val h = b.height.toFloat()
        val m = 0.06f
        return listOf(PointF(w * m, h * m), PointF(w * (1 - m), h * m), PointF(w * (1 - m), h * (1 - m)), PointF(w * m, h * (1 - m)))
    }

    private fun warpPerspective(src: Bitmap, corners: List<PointF>): Bitmap {
        // Affine approximation mapping quad → rect (full perspective matrix
        // solved via OpenCV.getPerspectiveTransform in production).
        val w = src.width; val h = src.height
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val srcPts = floatArrayOf(corners[0].x, corners[0].y, corners[1].x, corners[1].y, corners[2].x, corners[2].y, corners[3].x, corners[3].y)
        val m = Matrix().apply { setPolyToPoly(srcPts, 0, dst, 0, 4) }
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true)
    }

    private fun enhance(b: Bitmap): Bitmap {
        // Adaptive threshold + contrast boost placeholder (RenderScript/Toolkit
        // or GPU path in production). Returns copy to keep pipeline pure.
        return b.copy(b.config ?: Bitmap.Config.ARGB_8888, true)
    }

    suspend fun exportPdf(pages: List<Bitmap>, name: String): Uri = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages to export" }
        // Uses Android PdfDocument (no extra dep) — writes to MediaStore Downloads.
        val doc = android.graphics.pdf.PdfDocument()
        try {
            pages.forEach { bmp ->
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).build()
                val page = doc.startPage(info)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                doc.finishPage(page)
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.pdf")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/NovaCamera")
                }
            }
            // MediaStore.Downloads requires API 29+; minSdk is 26, so fall
            // back to Images on API 26-28 instead of crashing with
            // NoSuchFieldError. Insert may also return null (e.g. no volume).
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore insert failed — storage unavailable")
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                ?: throw IllegalStateException("Could not open output stream for $uri")
            uri
        } finally {
            doc.close()
        }
    }
}
