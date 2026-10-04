package com.novacamera.core.camera

import android.content.Context
import android.net.Uri
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.ProControls
import com.novacamera.domain.model.VideoQuality
import com.novacamera.media.VideoRecorderController
import com.novacamera.media.VideoResult
import com.novacamera.processing.FilterBaker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * CameraX session owner.
 *
 * Key rule: a session never binds more than THREE use cases. The previous
 * version bound Preview + ImageCapture + VideoCapture + ImageAnalysis (four),
 * which most phones reject, so `bindToLifecycle` threw and the viewfinder
 * stayed black — "nothing works". Now:
 *   photo modes → Preview + ImageCapture (+ ImageAnalysis only when a
 *                 histogram / zebra / peaking overlay is on)
 *   video modes → Preview + VideoCapture
 * If a device still refuses, [bind] retries with the minimal pair.
 */
@Singleton
class CameraXEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureHandler: LowLatencyCaptureHandler,
    private val proController: Camera2ProController,
    private val videoController: VideoRecorderController,
) : CameraEngine {

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewRef: WeakReference<PreviewView>? = null

    private var evStep = 0f
    private var userEvIndex = 0
    private var pendingFlash = FlashMode.OFF

    private var zoomLive: LiveData<ZoomState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var torchLive: LiveData<Int>? = null
    private var torchObserver: Observer<Int>? = null

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val _zoomState = MutableStateFlow(1f)
    override val zoomState: StateFlow<Float> = _zoomState.asStateFlow()
    private val _torchState = MutableStateFlow(false)
    override val torchState: StateFlow<Boolean> = _torchState.asStateFlow()
    private val _frameStats = MutableStateFlow<FrameStats?>(null)
    override val frameStats: StateFlow<FrameStats?> = _frameStats.asStateFlow()
    private val _caps = MutableStateFlow(CameraCaps())
    override val caps: StateFlow<CameraCaps> = _caps.asStateFlow()
    override val recording: StateFlow<RecordingState> = videoController.state
    override val videoResults: SharedFlow<VideoResult> = videoController.results

    /** Keeps captured photos upright regardless of how the UI is (or isn't) rotating. */
    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when {
                orientation >= 315 || orientation < 45 -> Surface.ROTATION_0
                orientation in 225..314 -> Surface.ROTATION_90
                orientation in 135..224 -> Surface.ROTATION_180
                else -> Surface.ROTATION_270
            }
            imageCapture?.targetRotation = rotation
            videoCapture?.targetRotation = rotation
        }
    }

    override suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        settings: CameraSettings,
    ): Result<Unit> = try {
        val provider = getProvider()
        awaitLayout(previewView) // the shared viewport needs a measured PreviewView
        if (videoController.state.value.active) videoController.abort()
        provider.unbindAll()
        detachObservers()
        previewRef = WeakReference(previewView)
        pendingFlash = settings.flashMode

        val selector = selectorFor(settings.lensFacing)
        if (!provider.hasCamera(selector)) {
            throw IllegalStateException("No ${settings.lensFacing.name.lowercase()} camera on this device")
        }

        val videoMode = settings.captureMode.isVideo()
        val pro = settings.proControls
        val wantAnalysis = !videoMode && (pro.histogramEnabled || pro.zebraEnabled || pro.focusPeakingEnabled)

        fun resolutionFor(ratio: Int) = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                if (ratio == 0) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY,
            ).build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionFor(if (videoMode) 1 else 0))
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val primary: UseCase
        imageCapture = null
        videoCapture = null
        if (videoMode) {
            val vc = buildVideoCapture(settings)
            videoCapture = vc
            primary = vc
        } else {
            val ic = ImageCapture.Builder()
                .setResolutionSelector(resolutionFor(0))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(95)
                .build()
            imageCapture = ic
            primary = ic
        }

        val analysis = if (wantAnalysis) {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, FrameStatsAnalyzer({ s -> _frameStats.value = s })) }
        } else {
            _frameStats.value = null
            null
        }

        val full = listOfNotNull(preview, primary, analysis)
        camera = try {
            provider.bindToLifecycle(lifecycleOwner, selector, group(previewView, full))
        } catch (e: IllegalArgumentException) {
            // Device can't do this combination — fall back to the guaranteed pair.
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, group(previewView, listOf(preview, primary)))
        }

        val cam = checkNotNull(camera)
        observeCamera(lifecycleOwner, cam)
        publishCaps(provider, cam)
        applySessionSettings(cam, settings)

        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun group(previewView: PreviewView, cases: List<UseCase>): UseCaseGroup {
        val b = UseCaseGroup.Builder()
        // Shared viewport => photo/video framing matches exactly what the viewfinder shows.
        previewView.viewPort?.let { b.setViewPort(it) }
        cases.forEach { b.addUseCase(it) }
        return b.build()
    }

    private fun buildVideoCapture(settings: CameraSettings): VideoCapture<Recorder> {
        val quality = when (settings.videoQuality) {
            VideoQuality.FHD_30, VideoQuality.FHD_60 -> Quality.FHD
            VideoQuality.UHD_4K_30, VideoQuality.UHD_4K_60, VideoQuality.UHD_8K_30 -> Quality.UHD
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality)))
            .build()
        val builder = VideoCapture.Builder(recorder)
        if (settings.videoQuality == VideoQuality.FHD_60 || settings.videoQuality == VideoQuality.UHD_4K_60) {
            builder.setTargetFrameRate(Range(60, 60))
        }
        return builder.build()
    }

    private fun observeCamera(owner: LifecycleOwner, cam: Camera) {
        val zObs = Observer<ZoomState> { _zoomState.value = it.zoomRatio }
        val tObs = Observer<Int> { _torchState.value = it == TorchState.ON }
        zoomLive = cam.cameraInfo.zoomState.also { it.observe(owner, zObs) }
        torchLive = cam.cameraInfo.torchState.also { it.observe(owner, tObs) }
        zoomObserver = zObs
        torchObserver = tObs
    }

    private fun detachObservers() {
        zoomObserver?.let { zoomLive?.removeObserver(it) }
        torchObserver?.let { torchLive?.removeObserver(it) }
        zoomObserver = null; torchObserver = null; zoomLive = null; torchLive = null
    }

    private fun publishCaps(provider: ProcessCameraProvider, cam: Camera) {
        val info = cam.cameraInfo
        val zs = info.zoomState.value
        val ev = info.exposureState
        evStep = ev.exposureCompensationStep.toFloat()
        val base = CameraCaps(
            minZoom = zs?.minZoomRatio ?: 1f,
            maxZoom = zs?.maxZoomRatio ?: 1f,
            hasFlash = info.hasFlashUnit(),
            hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA),
            hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA),
            evMin = if (ev.isExposureCompensationSupported) ev.exposureCompensationRange.lower * evStep else 0f,
            evMax = if (ev.isExposureCompensationSupported) ev.exposureCompensationRange.upper * evStep else 0f,
        )
        _caps.value = proController.readCaps(cam, base)
    }

    /** Re-applies everything that lives on the camera rather than in the use cases. */
    private fun applySessionSettings(cam: Camera, settings: CameraSettings) {
        setFlash(settings.flashMode)
        val c = _caps.value
        cam.cameraControl.setZoomRatio(settings.zoomRatio.coerceIn(c.minZoom, c.maxZoom))
        applyPro(settings.proControls)
    }

    override fun unbindAll() {
        orientationListener.disable()
        if (videoController.state.value.active) videoController.abort()
        detachObservers()
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        camera = null; imageCapture = null; videoCapture = null
    }

    // ---- photo -----------------------------------------------------------

    override suspend fun takePhoto(settings: CameraSettings): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not ready"))
        val result = when (settings.captureMode) {
            CaptureMode.HDR, CaptureMode.NIGHT -> {
                // Flash on every bracket frame would wreck the merge.
                val flash = capture.flashMode
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                try {
                    captureHandler.captureBracketed(
                        capture, settings,
                        setExposure = { shiftEv(it.toFloat() + settings.proControls.exposureCompensationEv) },
                        restoreExposure = { setEvIndex(evToIndex(settings.proControls.exposureCompensationEv)) },
                        location = locationFor(settings),
                    )
                } finally {
                    capture.flashMode = flash
                }
            }
            else -> captureHandler.captureToMediaStore(capture, locationFor(settings))
        }
        val uri = result.getOrNull() ?: return result
        if (settings.filter != com.novacamera.domain.model.LiveFilter.NONE) {
            withContext(Dispatchers.IO) { FilterBaker.bake(context.contentResolver, uri, settings.filter) }
        }
        return result
    }

    override suspend fun takeBurst(settings: CameraSettings, count: Int): Result<List<Uri>> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not ready"))
        return captureHandler.captureBurst(count) {
            val r = captureHandler.captureToMediaStore(capture, locationFor(settings))
            r.getOrNull()?.let { uri ->
                if (settings.filter != com.novacamera.domain.model.LiveFilter.NONE) {
                    withContext(Dispatchers.IO) { FilterBaker.bake(context.contentResolver, uri, settings.filter) }
                }
            }
            r
        }
    }

    /** Last known fix, only when the user opted in AND granted location permission. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun locationFor(settings: CameraSettings): android.location.Location? {
        if (!settings.locationTagging) return null
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return null
        return runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
    }

    private fun evToIndex(ev: Float): Int = if (evStep > 0f) (ev / evStep).roundToInt() else 0

    private suspend fun shiftEv(ev: Float) {
        val target = evToIndex(ev)
        if (target == userEvIndex) return // nothing to converge (night stacks re-use the same exposure)
        setEvIndex(target)
        delay(350) // let AE converge on the shifted exposure
    }

    private suspend fun setEvIndex(index: Int) {
        val cam = camera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        val clamped = index.coerceIn(range.lower, range.upper)
        userEvIndex = clamped
        runCatching { cam.cameraControl.setExposureCompensationIndex(clamped).await() }
    }

    // ---- controls --------------------------------------------------------

    override fun setZoom(ratio: Float) {
        val c = _caps.value
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(c.minZoom, c.maxZoom))
    }

    override fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    override fun setFlash(mode: FlashMode) {
        pendingFlash = mode
        imageCapture?.flashMode = when (mode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        // Torch stays lit for TORCH, and is forced off otherwise.
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(mode == FlashMode.TORCH)
    }

    override fun applyPro(pro: ProControls) {
        val cam = camera ?: return
        val manualExposure = _caps.value.manualSensor && (pro.iso != null || pro.shutterSpeedSec != null)
        if (!manualExposure && cam.cameraInfo.exposureState.isExposureCompensationSupported) {
            val range = cam.cameraInfo.exposureState.exposureCompensationRange
            val idx = evToIndex(pro.exposureCompensationEv).coerceIn(range.lower, range.upper)
            if (idx != userEvIndex) {
                userEvIndex = idx
                cam.cameraControl.setExposureCompensationIndex(idx)
            }
        }
        proController.apply(cam, pro, _caps.value)
    }

    override fun lockAfAe(lock: Boolean) {
        val cam = camera ?: return
        if (lock) {
            val pv = previewRef?.get() ?: return
            val point = pv.meteringPointFactory.createPoint(pv.width / 2f, pv.height / 2f)
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
                ).disableAutoCancel().build(),
            )
        } else {
            cam.cameraControl.cancelFocusAndMetering()
        }
    }

    override fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val pv = previewRef?.get() ?: return
        // PreviewView's factory accounts for crop, rotation and front-camera mirroring.
        val point = pv.meteringPointFactory.createPoint(x, y)
        cam.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build(),
        )
    }

    // ---- video -----------------------------------------------------------

    override fun startRecording(settings: CameraSettings): Result<Unit> {
        val vc = videoCapture ?: return Result.failure(IllegalStateException("Camera not in video mode"))
        return videoController.start(vc)
    }

    override fun stopRecording() = videoController.stop()
    override fun pauseRecording() = videoController.pause()
    override fun resumeRecording() = videoController.resume()

    // ---- plumbing --------------------------------------------------------

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalLensFacing::class)
    private fun selectorFor(lens: LensFacing): CameraSelector = CameraSelector.Builder()
        .requireLensFacing(
            when (lens) {
                LensFacing.BACK -> CameraSelector.LENS_FACING_BACK
                LensFacing.FRONT -> CameraSelector.LENS_FACING_FRONT
                LensFacing.EXTERNAL -> CameraSelector.LENS_FACING_EXTERNAL
            },
        ).build()

    private suspend fun getProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private suspend fun awaitLayout(v: PreviewView) {
        if (v.width > 0 && v.height > 0) return
        withTimeoutOrNull(1500) {
            suspendCancellableCoroutine<Unit> { cont ->
                val l = object : android.view.View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        view: android.view.View, left: Int, top: Int, right: Int, bottom: Int,
                        ol: Int, ot: Int, or: Int, ob: Int,
                    ) {
                        if (view.width > 0 && view.height > 0) {
                            view.removeOnLayoutChangeListener(this)
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
                v.addOnLayoutChangeListener(l)
                cont.invokeOnCancellation { v.removeOnLayoutChangeListener(l) }
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
}

internal fun CaptureMode.isVideo() = this == CaptureMode.VIDEO || this == CaptureMode.SLOW_MOTION
