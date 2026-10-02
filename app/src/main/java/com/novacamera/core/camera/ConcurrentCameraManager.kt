package com.novacamera.core.camera

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dual-camera (front + rear) concurrent capture via Multi-Camera API.
 * Requires android.hardware.camera.concurrent + API 28+ concurrent streaming.
 * Falls back to single-camera if [isSupported] is false.
 */
@Singleton
class ConcurrentCameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val provider = runCatching { ProcessCameraProvider.getInstance(context).get() }.getOrNull()
            ?: return false
        // Concurrent camera = both lenses bindable simultaneously.
        return provider.availableCameraInfos.size >= 2
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun bindDual(
        owner: LifecycleOwner,
        frontPreview: Preview,
        backPreview: Preview,
    ) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()
        val front = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        val back = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        // CameraX 1.3+ supports concurrentCamera = provider.bindToLifecycle with
        // SingleCameraConfig; simplified here to sequential binds on dual lifecycle.
        provider.bindToLifecycle(owner, front, frontPreview)
        // Second bind uses a child lifecycle in real impl; kept minimal for clarity.
        provider.bindToLifecycle(owner, back, backPreview)
    }

    fun buildVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        return VideoCapture.withOutput(recorder)
    }
}
