package com.novacamera.presentation.camera

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacamera.data.datasource.DeleteOutcome
import com.novacamera.data.datasource.SettingsDataStore
import com.novacamera.security.ExifStripper
import com.novacamera.data.repository.MediaRepository
import com.novacamera.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val media: MediaRepository,
    private val prefs: SettingsDataStore,
    private val stripper: ExifStripper,
) : ViewModel() {
    /** Shared with the camera thumbnail, so deletes and new shots show up everywhere. */
    val items: StateFlow<List<MediaItem>> = media.items

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Emits when a delete needs the user's consent via a system dialog. */
    private val _consent = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val consent: SharedFlow<IntentSender> = _consent.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            media.refresh()
            _loading.value = false
        }
    }

    /**
     * Resolves what to hand to the share sheet. With "strip metadata on export" on
     * (the default), photos are shared as a copy with location/device/time tags removed.
     */
    fun share(item: MediaItem, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = if (!item.isVideo && prefs.settings.value.stripExifOnExport) {
                runCatching { stripper.strippedCopy(item.uri) }.getOrDefault(item.uri)
            } else {
                item.uri
            }
            onReady(uri)
        }
    }

    fun delete(uri: Uri) {
        viewModelScope.launch {
            when (val r = media.delete(uri)) {
                DeleteOutcome.Done -> _message.tryEmit("Deleted")
                is DeleteOutcome.NeedsConsent -> _consent.tryEmit(r.sender)
                DeleteOutcome.Failed -> _message.tryEmit("Couldn't delete that file")
            }
        }
    }

    /** Called after the system consent dialog finishes. */
    fun onConsentResult(deleted: Boolean) {
        viewModelScope.launch {
            media.refresh()
            if (deleted) _message.tryEmit("Deleted")
        }
    }
}

@HiltViewModel
class VaultViewModel @Inject constructor() : ViewModel() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked as StateFlow<Boolean>
    fun setUnlocked(v: Boolean) { _unlocked.value = v }
}
