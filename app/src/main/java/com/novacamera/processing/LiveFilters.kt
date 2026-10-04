package com.novacamera.processing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.novacamera.domain.model.LiveFilter
import java.io.ByteArrayOutputStream

/**
 * Colour looks as 4x5 colour matrices. The same matrix drives the live
 * viewfinder (hardware layer paint on the preview TextureView) and the saved
 * photo ([FilterBaker]), so what you see is what you get.
 */
object LiveFilters {

    fun matrix(filter: LiveFilter): ColorMatrix? = when (filter) {
        LiveFilter.NONE -> null
        LiveFilter.VIVID -> chain(saturation(1.45f), contrast(1.10f))
        LiveFilter.WARM -> chain(scaleRgb(1.08f, 1.0f, 0.90f), saturation(1.10f))
        LiveFilter.COOL -> chain(scaleRgb(0.91f, 1.0f, 1.09f), saturation(1.05f))
        LiveFilter.FADE -> chain(saturation(0.80f), contrast(0.86f), lift(18f))
        LiveFilter.SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        LiveFilter.MONO -> saturation(0f)
        LiveFilter.NOIR -> chain(saturation(0f), contrast(1.45f))
    }

    fun paint(filter: LiveFilter): Paint? = matrix(filter)?.let {
        Paint().apply { colorFilter = ColorMatrixColorFilter(it) }
    }

    /** Representative swatch colour for the filter picker. */
    fun swatch(filter: LiveFilter): Int = when (filter) {
        LiveFilter.NONE -> 0xFFE0E0E0.toInt()
        LiveFilter.VIVID -> 0xFFFF4D6D.toInt()
        LiveFilter.WARM -> 0xFFFFA64D.toInt()
        LiveFilter.COOL -> 0xFF4DA6FF.toInt()
        LiveFilter.FADE -> 0xFFB8A99A.toInt()
        LiveFilter.SEPIA -> 0xFF8B6B3E.toInt()
        LiveFilter.MONO -> 0xFF9E9E9E.toInt()
        LiveFilter.NOIR -> 0xFF202020.toInt()
    }

    private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

    private fun contrast(c: Float): ColorMatrix {
        val t = (1f - c) * 128f
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun scaleRgb(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

    private fun lift(amount: Float) = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, amount,
            0f, 1f, 0f, 0f, amount,
            0f, 0f, 1f, 0f, amount,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private fun chain(vararg m: ColorMatrix): ColorMatrix {
        val out = ColorMatrix()
        m.forEach { out.postConcat(it) }
        return out
    }
}

/** Bakes a [LiveFilter] into an already-saved JPEG, in place. */
object FilterBaker {

    /**
     * Rewrites the image at [uri] with the filter applied. Pixels are rotated
     * upright first (CameraX stores rotation only as an EXIF tag, which a
     * bitmap round-trip would drop). Returns false if nothing was changed.
     */
    fun bake(resolver: ContentResolver, uri: Uri, filter: LiveFilter): Boolean {
        val paint = LiveFilters.paint(filter) ?: return false
        return runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val orientation = ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
            val upright = rotate(src, orientation)
            val out = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(upright, 0f, 0f, paint)
            if (upright !== src) src.recycle()
            upright.recycle()

            val buf = ByteArrayOutputStream(bytes.size)
            out.compress(Bitmap.CompressFormat.JPEG, 95, buf)
            out.recycle()
            resolver.openOutputStream(uri, "wt")?.use { it.write(buf.toByteArray()) } ?: return false
            true
        }.getOrDefault(false)
    }

    internal fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
