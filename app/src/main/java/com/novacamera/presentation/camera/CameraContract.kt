package com.novacamera.presentation.camera

import com.novacamera.core.camera.CameraCaps
import com.novacamera.core.camera.RecordingState
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CameraState
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.GridStyle
import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.LiveFilter
import com.novacamera.domain.model.VideoQuality

/** MVI contract: single Intent → ViewModel → single StateFlow<CameraUiState>. */
data class CameraUiState(
    val settings: CameraSettings = CameraSettings(),
    val camera: CameraState = CameraState(),
    val caps: CameraCaps = CameraCaps(),
    val recording: RecordingState = RecordingState(),
    /** Self-timer seconds remaining, null when no countdown is running. */
    val countdown: Int? = null,
    /** "Processing…" style label while a multi-frame capture is merging. */
    val busyLabel: String? = null,
    /** Newest saved media, for the gallery thumbnail on the shutter row. */
    val thumbnail: android.net.Uri? = null,
    val showFilters: Boolean = false,
    val showProPanel: Boolean = false,
    val showSettings: Boolean = false,
    val presets: List<com.novacamera.data.datasource.Preset> = emptyList(),
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
    data object ToggleAfAeLock : CameraIntent
    data class SetBurstShots(val count: Int) : CameraIntent
    data class SetHdrFrames(val frames: Int) : CameraIntent
    data class SetHdrStep(val stepEv: Int) : CameraIntent
    data class SetTimelapseShots(val shots: Int) : CameraIntent
    data class SetGridStyle(val style: GridStyle) : CameraIntent
    data class SetAspectMask(val mask: AspectMask) : CameraIntent
    data class SetLevel(val enabled: Boolean) : CameraIntent
    data class SetFilter(val filter: LiveFilter) : CameraIntent
    data class SetVideoQuality(val quality: VideoQuality) : CameraIntent
    data class SetShutterSound(val enabled: Boolean) : CameraIntent
    data object CycleTimer : CameraIntent
    data object ToggleFilters : CameraIntent
    data object ClearToast : CameraIntent
}

enum class OverlayKind { HISTOGRAM, ZEBRA, PEAKING }
