package com.novacamera.presentation.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacamera.core.camera.CameraEngine
import com.novacamera.core.common.ThermalMonitor
import com.novacamera.data.datasource.SettingsDataStore
import com.novacamera.data.repository.MediaRepository
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.LensFacing
import com.novacamera.media.VideoRecorderController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel: owns [CameraUiState], routes intents to CameraX engine,
 * MediaStore writes, and thermal-aware degradation (drops ML/FPS on severe).
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val engine: CameraEngine,
    private val media: MediaRepository,
    private val prefs: SettingsDataStore,
    private val thermal: ThermalMonitor,
    val video: VideoRecorderController,
) : ViewModel() {

    private val _ui = MutableStateFlow(CameraUiState())
    val ui: StateFlow<CameraUiState> = _ui.asStateFlow()
    private var captureJob: Job? = null

    init {
        thermal.start()
        viewModelScope.launch {
            prefs.settings.collect { s -> _ui.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            thermal.status.collect { t ->
                _ui.update {
                    it.copy(camera = it.camera.copy(thermalThrottled = thermal.shouldShedLoad()))
                }
            }
        }
    }

    fun onIntent(intent: CameraIntent) {
        when (intent) {
            is CameraIntent.SetMode -> updateSettings { it.copy(captureMode = intent.mode) }
            is CameraIntent.SetLens -> updateSettings { it.copy(lensFacing = intent.lens) }
            is CameraIntent.SetFlash -> updateSettings { it.copy(flashMode = intent.flash) }
            is CameraIntent.SetZoom -> {
                engine.setZoom(intent.ratio)
                updateSettings { it.copy(zoomRatio = intent.ratio) }
            }
            CameraIntent.ToggleTorch -> {
                val on = !_ui.value.camera.torchOn
                engine.setTorch(on)
                _ui.update { it.copy(camera = it.camera.copy(torchOn = on)) }
            }
            CameraIntent.ToggleProPanel -> _ui.update { it.copy(showProPanel = !it.showProPanel) }
            CameraIntent.SwitchCamera -> {
                val next = if (_ui.value.settings.lensFacing == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
                updateSettings { it.copy(lensFacing = next) }
            }
            CameraIntent.Shutter -> shutter()
            CameraIntent.ToggleVideo -> { /* bound in CameraScreen via video controller */ }
            CameraIntent.PauseResumeVideo -> { }
            is CameraIntent.SetBokeh -> updateSettings { it.copy(bokehStrength = intent.value) }
            is CameraIntent.SetEv -> updateSettings { it.copy(proControls = it.proControls.copy(exposureCompensationEv = intent.ev)) }
            is CameraIntent.SetIso -> updateSettings { it.copy(proControls = it.proControls.copy(iso = intent.iso)) }
            is CameraIntent.SetShutter -> updateSettings { it.copy(proControls = it.proControls.copy(shutterSpeedSec = intent.seconds)) }
            is CameraIntent.SetWb -> updateSettings { it.copy(proControls = it.proControls.copy(whiteBalanceKelvin = intent.kelvin)) }
            is CameraIntent.SetFocus -> updateSettings { it.copy(proControls = it.proControls.copy(manualFocusDistance = intent.distance)) }
            is CameraIntent.ToggleOverlay -> toggleOverlay(intent.kind)
            CameraIntent.ClearToast -> _ui.update { it.copy(toast = null) }
        }
    }

    private fun toggleOverlay(kind: OverlayKind) {
        val p = _ui.value.settings.proControls
        val next = when (kind) {
            OverlayKind.HISTOGRAM -> p.copy(histogramEnabled = !p.histogramEnabled)
            OverlayKind.ZEBRA -> p.copy(zebraEnabled = !p.zebraEnabled)
            OverlayKind.PEAKING -> p.copy(focusPeakingEnabled = !p.focusPeakingEnabled)
        }
        updateSettings { it.copy(proControls = next) }
    }

    private fun updateSettings(t: (com.novacamera.domain.model.CameraSettings) -> com.novacamera.domain.model.CameraSettings) {
        val cur = _ui.value.settings
        viewModelScope.launch { prefs.update(t, cur) }
    }

    private fun shutter() {
        if (captureJob?.isActive == true) return
        captureJob = viewModelScope.launch {
            _ui.update { it.copy(camera = it.camera.copy(isCapturing = true)) }
            val s = _ui.value.settings
            val uri: android.net.Uri?
            val error: Throwable?
            if (s.captureMode == CaptureMode.BURST) {
                val r = engine.takeBurst(s, 10)
                uri = r.getOrNull()?.firstOrNull()
                error = r.exceptionOrNull()
            } else {
                val r = engine.takePhoto(s)
                uri = r.getOrNull()
                error = r.exceptionOrNull()
            }
            if (uri != null) {
                media.notifyNewMedia(uri, false)
                _ui.update {
                    it.copy(
                        camera = it.camera.copy(isCapturing = false, lastCaptureUri = uri, burstCount = 0),
                        toast = "Saved",
                    )
                }
            } else {
                _ui.update {
                    it.copy(camera = it.camera.copy(isCapturing = false, error = error?.message), toast = error?.message)
                }
            }
        }
    }

    /** Binds Preview+Capture to the given lifecycle. Safe to call again on settings change. */
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            engine.bind(lifecycleOwner, previewView, _ui.value.settings)
                .onSuccess { _ui.update { it.copy(camera = it.camera.copy(isBound = true, error = null)) } }
                .onFailure { e ->
                    _ui.update { it.copy(camera = it.camera.copy(isBound = false, error = e.message), toast = e.message) }
                }
        }
    }

    /** [xNorm]/[yNorm] are normalized (0..1) coordinates within the preview surface. */
    fun onTapToFocus(xNorm: Float, yNorm: Float) {
        engine.tapToFocus(xNorm.coerceIn(0f, 1f), yNorm.coerceIn(0f, 1f))
    }

    fun consumeLastCapture(): android.net.Uri? {
        val u = _ui.value.camera.lastCaptureUri
        _ui.update { it.copy(camera = it.camera.copy(lastCaptureUri = null)) }
        return u
    }

    override fun onCleared() {
        thermal.stop()
        engine.unbindAll()
    }
}
