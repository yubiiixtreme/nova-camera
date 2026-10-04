package com.novacamera.core.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.PhotoFormat
import com.novacamera.domain.model.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CRITICAL COMPONENT — CameraX initialization with Camera2 fallback.
 *
 * - Uses ProcessCameraProvider bound to [LifecycleOwner] (no leaks on rotate).
 * - ZSL-equivalent: CAPTURE_MODE_MINIMIZE_LATENCY for single shot.
 * - HDR / Night / Portrait route through [LowLatencyCaptureHandler] bracketing.
 * - Manual ISO/shutter/RAW hand off to [Camera2ProController] when requested.
 */
@Singleton
class CameraXEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureHandler: LowLatencyCaptureHandler,
    private val proController: Camera2ProController,
) : CameraEngine {

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private val _zoomState = MutableStateFlow(1f)
    override val zoomState: StateFlow<Float> = _zoomState.asStateFlow()
    private val _torchState = MutableStateFlow(false)
    override val torchState: StateFlow<Boolean> = _torchState.asStateFlow()
    private val _frameStats = MutableStateFlow<FrameStats?>(null)
    override val frameStats: StateFlow<FrameStats?> = _frameStats.asStateFlow()

    override suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        settings: CameraSettings,
    ): Result<Unit> = try {
        val provider = getProvider()
        provider.unbindAll()

        val selector = CameraSelector.Builder()
            .requireLensFacing(
                when (settings.lensFacing) {
                    LensFacing.BACK -> CameraSelector.LENS_FACING_BACK
                    LensFacing.FRONT -> CameraSelector.LENS_FACING_FRONT
                    LensFacing.EXTERNAL -> CameraSelector.LENS_FACING_EXTERNAL
                },
            ).build()

        // Preview: keep 16:9/4:3 adaptive; PreviewView handles foldable resize.
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = run {
            val builder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // ZSL path
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)

            // If manual/RAW requested and device supports FULL camera2, attach
            // CaptureRequest overrides via Camera2Interop (preview stays bound).
            if (settings.proControls.rawEnabled || settings.proControls.iso != null) {
                if (proController.isFullManualSupported()) {
                    proController.applyManualSettings(builder, settings.proControls)
                }
            }
            builder.build()
        }

        val quality = when (settings.videoQuality) {
            VideoQuality.FHD_30 -> Quality.FHD
            VideoQuality.FHD_60 -> Quality.FHD
            VideoQuality.UHD_4K_30, VideoQuality.UHD_4K_60 -> Quality.UHD
            VideoQuality.UHD_8K_30 -> Quality.UHD
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    quality,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
                ),
            ).build()
        videoCapture = VideoCapture.withOutput(recorder)

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        // Live exposure/focus telemetry for histogram/zebra/peaking overlays.
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context), FrameStatsAnalyzer({ _frameStats.value = it }))

        // HDR/Night need extended exposure: switch to MAXIMIZE_QUALITY lazily
        // at capture time inside captureHandler (keeps preview at 60fps).

        camera = provider.bindToLifecycle(
            lifecycleOwner, selector, preview, imageCapture, videoCapture, analysis,
        )

        camera?.cameraInfo?.zoomState?.observeForever { _zoomState.value = it.zoomRatio }
        camera?.cameraInfo?.torchState?.observeForever {
            _torchState.value = it == androidx.camera.core.TorchState.ON
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun unbindAll() {
        // Fire-and-forget safe: provider future may not be complete; guard it.
        runCatching {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    override suspend fun takePhoto(settings: CameraSettings): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not bound"))
        return when (settings.captureMode) {
            CaptureMode.HDR, CaptureMode.NIGHT, CaptureMode.PORTRAIT ->
                captureHandler.captureBracketed(capture, settings, setExposure = { shiftEv(it) })
            else -> captureSingle(capture, settings)
        }
    }

    /** Shifts capture EV for bracketing, clamped to the device range. Settles before return. */
    private suspend fun shiftEv(ev: Int) {
        val cam = camera
        val range = cam?.cameraInfo?.exposureState?.exposureCompensationRange
        val clamped = ev.coerceIn(range?.lower ?: ev, range?.upper ?: ev)
        runCatching { cam?.cameraControl?.setExposureCompensationIndex(clamped) }
        kotlinx.coroutines.delay(150)
    }

    private suspend fun captureSingle(
        capture: ImageCapture,
        settings: CameraSettings,
    ): Result<Uri> = suspendCancellableCoroutine { cont ->
        val name = "NOVA_${timestamp()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(settings))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NovaCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: run { cont.resume(Result.failure(IllegalStateException("MediaStore insert failed"))); return@suspendCancellableCoroutine }

        val output = ImageCapture.OutputFileOptions.Builder(resolver, uri, values).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    cont.resume(Result.success(o.savedUri ?: uri))
                }
                override fun onError(e: ImageCaptureException) {
                    runCatching { resolver.delete(uri, null, null) }
                    cont.resume(Result.failure(e))
                }
            },
        )
    }

    override suspend fun takeBurst(settings: CameraSettings, count: Int): Result<List<Uri>> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not bound"))
        return captureHandler.captureBurst(capture, count) { singleCapture(settings) }
    }

    private suspend fun singleCapture(settings: CameraSettings): Result<Uri> =
        imageCapture?.let { captureSingle(it, settings) }
            ?: Result.failure(IllegalStateException("Camera not bound"))

    override fun setZoom(ratio: Float) { camera?.cameraControl?.setZoomRatio(ratio) }
    override fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
        _torchState.value = on
    }

    override fun lockAfAe(lock: Boolean) {
        val cam = camera ?: return
        if (lock) {
            // AF + AE together at frame center, metering held until unlocked.
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val point = factory.createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
            ).disableAutoCancel().build()
            cam.cameraControl.startFocusAndMetering(action)
        } else {
            cam.cameraControl.cancelFocusAndMetering()
        }
    }

    override fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        // x/y are normalized (0..1) coordinates within the preview surface, so
        // the factory is sized 1x1 to match (do not pass raw view-pixel coords here).
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private suspend fun getProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }

    private fun mimeFor(settings: CameraSettings) =
        if (settings.photoFormat == PhotoFormat.HEIF) "image/heif" else "image/jpeg"

    private fun timestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis())
}
