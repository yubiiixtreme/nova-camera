package com.novacamera.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Spatial audio / external-mic (BT + USB-C) routing + directional NR hint. */
@Singleton
class AudioController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class MicOption(val id: Int, val name: String, val type: Int)

    private val _mics = MutableStateFlow<List<MicOption>>(emptyList())
    val mics: StateFlow<List<MicOption>> = _mics.asStateFlow()
    private val _audioZoom = MutableStateFlow(false)
    val audioZoom: StateFlow<Boolean> = _audioZoom.asStateFlow()

    fun refreshDevices() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        _mics.value = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map {
            MicOption(it.id, it.productName?.toString() ?: typeName(it.type), it.type)
        }
    }

    fun setAudioZoom(enabled: Boolean) { _audioZoom.value = enabled }

    fun hasExternalMic(): Boolean = _mics.value.any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            (Build.VERSION.SDK_INT >= 28 && it.type == AudioDeviceInfo.TYPE_USB_HEADSET)
    }

    private fun typeName(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mic"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        else -> "Mic ($t)"
    }
}
