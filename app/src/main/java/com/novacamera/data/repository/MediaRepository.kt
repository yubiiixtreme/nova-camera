package com.novacamera.data.repository

import android.net.Uri
import com.novacamera.data.datasource.MediaStoreDataSource
import com.novacamera.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface MediaRepository {
    suspend fun recent(limit: Int = 60): List<MediaItem>
    suspend fun notifyNewMedia(uri: Uri, isVideo: Boolean)
    val newMediaEvents: SharedFlow<Uri>
}

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val store: MediaStoreDataSource,
) : MediaRepository {
    private val _events = MutableSharedFlow<Uri>(extraBufferCapacity = 16)
    override val newMediaEvents = _events.asSharedFlow()
    override suspend fun recent(limit: Int) = store.recent(limit)
    override suspend fun notifyNewMedia(uri: Uri, isVideo: Boolean) { _events.emit(uri) }
}
