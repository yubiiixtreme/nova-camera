package com.novacamera.ml

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared ML Kit analyzers: barcode, OCR, pose. Keep-only-latest backpressure. */
@Singleton
class FrameAnalyzers @Inject constructor() {

    private val _barcode = MutableStateFlow<String?>(null)
    val barcode: StateFlow<String?> = _barcode.asStateFlow()
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()
    private val _poseLikelihood = MutableStateFlow(0f)
    val poseLikelihood: StateFlow<Float> = _poseLikelihood.asStateFlow()

    private val barcodeScanner = BarcodeScanning.getClient()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT)
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )

    fun barcodeAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy ->
        process(proxy) { img ->
            barcodeScanner.process(img)
                .addOnSuccess { codes -> _barcode.value = codes.firstOrNull()?.rawValue }
                .addOnCompleteListener { proxy.close() }
        }
    }

    fun ocrAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy ->
        process(proxy) { img ->
            textRecognizer.process(img)
                .addOnSuccess { result -> _ocrText.value = result.text }
                .addOnCompleteListener { proxy.close() }
        }
    }

    fun poseAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy ->
        process(proxy) { img ->
            poseDetector.process(img)
                .addOnSuccess { pose -> _poseLikelihood.value = pose.allPoseLandmarks.size / 33f }
                .addOnCompleteListener { proxy.close() }
        }
    }

    private inline fun process(proxy: ImageProxy, block: (InputImage) -> Unit) {
        val media = proxy.image ?: run { proxy.close(); return }
        runCatching {
            block(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
        }.onFailure { proxy.close() }
    }
}
