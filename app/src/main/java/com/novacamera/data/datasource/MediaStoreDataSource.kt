package com.novacamera.data.datasource

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.novacamera.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a delete: done, needs the user's consent (system dialog), or failed. */
sealed interface DeleteOutcome {
    data object Done : DeleteOutcome
    data class NeedsConsent(val sender: IntentSender) : DeleteOutcome
    data object Failed : DeleteOutcome
}

/** Scoped-storage compliant MediaStore gateway for everything NovaCamera has captured. */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Newest photos and videos saved under .../NovaCamera, newest first. */
    suspend fun recent(limit: Int = 60): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = query(isVideo = false, limit)
        val videos = query(isVideo = true, limit)
        (images + videos).sortedByDescending { it.takenAt }.take(limit)
    }

    private fun query(isVideo: Boolean, limit: Int): List<MediaItem> {
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%NovaCamera%")
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/NovaCamera/%")
        }
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val out = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(base, projection, selection, args, sort)?.use { cur ->
                val id = cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val name = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mime = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val added = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val w = cur.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val h = cur.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val dur = if (isVideo) cur.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (cur.moveToNext() && out.size < limit) {
                    val rowId = cur.getLong(id)
                    out += MediaItem(
                        uri = ContentUris.withAppendedId(base, rowId),
                        name = cur.getString(name) ?: "NOVA_$rowId",
                        mimeType = cur.getString(mime) ?: if (isVideo) "video/mp4" else "image/jpeg",
                        takenAt = cur.getLong(added) * 1000L,
                        width = if (w != -1) cur.getInt(w) else 0,
                        height = if (h != -1) cur.getInt(h) else 0,
                        durationMs = if (dur != -1) cur.getLong(dur) else null,
                        isVideo = isVideo,
                    )
                }
            }
        }
        return out
    }

    suspend fun delete(uri: Uri): DeleteOutcome = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        try {
            if (resolver.delete(uri, null, null) > 0) DeleteOutcome.Done else DeleteOutcome.Failed
        } catch (e: SecurityException) {
            // Not our file any more (e.g. after reinstall): ask the user via the system dialog.
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    DeleteOutcome.NeedsConsent(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    DeleteOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
                else -> DeleteOutcome.Failed
            }
        }
    }

    fun collectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
}
