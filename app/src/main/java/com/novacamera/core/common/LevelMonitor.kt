package com.novacamera.core.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Pitch/roll in degrees from the accelerometer (low-passed). Plain class: no DI needed. */
data class Tilt(val pitchDeg: Float, val rollDeg: Float) {
    val isLevel: Boolean get() = abs(pitchDeg) < 1.5f && abs(rollDeg) < 1.5f
}

class LevelMonitor {
    private val _tilt = MutableStateFlow(Tilt(0f, 0f))
    val tilt: StateFlow<Tilt> = _tilt.asStateFlow()
    private var mgr: SensorManager? = null
    private var gravity = floatArrayOf(0f, 0f, 9.81f)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values
            val alpha = 0.85f
            gravity = floatArrayOf(
                alpha * gravity[0] + (1 - alpha) * v[0],
                alpha * gravity[1] + (1 - alpha) * v[1],
                alpha * gravity[2] + (1 - alpha) * v[2],
            )
            val g = gravity
            val norm = kotlin.math.sqrt((g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).toDouble()).toFloat()
            if (norm < 0.1f) return
            // Portrait-held phone: roll from X tilt, pitch from Y tilt.
            val roll = Math.toDegrees(kotlin.math.asin((g[0] / norm).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            val pitch = Math.toDegrees(kotlin.math.asin((g[1] / norm).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            _tilt.value = Tilt(pitch, roll)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(context: Context) {
        if (mgr != null) return
        val m = context.getSystemService(SensorManager::class.java) ?: return
        mgr = m
        m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            m.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        mgr?.unregisterListener(listener)
        mgr = null
    }
}
