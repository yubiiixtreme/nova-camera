package com.novacamera.core.common

import android.content.Context
import android.os.PowerManager
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

    private val listener = PowerManager.OnThermalStatusChangedListener { status ->
        _status.value = when (status) {
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
            else -> ThermalStatus.NORMAL
        }
    }

    fun start() {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        pm.addThermalStatusListener(listener)
        _status.value = mapStatus(pm.currentThermalStatus)
    }

    fun stop() {
        context.getSystemService(PowerManager::class.java)
            ?.removeThermalStatusListener(listener)
    }

    /** True when heavy real-time effects / high-FPS should be disabled. */
    fun shouldShedLoad(): Boolean =
        _status.value == ThermalStatus.SEVERE || _status.value == ThermalStatus.CRITICAL

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
