package com.novacamera.core.camera

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.novacamera.domain.model.CameraSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified camera engine abstraction.
 * Primary impl = [CameraXEngine]; advanced manual/RAW paths delegate to
 * [Camera2ProController]; dual-cam uses [ConcurrentCameraManager].
 */
interface CameraEngine {
    val zoomState: StateFlow<Float>
    val torchState: StateFlow<Boolean>

    /** Bind Preview + ImageCapture + VideoCapture + Analysis. Safe to re-call on settings change. */
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
    fun lockAfAe(lock: Boolean)
    fun tapToFocus(x: Float, y: Float)
}
