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
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT $limit"
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sort,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(projection[0])
                val nameCol = c.getColumnIndexOrThrow(projection[1])
                val mimeCol = c.getColumnIndexOrThrow(projection[2])
                val dateCol = c.getColumnIndexOrThrow(projection[3])
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id,
                    )
                    out += MediaItem(
                        uri = uri,
                        name = c.getString(nameCol) ?: "IMG_$id",
                        mimeType = c.getString(mimeCol) ?: "image/jpeg",
                        takenAt = c.getLong(dateCol),
                        width = c.getInt(4), height = c.getInt(5),
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
