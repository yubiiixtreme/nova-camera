package com.novacamera.presentation.camera

import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CameraState
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.LensFacing

/** MVI contract: single Intent → ViewModel → single StateFlow<CameraUiState>. */
data class CameraUiState(
    val settings: CameraSettings = CameraSettings(),
    val camera: CameraState = CameraState(),
    val showProPanel: Boolean = false,
    val showSettings: Boolean = false,
    val toast: String? = null,
)

sealed interface CameraIntent {
    data class SetMode(val mode: CaptureMode) : CameraIntent
    data class SetLens(val lens: LensFacing) : CameraIntent
    data class SetFlash(val flash: FlashMode) : CameraIntent
    data class SetZoom(val ratio: Float) : CameraIntent
    data object ToggleTorch : CameraIntent
    data object ToggleProPanel : CameraIntent
    data object Shutter : CameraIntent
    data object ToggleVideo : CameraIntent
    data object PauseResumeVideo : CameraIntent
    data object SwitchCamera : CameraIntent
    data class SetBokeh(val value: Float) : CameraIntent
    data class SetEv(val ev: Float) : CameraIntent
    data class SetIso(val iso: Int?) : CameraIntent
    data class SetShutter(val seconds: Double?) : CameraIntent
    data class SetWb(val kelvin: Int?) : CameraIntent
    data class SetFocus(val distance: Float?) : CameraIntent
    data class ToggleOverlay(val kind: OverlayKind) : CameraIntent
    data class SetLocationTagging(val enabled: Boolean) : CameraIntent
    data class SetStripExif(val enabled: Boolean) : CameraIntent
    data class SetGrid(val enabled: Boolean) : CameraIntent
    data class SetAudioZoom(val enabled: Boolean) : CameraIntent
    data object ToggleGrid : CameraIntent
    data object ClearToast : CameraIntent
}

enum class OverlayKind { HISTOGRAM, ZEBRA, PEAKING }
