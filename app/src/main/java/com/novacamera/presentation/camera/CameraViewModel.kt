package com.novacamera.presentation.camera

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacamera.core.camera.CameraEngine
import com.novacamera.core.common.ThermalMonitor
import com.novacamera.data.datasource.PresetStore
import com.novacamera.data.datasource.SettingsDataStore
import com.novacamera.data.repository.MediaRepository
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.ProControls
import com.novacamera.media.VideoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel: owns [CameraUiState], routes intents to the CameraX engine,
 * and runs the capture state machine (self-timer → capture → save).
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val engine: CameraEngine,
    private val media: MediaRepository,
    private val prefs: SettingsDataStore,
    private val presetStore: PresetStore,
    private val thermal: ThermalMonitor,
) : ViewModel() {

    private val _ui = MutableStateFlow(CameraUiState(settings = prefs.settings.value))
    val ui: StateFlow<CameraUiState> = _ui.asStateFlow()
    private var captureJob: Job? = null
    private var bindJob: Job? = null

    /** Fires at the instant a still is actually taken (after any countdown): flash + sound hook. */
    private val _shutterFired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shutterFired: SharedFlow<Unit> = _shutterFired.asSharedFlow()

    /** Live frame telemetry for histogram/zebra/peaking overlays. */
    val frameStats: StateFlow<com.novacamera.core.camera.FrameStats?> = engine.frameStats

    init {
        thermal.start()
        // Always start at 1x; a stale persisted zoom is surprising after relaunch.
        prefs.update { it.copy(zoomRatio = 1f) }
        viewModelScope.launch { prefs.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
        viewModelScope.launch { presetStore.presets.collect { p -> _ui.update { it.copy(presets = p) } } }
        viewModelScope.launch { engine.caps.collect { c -> _ui.update { it.copy(caps = c) } } }
        viewModelScope.launch {
            engine.recording.collect { r ->
                _ui.update { it.copy(recording = r, camera = it.camera.copy(isRecording = r.active, isPausedRecording = r.paused)) }
            }
        }
        viewModelScope.launch {
            engine.videoResults.collect { r ->
                when (r) {
                    is VideoResult.Saved -> {
                        media.notifyNewMedia(r.uri, true)
                        toast("Video saved")
                    }
                    is VideoResult.Failed -> toast(r.message)
                }
            }
        }
        viewModelScope.launch {
            media.items.collect { list -> _ui.update { it.copy(thumbnail = list.firstOrNull()?.uri) } }
        }
        viewModelScope.launch { media.refresh() }
        viewModelScope.launch {
            thermal.status.collect {
                _ui.update { it.copy(camera = it.camera.copy(thermalThrottled = thermal.shouldShedLoad())) }
            }
        }
    }

    fun onIntent(intent: CameraIntent) {
        when (intent) {
            is CameraIntent.SetMode -> setMode(intent.mode)
            is CameraIntent.SetLens -> setLens(intent.lens)
            is CameraIntent.SetFlash -> {
                engine.setFlash(intent.flash)
                updateSettings { it.copy(flashMode = intent.flash) }
            }
            is CameraIntent.SetZoom -> {
                val c = _ui.value.caps
                val ratio = if (c.maxZoom > c.minZoom) intent.ratio.coerceIn(c.minZoom, c.maxZoom) else intent.ratio
                engine.setZoom(ratio)
                updateSettings { it.copy(zoomRatio = ratio) }
            }
            CameraIntent.ToggleTorch -> engine.setTorch(!engine.torchState.value)
            CameraIntent.ToggleProPanel -> _ui.update { it.copy(showProPanel = !it.showProPanel, showFilters = false) }
            CameraIntent.ToggleFilters -> _ui.update { it.copy(showFilters = !it.showFilters, showProPanel = false) }
            CameraIntent.SwitchCamera -> {
                val next = if (_ui.value.settings.lensFacing == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
                setLens(next)
            }
            CameraIntent.Shutter -> shutter()
            CameraIntent.ToggleVideo -> shutter()
            CameraIntent.PauseResumeVideo -> {
                if (_ui.value.recording.paused) engine.resumeRecording() else engine.pauseRecording()
            }
            is CameraIntent.SetBokeh -> updateSettings { it.copy(bokehStrength = intent.value) }
            is CameraIntent.SetEv -> updatePro { it.copy(exposureCompensationEv = intent.ev) }
            is CameraIntent.SetIso -> updatePro { it.copy(iso = intent.iso) }
            is CameraIntent.SetShutter -> updatePro { it.copy(shutterSpeedSec = intent.seconds) }
            is CameraIntent.SetWb -> updatePro { it.copy(whiteBalanceKelvin = intent.kelvin) }
            is CameraIntent.SetFocus -> updatePro { it.copy(manualFocusDistance = intent.distance) }
            is CameraIntent.ToggleOverlay -> toggleOverlay(intent.kind)
            is CameraIntent.SetLocationTagging -> updateSettings { it.copy(locationTagging = intent.enabled) }
            is CameraIntent.SetStripExif -> updateSettings { it.copy(stripExifOnExport = intent.enabled) }
            is CameraIntent.SetGrid -> updateSettings { it.copy(gridEnabled = intent.enabled) }
            is CameraIntent.SetAudioZoom -> updateSettings { it.copy(audioZoomEnabled = intent.enabled) }
            CameraIntent.ToggleGrid -> updateSettings { it.copy(gridEnabled = !it.gridEnabled) }
            CameraIntent.ToggleAfAeLock -> toggleAfAeLock()
            is CameraIntent.SetBurstShots -> updateSettings { it.copy(burstShots = intent.count.coerceIn(2, 50)) }
            is CameraIntent.SetHdrFrames -> updateSettings { it.copy(hdrFrames = intent.frames.coerceIn(3, 7)) }
            is CameraIntent.SetHdrStep -> updateSettings { it.copy(hdrStepEv = intent.stepEv.coerceIn(1, 3)) }
            is CameraIntent.SetTimelapseShots -> updateSettings { it.copy(timelapseShots = intent.shots.coerceIn(2, 300)) }
            is CameraIntent.SetGridStyle -> updateSettings { it.copy(gridStyle = intent.style) }
            is CameraIntent.SetAspectMask -> updateSettings { it.copy(aspectMask = intent.mask) }
            is CameraIntent.SetLevel -> updateSettings { it.copy(levelEnabled = intent.enabled) }
            is CameraIntent.SetFilter -> updateSettings { it.copy(filter = intent.filter) }
            is CameraIntent.SetVideoQuality -> updateSettings { it.copy(videoQuality = intent.quality) }
            is CameraIntent.SetShutterSound -> updateSettings { it.copy(shutterSound = intent.enabled) }
            CameraIntent.CycleTimer -> updateSettings {
                it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 })
            }
            CameraIntent.ClearToast -> _ui.update { it.copy(toast = null) }
        }
    }

    private fun setMode(mode: CaptureMode) {
        if (_ui.value.settings.captureMode == mode) return
        if (_ui.value.recording.active) engine.stopRecording()
        captureJob?.cancel()
        updateSettings { it.copy(captureMode = mode) }
    }

    private fun setLens(lens: LensFacing) {
        if (_ui.value.recording.active) engine.stopRecording()
        _ui.update { it.copy(camera = it.camera.copy(afLocked = false, aeLocked = false)) }
        updateSettings { it.copy(lensFacing = lens, zoomRatio = 1f) }
    }

    private fun toggleOverlay(kind: OverlayKind) {
        updatePro {
            when (kind) {
                OverlayKind.HISTOGRAM -> it.copy(histogramEnabled = !it.histogramEnabled)
                OverlayKind.ZEBRA -> it.copy(zebraEnabled = !it.zebraEnabled)
                OverlayKind.PEAKING -> it.copy(focusPeakingEnabled = !it.focusPeakingEnabled)
            }
        }
    }

    private fun updateSettings(t: (CameraSettings) -> CameraSettings) = prefs.update(t)

    /** Updates pro controls and pushes them to the live camera session. */
    private fun updatePro(t: (ProControls) -> ProControls) {
        prefs.update { it.copy(proControls = t(it.proControls)) }
        engine.applyPro(prefs.settings.value.proControls)
    }

    // ---- capture state machine ------------------------------------------

    private fun shutter() {
        val s = _ui.value.settings
        if (s.captureMode == CaptureMode.VIDEO || s.captureMode == CaptureMode.SLOW_MOTION) {
            toggleRecording(s)
            return
        }
        // A running countdown / timelapse is cancelled by pressing the shutter again.
        if (captureJob?.isActive == true) {
            if (_ui.value.countdown != null || s.captureMode == CaptureMode.TIMELAPSE) captureJob?.cancel()
            return
        }
        captureJob = viewModelScope.launch {
            try {
                runCountdown(s.timerSeconds)
                capture(s)
            } catch (e: CancellationException) {
                throw e
            } finally {
                _ui.update {
                    it.copy(countdown = null, busyLabel = null, camera = it.camera.copy(isCapturing = false, burstCount = 0))
                }
            }
        }
    }

    private suspend fun runCountdown(seconds: Int) {
        for (left in seconds downTo 1) {
            _ui.update { it.copy(countdown = left) }
            delay(1000)
        }
        _ui.update { it.copy(countdown = null) }
    }

    private suspend fun capture(s: CameraSettings) {
        _ui.update { it.copy(camera = it.camera.copy(isCapturing = true, error = null)) }
        val uri: Uri?
        val error: Throwable?
        when (s.captureMode) {
            CaptureMode.BURST -> {
                _shutterFired.tryEmit(Unit)
                _ui.update { it.copy(busyLabel = "Burst ×${s.burstShots}") }
                val r = engine.takeBurst(s, s.burstShots)
                uri = r.getOrNull()?.firstOrNull()
                error = r.exceptionOrNull()
                r.getOrNull()?.forEach { media.notifyNewMedia(it, false) }
            }
            CaptureMode.TIMELAPSE -> {
                uri = runTimelapse(s)
                error = if (uri != null) null else IllegalStateException("Timelapse produced no frames")
            }
            CaptureMode.HDR, CaptureMode.NIGHT -> {
                _shutterFired.tryEmit(Unit)
                _ui.update { it.copy(busyLabel = if (s.captureMode == CaptureMode.HDR) "Merging HDR… hold steady" else "Stacking night shot… hold steady") }
                val r = engine.takePhoto(s)
                uri = r.getOrNull()
                error = r.exceptionOrNull()
            }
            else -> {
                _shutterFired.tryEmit(Unit)
                val r = engine.takePhoto(s)
                uri = r.getOrNull()
                error = r.exceptionOrNull()
            }
        }
        if (uri != null) {
            if (s.captureMode != CaptureMode.BURST) media.notifyNewMedia(uri, false)
            _ui.update { it.copy(camera = it.camera.copy(lastCaptureUri = uri)) }
        } else if (error != null) {
            toast(error.message ?: "Capture failed")
        }
    }

    private fun toggleRecording(s: CameraSettings) {
        if (_ui.value.recording.active) {
            engine.stopRecording()
            return
        }
        engine.startRecording(s).onFailure { toast(it.message ?: "Could not start recording") }
    }

    /** Intervalometer: N stills spaced by the configured interval. Last frame is kept. */
    private suspend fun runTimelapse(s: CameraSettings): Uri? {
        var last: Uri? = null
        val total = s.timelapseShots.coerceIn(2, 300)
        for (i in 0 until total) {
            _shutterFired.tryEmit(Unit)
            val r = engine.takePhoto(s)
            r.getOrNull()?.let {
                last = it
                media.notifyNewMedia(it, false)
            }
            _ui.update {
                it.copy(
                    camera = it.camera.copy(burstCount = i + 1),
                    busyLabel = "Timelapse ${i + 1}/$total — tap shutter to stop",
                )
            }
            if (i < total - 1) delay(s.timelapseIntervalMs.coerceIn(500L, 60_000L))
        }
        return last
    }

    private fun toggleAfAeLock() {
        val locked = !(_ui.value.camera.afLocked || _ui.value.camera.aeLocked)
        engine.lockAfAe(locked)
        _ui.update {
            it.copy(
                camera = it.camera.copy(afLocked = locked, aeLocked = locked),
                toast = if (locked) "AF/AE locked" else "AF/AE unlocked",
            )
        }
    }

    /** Binds the camera to the given lifecycle. Safe to call again on settings change. */
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        // Rapid setting changes must not leave two binds racing; the newest wins.
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            engine.bind(lifecycleOwner, previewView, prefs.settings.value)
                .onSuccess { _ui.update { it.copy(camera = it.camera.copy(isBound = true, error = null)) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            camera = it.camera.copy(isBound = false, error = e.message ?: e.javaClass.simpleName),
                            toast = e.message,
                        )
                    }
                }
        }
    }

    /** [x]/[y] are pixel coordinates inside the preview view. */
    fun onTapToFocus(x: Float, y: Float) {
        engine.tapToFocus(x, y)
    }

    fun consumeLastCapture(): Uri? {
        val u = _ui.value.camera.lastCaptureUri
        _ui.update { it.copy(camera = it.camera.copy(lastCaptureUri = null)) }
        return u
    }

    private fun toast(msg: String?) {
        if (msg != null) _ui.update { it.copy(toast = msg) }
    }

    /** Presets: save current setup, recall, or delete by name. */
    fun savePreset(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            presetStore.save(name.trim(), _ui.value.settings)
            toast("Preset saved")
        }
    }

    fun applyPreset(name: String) {
        val p = _ui.value.presets.firstOrNull { it.name == name } ?: return
        updateSettings { p.settings.copy(zoomRatio = 1f) }
        engine.applyPro(p.settings.proControls)
        toast("Preset \"$name\"")
    }

    fun deletePreset(name: String) {
        viewModelScope.launch { presetStore.delete(name) }
    }

    override fun onCleared() {
        // Use cases are lifecycle-bound, so CameraX releases the camera itself.
        thermal.stop()
    }
}
