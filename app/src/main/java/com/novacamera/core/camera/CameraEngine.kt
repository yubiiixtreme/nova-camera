package com.novacamera.core.camera

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.ProControls
import com.novacamera.media.VideoResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What the currently bound camera can actually do. Drives which controls the UI offers. */
data class CameraCaps(
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val hasFlash: Boolean = false,
    val hasFront: Boolean = true,
    val hasBack: Boolean = true,
    /** Exposure compensation range expressed in EV (already multiplied by the step). */
    val evMin: Float = 0f,
    val evMax: Float = 0f,
    val manualSensor: Boolean = false,
    val isoMin: Int = 100,
    val isoMax: Int = 3200,
    val shutterMinSec: Double = 1.0 / 8000,
    val shutterMaxSec: Double = 1.0 / 4,
    val manualFocus: Boolean = false,
    val minFocusDiopters: Float = 0f,
    val manualWhiteBalance: Boolean = false,
) {
    val canSwitchLens get() = hasFront && hasBack
    val hasEv get() = evMax > evMin
}

/** Live video-recording state. */
data class RecordingState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val durationMs: Long = 0L,
)

/**
 * Unified camera engine abstraction.
 * Primary impl = [CameraXEngine]; manual sensor controls go through
 * [Camera2ProController] on the same CameraX session.
 */
interface CameraEngine {
    val zoomState: StateFlow<Float>
    val torchState: StateFlow<Boolean>
    /** Live frame telemetry (null until the first analyzed frame). */
    val frameStats: StateFlow<FrameStats?>
    val caps: StateFlow<CameraCaps>
    val recording: StateFlow<RecordingState>
    val videoResults: SharedFlow<VideoResult>

    /**
     * Binds the use cases the current mode needs (never more than three, the
     * guaranteed-supported limit) and re-applies flash/zoom/pro settings.
     * Safe to re-call on settings change.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        settings: CameraSettings,
    ): Result<Unit>

    fun unbindAll()
    suspend fun takePhoto(settings: CameraSettings): Result<Uri>
    suspend fun takeBurst(settings: CameraSettings, count: Int): Result<List<Uri>>
    fun setZoom(ratio: Float)
    fun setTorch(on: Boolean)
    fun setFlash(mode: FlashMode)
    fun applyPro(pro: ProControls)
    fun lockAfAe(lock: Boolean)

    /** [x]/[y] are pixel coordinates inside the bound PreviewView. */
    fun tapToFocus(x: Float, y: Float)

    fun startRecording(settings: CameraSettings): Result<Unit>
    fun stopRecording()
    fun pauseRecording()
    fun resumeRecording()
}
