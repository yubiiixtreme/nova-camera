package com.novacamera.ml

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Smart framing & auto-tracking: keeps the detected subject centered by
 * computing a crop window from the tracked bounding box. The camera pipeline
 * applies it as a digital zoom/pan offset during video recording.
 */
@Singleton
class SubjectTracker @Inject constructor() {
    data class Box(val cx: Float, val cy: Float, val w: Float, val h: Float)

    private val _box = MutableStateFlow<Box?>(null)
    val box: StateFlow<Box?> = _box.asStateFlow()

    fun onDetection(cx: Float, cy: Float, w: Float, h: Float) { _box.value = Box(cx, cy, w, h) }
    fun clear() { _box.value = null }

    /** Crop window (0..1) keeping subject centered with margin. */
    fun cropWindow(): FloatArray {
        val b = _box.value ?: return floatArrayOf(0f, 0f, 1f, 1f)
        val margin = 1.35f
        val cw = (b.w * margin).coerceIn(0.3f, 1f)
        val ch = (b.h * margin).coerceIn(0.3f, 1f)
        val x = (b.cx - cw / 2).coerceIn(0f, 1f - cw)
        val y = (b.cy - ch / 2).coerceIn(0f, 1f - ch)
        return floatArrayOf(x, y, x + cw, y + ch)
    }
}
