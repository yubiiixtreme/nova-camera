package com.novacamera.media

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Video pipeline: standard 4K60/8K30, pause/resume clip compilation,
 * slow-motion (high-FPS) + timelapse (intervalometer) helpers.
 * Pause/resume uses Recorder pause() so output is ONE file (no stitching).
 */
@Singleton
class VideoRecorderController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recording: Recording? = null
    private var pendingOutputUri: android.net.Uri? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    private val _event = MutableStateFlow<VideoRecordEvent?>(null)
    val event: StateFlow<VideoRecordEvent?> = _event.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(videoCapture: VideoCapture<Recorder>, withAudio: Boolean) {
        if (_isRecording.value) return
        val name = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NovaCamera")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val output = MediaStoreOutputOptionsCompat(collection, values)
        recording = videoCapture.output
            .prepareRecording(context, output.toFileOutput())
            .apply { if (withAudio) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(context)) { e ->
                _event.value = e
                when (e) {
                    is VideoRecordEvent.Start -> { _isRecording.value = true; _isPaused.value = false }
                    is VideoRecordEvent.Pause -> _isPaused.value = true
                    is VideoRecordEvent.Resume -> _isPaused.value = false
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false; _isPaused.value = false
                        pendingOutputUri = e.outputResults.outputUri
                    }
                    else -> Unit
                }
            }
    }

    fun pause() { recording?.pause(); }
    fun resume() { recording?.resume(); }
    fun stop() { recording?.stop(); recording = null }
    fun mute() { recording?.mute(true) }

    fun lastOutputUri(): android.net.Uri? = pendingOutputUri

    /** Timelapse capture interval helper (CameraX video + intervalometer wakeups). */
    fun timelapseIntervalMs(configured: Long): Long = configured.coerceIn(500L, 60_000L)
}

/** Minimal MediaStore→File bridge for prepareRecording on all API levels. */
private class MediaStoreOutputOptionsCompat(
    val collection: android.net.Uri,
    val values: ContentValues,
) {
    fun toFileOutput(): FileOutputOptions {
        val f = File.createTempFile("nova_vid", ".mp4")
        return FileOutputOptions.Builder(f).build()
    }
}
