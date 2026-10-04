package com.novacamera.core.common

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalStatus { NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL }

/**
 * Observes PowerManager thermal status and exposes a downshift hint so the
 * camera pipeline can shed load (lower FPS, disable heavy ML filters).
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(ThermalStatus.NORMAL)
    val status: StateFlow<ThermalStatus> = _status.asStateFlow()

    // The thermal API arrived in Android 10 (API 29). Creating the listener eagerly on
    // API 24-28 would fail class loading, so it is lazy and only touched behind the check.
    @delegate:RequiresApi(Build.VERSION_CODES.Q)
    private val listener by lazy(LazyThreadSafetyMode.NONE) {
        PowerManager.OnThermalStatusChangedListener { status -> _status.value = mapStatus(status) }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        pm.addThermalStatusListener(listener)
        _status.value = mapStatus(pm.currentThermalStatus)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        context.getSystemService(PowerManager::class.java)
            ?.removeThermalStatusListener(listener)
    }

    /** True when heavy real-time effects / high-FPS should be disabled. */
    fun shouldShedLoad(): Boolean =
        _status.value == ThermalStatus.SEVERE || _status.value == ThermalStatus.CRITICAL

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mapStatus(s: Int) = when (s) {
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
        else -> ThermalStatus.NORMAL
    }
}
