package com.novacamera.domain.usecase

import android.net.Uri
import com.novacamera.core.camera.CameraEngine
import com.novacamera.data.repository.MediaRepository
import com.novacamera.domain.model.CameraSettings
import javax.inject.Inject

class CapturePhotoUseCase @Inject constructor(
    private val engine: CameraEngine,
    private val media: MediaRepository,
) {
    suspend operator fun invoke(settings: CameraSettings): Result<Uri> {
        val result = engine.takePhoto(settings)
        result.getOrNull()?.let { media.notifyNewMedia(it, isVideo = false) }
        return result
    }
}

class CaptureBurstUseCase @Inject constructor(
    private val engine: CameraEngine,
    private val media: MediaRepository,
) {
    suspend operator fun invoke(settings: CameraSettings, count: Int = 10): Result<List<Uri>> {
        val result = engine.takeBurst(settings, count)
        result.getOrNull()?.forEach { media.notifyNewMedia(it, isVideo = false) }
        return result
    }
}

class SetZoomUseCase @Inject constructor(private val engine: CameraEngine) {
    operator fun invoke(ratio: Float) = engine.setZoom(ratio)
}

class ToggleTorchUseCase @Inject constructor(private val engine: CameraEngine) {
    operator fun invoke(on: Boolean) = engine.setTorch(on)
}
