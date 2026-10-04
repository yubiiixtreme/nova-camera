package com.novacamera.presentation.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.novacamera.ml.DocumentScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Student scan flow: pick a photo of notes/whiteboard -> warp -> OCR -> PDF/share. */
data class ScanUiState(
    val sourceUri: Uri? = null,
    val preview: Bitmap? = null,
    val scanned: Bitmap? = null,
    val ocrText: String = "",
    val scanning: Boolean = false,
    val ocrRunning: Boolean = false,
    val pdfUri: Uri? = null,
    val pdfName: String = "",
    val error: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanner: DocumentScanner,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Loads the picked image, downsampled so low-RAM devices (Android 7+) stay alive. */
    fun setSource(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, pdfUri = null, scanned = null, ocrText = "") }
            val bmp = runCatching { decodeDownsampled(uri, maxDim = 2048) }.getOrNull()
            if (bmp == null) {
                _ui.update { it.copy(error = "Could not open that image") }
            } else {
                _ui.update { it.copy(sourceUri = uri, preview = bmp) }
            }
        }
    }

    /** Warps the page, then OCRs it. Shows a Play Services hint when ML is unavailable. */
    fun scan() {
        val src = _ui.value.preview ?: return
        viewModelScope.launch {
            _ui.update { it.copy(scanning = true, error = null, pdfUri = null) }
            val result = runCatching { scanner.scan(src) }.getOrNull()
            if (result == null) {
                _ui.update { it.copy(scanning = false, error = "Scan failed — try a clearer photo") }
                return@launch
            }
            _ui.update { it.copy(scanned = result.bitmap, scanning = false, ocrRunning = true) }
            val text = runCatching { recognize(result.bitmap) }.getOrElse { e ->
                _ui.update {
                    it.copy(
                        ocrRunning = false,
                        error = if (isMissingPlayServices(e)) {
                            "Text recognition needs Google Play Services — update it and retry"
                        } else {
                            "Text recognition failed: ${e.message}"
                        },
                    )
                }
                return@launch
            }
            _ui.update { it.copy(ocrText = text, ocrRunning = false) }
        }
    }

    fun savePdf(name: String) {
        val bmp = _ui.value.scanned ?: return
        viewModelScope.launch {
            val clean = name.ifBlank { "scan_${System.currentTimeMillis()}" }
            val uri = runCatching { scanner.exportPdf(listOf(bmp), clean) }.getOrNull()
            if (uri == null) {
                _ui.update { it.copy(error = "Could not save PDF — storage unavailable") }
            } else {
                _ui.update { it.copy(pdfUri = uri, pdfName = clean) }
            }
        }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun reset() {
        _ui.value.preview?.recycle()
        _ui.value.scanned?.takeIf { it != _ui.value.preview }?.recycle()
        _ui.value = ScanUiState()
    }

    private fun decodeDownsampled(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private suspend fun recognize(bmp: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun isMissingPlayServices(e: Throwable): Boolean {
        val msg = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return msg.contains("Play Services", ignoreCase = true) ||
            msg.contains("MlKit", ignoreCase = true) ||
            e.javaClass.name.contains("MlKitException")
    }

    override fun onCleared() {
        runCatching { recognizer.close() }
    }
}
