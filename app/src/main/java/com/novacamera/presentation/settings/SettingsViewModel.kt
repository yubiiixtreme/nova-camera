package com.novacamera.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacamera.data.datasource.Preset
import com.novacamera.data.datasource.PresetStore
import com.novacamera.data.datasource.SettingsDataStore
import com.novacamera.domain.model.CameraSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings talks to the shared settings store directly — it must not spin up a second camera ViewModel. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsDataStore,
    private val presetStore: PresetStore,
) : ViewModel() {
    val settings: StateFlow<CameraSettings> = prefs.settings
    val presets: StateFlow<List<Preset>> =
        presetStore.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(t: (CameraSettings) -> CameraSettings) = prefs.update(t)

    fun savePreset(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { presetStore.save(name.trim(), prefs.settings.value) }
    }

    fun applyPreset(name: String) {
        val p = presets.value.firstOrNull { it.name == name } ?: return
        prefs.update { p.settings.copy(zoomRatio = 1f) }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch { presetStore.delete(name) }
    }
}
