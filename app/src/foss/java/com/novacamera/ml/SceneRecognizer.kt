package com.novacamera.ml

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.novacamera.domain.model.SceneType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FOSS flavor: face detection needs Google ML Kit, so scene hints fall back
 * to the brightness/time heuristic. Camera capture is unaffected.
 */
@Singleton
class SceneRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _scene = MutableStateFlow(SceneType.UNKNOWN)
    val scene: StateFlow<SceneType> = _scene.asStateFlow()
    private val _faceCount = MutableStateFlow(0)
    val faceCount: StateFlow<Int> = _faceCount.asStateFlow()

    fun analyzerEnabledProvider(): () -> Boolean = { false }

    fun asAnalyzer(onScene: (SceneType) -> Unit): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { proxy: ImageProxy ->
            proxy.close()
            onScene(SceneType.UNKNOWN)
        }
    }

    /** Rule-based fallback when ML is throttled (brightness/time heuristics). */
    fun classifyFallback(hourOfDay: Int, meanLuma: Float): SceneType = when {
        hourOfDay in 17..19 && meanLuma > 0.35f -> SceneType.SUNSET
        meanLuma < 0.12f -> SceneType.NIGHT_SKY
        else -> SceneType.UNKNOWN
    }
}
