package com.novacamera.data.datasource

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.novacamera.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped-storage compliant MediaStore gateway (read + async write signaling). */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun recent(limit: Int = 60): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<MediaItem>()
        // WIDTH/HEIGHT are not guaranteed pre-29; resolve indices by name.
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_TAKEN,
            )
        }
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        runCatching {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val args = android.os.Bundle().apply {
                    putString(
                        android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort,
                    )
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                }
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, args, null,
                )
            } else {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, sort,
                )
            }
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(projection[0])
                val nameCol = c.getColumnIndexOrThrow(projection[1])
                val mimeCol = c.getColumnIndexOrThrow(projection[2])
                val dateCol = c.getColumnIndexOrThrow(projection[3])
                val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id,
                    )
                    out += MediaItem(
                        uri = uri,
                        name = c.getString(nameCol) ?: "IMG_$id",
                        mimeType = c.getString(mimeCol) ?: "image/jpeg",
                        takenAt = runCatching { c.getLong(dateCol) }.getOrDefault(0L),
                        width = if (widthCol != -1) runCatching { c.getInt(widthCol) }.getOrDefault(0) else 0,
                        height = if (heightCol != -1) runCatching { c.getInt(heightCol) }.getOrDefault(0) else 0,
                    )
                }
            }
        }
        out
    }

    fun collectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
}
