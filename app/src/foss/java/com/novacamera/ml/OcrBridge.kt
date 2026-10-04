package com.novacamera.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/** FOSS flavor: OCR needs Google ML Kit, excluded from this build. */
@Singleton
class OcrBridge @Inject constructor() {
    suspend fun read(bmp: Bitmap): String =
        throw UnsupportedOperationException("FOSS build excludes Google ML Kit")

    fun close() = Unit
}
