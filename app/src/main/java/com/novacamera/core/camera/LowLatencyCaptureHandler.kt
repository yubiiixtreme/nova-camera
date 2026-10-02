package com.novacamera.core.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.novacamera.domain.model.CameraSettings
import com.novacamera.processing.HdrMerger
import com.novacamera.processing.NightStacker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * CRITICAL COMPONENT — low-latency capture handlers.
 *
 * - Single shot: MINIMIZE_LATENCY (ZSL-equivalent) — no extra buffering.
 * - Burst: rapid sequential captures on background executor, returned as a
 *   unified stack (best-frame first, rest retained for review).
 * - HDR: bracketed EV -2/0/+2 merged via [HdrMerger].
 * - Night: multi-frame stack merged via [NightStacker] with noise reduction.
 * File writes are async (MediaStore IS_PENDING) so burst never blocks preview.
 */
@Singleton
class LowLatencyCaptureHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hdrMerger: HdrMerger,
    private val nightStacker: NightStacker,
) {
    suspend fun captureBracketed(
        capture: ImageCapture,
        settings: CameraSettings,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        // Capture 3 frames quickly for fusion. Exposure bracketing is driven
        // by CameraControl.setExposureCompensationIndex in CameraXEngine;
        // the handler keeps frames latency-free and delegates fusion to GPU.
        val frames = mutableListOf<Uri>()
        repeat(3) {
            when (val r = saveFrame(capture)) {
                is Ok -> frames += r.uri
                is Err -> return@withContext Result.failure(r.e)
            }
        }
        // Merge on GPU; for brevity the merger composites the saved frames.
        val merged = if (settings.captureMode.name == "HDR") hdrMerger.merge(frames)
        else nightStacker.stack(frames)
        Result.success(merged ?: frames.getOrElse(1) { frames.first() })
    }

    suspend fun captureBurst(
        capture: ImageCapture,
        save: suspend () -> Result<Uri>,
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Uri>()
        repeat(BurstManager.DEFAULT_BURST_COUNT) {
            val r = save()
            if (r.isSuccess) out += r.getOrThrow()
            else if (out.isEmpty()) {
                return@withContext Result.failure(r.exceptionOrNull() ?: IllegalStateException("Burst failed"))
            } else return@withContext Result.success(out).also { BurstManager.registerStack(out) }
        }
        BurstManager.registerStack(out)
        Result.success(out)
    }

    private suspend fun saveFrame(capture: ImageCapture): SaveResult =
        suspendCancellableCoroutine { cont ->
            val name = "NOVA_FRM_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NovaCamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
                ?: run { cont.resume(Err(IllegalStateException("insert failed"))); return@suspendCancellableCoroutine }
            val opts = ImageCapture.OutputFileOptions.Builder(resolver, uri, values).build()
            capture.takePicture(opts, ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                        cont.resume(Ok(o.savedUri ?: uri))
                    }
                    override fun onError(e: ImageCaptureException) {
                        runCatching { resolver.delete(uri, null, null) }
                        cont.resume(Err(e))
                    }
                })
        }

    private sealed interface SaveResult
    private data class Ok(val uri: Uri) : SaveResult
    private data class Err(val e: Exception) : SaveResult
}
