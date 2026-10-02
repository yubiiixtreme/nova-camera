package com.novacamera.domain.model

import android.net.Uri

/** Reactive camera pipeline state (MVI/MVVM single source of truth). */
data class CameraState(
    val isBound: Boolean = false,
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val isPausedRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 10f,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = -12..12,
    val torchOn: Boolean = false,
    val afLocked: Boolean = false,
    val aeLocked: Boolean = false,
    val lastCaptureUri: Uri? = null,
    val burstCount: Int = 0,
    val activeScene: SceneType = SceneType.UNKNOWN,
    val faceCount: Int = 0,
    val thermalThrottled: Boolean = false,
    val error: String? = null,
)

/** One saved media item. */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val takenAt: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long? = null,
    val isVideo: Boolean = false,
    val inVault: Boolean = false,
)
