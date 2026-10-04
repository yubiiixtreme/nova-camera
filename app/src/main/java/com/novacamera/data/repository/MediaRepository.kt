package com.novacamera.data.repository

import android.net.Uri
import com.novacamera.data.datasource.DeleteOutcome
import com.novacamera.data.datasource.MediaStoreDataSource
import com.novacamera.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface MediaRepository {
    /** Shared, cached list of everything captured by the app (newest first). */
    val items: StateFlow<List<MediaItem>>
    val newMediaEvents: SharedFlow<Uri>
    suspend fun refresh()
    suspend fun recent(limit: Int = 60): List<MediaItem>
    suspend fun notifyNewMedia(uri: Uri, isVideo: Boolean)
    suspend fun delete(uri: Uri): DeleteOutcome
}

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val store: MediaStoreDataSource,
) : MediaRepository {
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    override val items: StateFlow<List<MediaItem>> = _items.asStateFlow()
    private val _events = MutableSharedFlow<Uri>(extraBufferCapacity = 16)
    override val newMediaEvents = _events.asSharedFlow()

    override suspend fun refresh() { _items.value = store.recent(200) }
    override suspend fun recent(limit: Int) = store.recent(limit)

    override suspend fun notifyNewMedia(uri: Uri, isVideo: Boolean) {
        _events.emit(uri)
        refresh()
    }

    override suspend fun delete(uri: Uri): DeleteOutcome =
        store.delete(uri).also { if (it == DeleteOutcome.Done) refresh() }
}
