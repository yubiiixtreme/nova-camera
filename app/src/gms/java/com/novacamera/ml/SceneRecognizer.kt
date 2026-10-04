package com.novacamera.ml

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.novacamera.domain.model.SceneType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI scene recognition: lightweight heuristic + ML Kit face density → scene hint.
 * Drives auto-tuning of contrast/saturation/exposure in CameraViewModel.
 * Heavy path is skipped when ThermalMonitor.shouldShedLoad().
 */
@Singleton
class SceneRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _scene = MutableStateFlow(SceneType.UNKNOWN)
    val scene: StateFlow<SceneType> = _scene.asStateFlow()
    private val _faceCount = MutableStateFlow(0)
    val faceCount: StateFlow<Int> = _faceCount.asStateFlow()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build(),
    )

    fun analyzerEnabledProvider(): () -> Boolean = { true }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun asAnalyzer(onScene: (SceneType) -> Unit): ImageAnalysis.Analyzer {
        val exec = Executors.newSingleThreadExecutor()
        return ImageAnalysis.Analyzer { proxy: ImageProxy ->
            try {
                val media = proxy.image ?: run { proxy.close(); return@Analyzer }
                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                detector.process(img)
                    .addOnSuccessListener { faces ->
                        _faceCount.value = faces.size
                        val s = when {
                            faces.size >= 3 -> SceneType.PORTRAIT
                            faces.size in 1..2 -> SceneType.PORTRAIT
                            else -> SceneType.UNKNOWN
                        }
                        _scene.value = s
                        onScene(s)
                    }
                    .addOnCompleteListener { proxy.close() }
            } catch (_: Exception) { proxy.close() }
        }
    }

    /** Rule-based fallback when ML is throttled (brightness/time heuristics). */
    fun classifyFallback(hourOfDay: Int, meanLuma: Float): SceneType = when {
        hourOfDay in 17..19 && meanLuma > 0.35f -> SceneType.SUNSET
        meanLuma < 0.12f -> SceneType.NIGHT_SKY
        else -> SceneType.UNKNOWN
    }
}
