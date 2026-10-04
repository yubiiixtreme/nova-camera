package com.novacamera.ml

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared ML Kit analyzers: barcode, OCR. Keep-only-latest backpressure. */
@Singleton
class FrameAnalyzers @Inject constructor() {

    private val _barcode = MutableStateFlow<String?>(null)
    val barcode: StateFlow<String?> = _barcode.asStateFlow()
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    private val barcodeScanner = BarcodeScanning.getClient()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun barcodeAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy ->
        process(proxy) { img ->
            barcodeScanner.process(img)
                .addOnSuccessListener { codes -> _barcode.value = codes.firstOrNull()?.rawValue }
                .addOnCompleteListener { proxy.close() }
        }
    }

    fun ocrAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy ->
        process(proxy) { img ->
            textRecognizer.process(img)
                .addOnSuccessListener { result -> _ocrText.value = result.text }
                .addOnCompleteListener { proxy.close() }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inline fun process(proxy: ImageProxy, block: (InputImage) -> Unit) {
        val media = proxy.image ?: run { proxy.close(); return }
        runCatching {
            block(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
        }.onFailure { proxy.close() }
    }
}
