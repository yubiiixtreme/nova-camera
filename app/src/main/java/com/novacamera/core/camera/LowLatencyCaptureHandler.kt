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
import com.novacamera.domain.model.CaptureMode
import com.novacamera.processing.HdrMerger
import com.novacamera.processing.NightStacker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Still-capture workflows built on a bound [ImageCapture].
 *
 * - Single shot: written straight to MediaStore.
 * - Burst: rapid sequential captures, each saved.
 * - HDR / Night: frames go to private cache files, are aligned and fused by
 *   [HdrMerger] / [NightStacker], and ONLY the merged result reaches the
 *   gallery (previously every intermediate frame was left behind as clutter).
 */
@Singleton
class LowLatencyCaptureHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hdrMerger: HdrMerger,
    private val nightStacker: NightStacker,
) {
    suspend fun captureToMediaStore(capture: ImageCapture, location: android.location.Location? = null): Result<Uri> {
        val file = captureToFile(capture, location).getOrElse { return Result.failure(it) }
        return try {
            withContext(Dispatchers.IO) { insertFile(file) }?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not save photo"))
        } finally {
            file.delete()
        }
    }

    suspend fun captureBracketed(
        capture: ImageCapture,
        settings: CameraSettings,
        setExposure: suspend (Int) -> Unit,
        restoreExposure: suspend () -> Unit,
        location: android.location.Location? = null,
    ): Result<Uri> {
        val isHdr = settings.captureMode == CaptureMode.HDR
        val evs = if (isHdr) {
            bracketEvs(settings.hdrFrames, settings.hdrStepEv)
        } else {
            List(NIGHT_FRAMES) { 0 }
        }
        val frames = mutableListOf<File>()
        val merged = File(context.cacheDir, "merged_${System.nanoTime()}.jpg")
        try {
            for (ev in evs) {
                setExposure(ev)
                val r = captureToFile(capture, location)
                if (r.isFailure) {
                    return if (frames.isEmpty()) Result.failure(r.exceptionOrNull()!!) else saveBest(frames)
                }
                frames += r.getOrThrow()
            }
            val ok = if (isHdr) hdrMerger.merge(frames, merged) else nightStacker.stack(frames, merged)
            val source = if (ok) merged else frames[frames.size / 2]
            return withContext(Dispatchers.IO) { insertFile(source) }?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not save photo"))
        } finally {
            runCatching { restoreExposure() }
            frames.forEach { it.delete() }
            merged.delete()
        }
    }

    private suspend fun saveBest(frames: List<File>): Result<Uri> =
        withContext(Dispatchers.IO) { insertFile(frames[frames.size / 2]) }?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not save photo"))

    suspend fun captureBurst(
        count: Int,
        save: suspend () -> Result<Uri>,
    ): Result<List<Uri>> {
        val out = mutableListOf<Uri>()
        repeat(count.coerceIn(2, 50)) {
            val r = save()
            if (r.isSuccess) {
                out += r.getOrThrow()
            } else if (out.isEmpty()) {
                return Result.failure(r.exceptionOrNull() ?: IllegalStateException("Burst failed"))
            } else {
                BurstManager.registerStack(out)
                return Result.success(out)
            }
        }
        BurstManager.registerStack(out)
        return Result.success(out)
    }

    suspend fun captureToFile(capture: ImageCapture, location: android.location.Location? = null): Result<File> =
        suspendCancellableCoroutine { cont ->
            val file = File(context.cacheDir, "frame_${System.nanoTime()}.jpg")
            val opts = ImageCapture.OutputFileOptions.Builder(file)
                .apply { if (location != null) setMetadata(ImageCapture.Metadata().apply { this.location = location }) }
                .build()
            capture.takePicture(
                opts,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(Result.success(file))
                    }

                    override fun onError(e: ImageCaptureException) {
                        file.delete()
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
                },
            )
        }

    /** Copies [file] into Pictures/NovaCamera and returns its content Uri. */
    fun insertFile(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NOVA_${timestamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NovaCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("No output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun timestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis())

    companion object {
        const val NIGHT_FRAMES = 5

        /** Symmetric EV sweep around 0, e.g. (3,1)->[-1,0,1], (5,2)->[-4..4 step 2]. */
        fun bracketEvs(frames: Int, stepEv: Int): List<Int> {
            val f = (if (frames % 2 == 0) frames + 1 else frames).coerceIn(3, 7)
            val step = stepEv.coerceIn(1, 3)
            val half = f / 2
            return (-half..half).map { it * step }
        }
    }
}
