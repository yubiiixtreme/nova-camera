package com.novacamera.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacamera.data.repository.MediaRepository
import com.novacamera.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val media: MediaRepository,
) : ViewModel() {
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    fun refresh() = viewModelScope.launch { _items.value = media.recent(120) }
}

@HiltViewModel
class VaultViewModel @Inject constructor() : ViewModel() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked as StateFlow<Boolean>
    fun setUnlocked(v: Boolean) { _unlocked.value = v }
}
