package com.novacamera.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Hardware shutter requests (volume-key press). Collected screen-side so only
 * the visible camera screen fires — background ViewModels never see it. */
object ShutterEvents {
    private val _presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val presses: SharedFlow<Unit> = _presses.asSharedFlow()

    val isListening: Boolean get() = _presses.subscriptionCount.value > 0

    /** Returns true only if a camera screen is listening, so the key keeps its volume role elsewhere. */
    fun press(): Boolean {
        if (_presses.subscriptionCount.value == 0) return false
        return _presses.tryEmit(Unit)
    }
}
