package com.novacamera.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.novacamera.core.camera.RecordingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of a finished recording, delivered once per clip. */
sealed interface VideoResult {
    data class Saved(val uri: Uri) : VideoResult
    data class Failed(val message: String) : VideoResult
}

/**
 * Video pipeline. Writes straight into MediaStore (Movies/NovaCamera) — the
 * previous implementation recorded into a throw-away cache temp file, so
 * clips never reached the gallery. Pause/resume uses Recorder pause() so the
 * output is ONE file.
 */
@Singleton
class VideoRecorderController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recording: Recording? = null

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<VideoResult>(extraBufferCapacity = 4)
    val results: SharedFlow<VideoResult> = _results.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun start(videoCapture: VideoCapture<Recorder>): Result<Unit> {
        if (recording != null) return Result.failure(IllegalStateException("Already recording"))
        val name = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NovaCamera")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, collection)
            .setContentValues(values)
            .build()
        val withAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        return runCatching {
            recording = videoCapture.output
                .prepareRecording(context, output)
                .apply { if (withAudio) withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(context)) { e -> onEvent(e) }
            _state.value = RecordingState(active = true)
        }
    }

    private fun onEvent(e: VideoRecordEvent) {
        when (e) {
            is VideoRecordEvent.Start -> _state.value = RecordingState(active = true)
            is VideoRecordEvent.Status -> _state.value = _state.value.copy(
                active = true,
                durationMs = e.recordingStats.recordedDurationNanos / 1_000_000,
            )
            is VideoRecordEvent.Pause -> _state.value = _state.value.copy(paused = true)
            is VideoRecordEvent.Resume -> _state.value = _state.value.copy(paused = false)
            is VideoRecordEvent.Finalize -> {
                recording = null
                _state.value = RecordingState()
                if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_NONE &&
                    e.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED &&
                    e.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                ) {
                    _results.tryEmit(VideoResult.Failed("Recording failed (code ${e.error})"))
                } else {
                    _results.tryEmit(VideoResult.Saved(e.outputResults.outputUri))
                }
            }
            else -> Unit
        }
    }

    fun pause() { recording?.pause() }
    fun resume() { recording?.resume() }
    fun stop() { recording?.stop() }

    /** Called when the bound VideoCapture goes away mid-recording (rebind/unbind). */
    fun abort() {
        recording?.stop()
        recording = null
        _state.value = RecordingState()
    }

    /** Timelapse capture interval helper (clamped to a sane range). */
    fun timelapseIntervalMs(configured: Long): Long = configured.coerceIn(500L, 60_000L)
}
