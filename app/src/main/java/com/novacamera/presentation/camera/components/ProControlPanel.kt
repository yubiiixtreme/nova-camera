package com.novacamera.presentation.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.GridStyle
import com.novacamera.presentation.camera.CameraIntent
import com.novacamera.presentation.camera.OverlayKind
import kotlin.math.ln

internal const val SHUTTER_MIN = 1.0 / 8000
internal const val SHUTTER_MAX = 30.0

internal fun formatShutter(sec: Double?): String {
    if (sec == null || sec <= 0) return "AUTO"
    if (sec >= 1.0) return "${"%.1f".format(sec)}s"
    return "1/${(1.0 / sec).toInt()}s"
}

/** Log-scale slider: equal travel per stop, from 1/8000s to 30s. 0 = AUTO. */
internal fun sliderToShutter(t: Float): Double? {
    if (t <= 0.01f) return null
    return SHUTTER_MIN * Math.pow(SHUTTER_MAX / SHUTTER_MIN, t.toDouble())
}

internal fun shutterToSlider(sec: Double?): Float {
    if (sec == null || sec <= 0) return 0f
    return (ln(sec / SHUTTER_MIN) / ln(SHUTTER_MAX / SHUTTER_MIN)).toFloat().coerceIn(0f, 1f)
}

/**
 * DSLR-like pro panel: ISO, shutter, WB Kelvin, manual focus, EV, AEB,
 * burst, timelapse, composition, and overlays. Every row offers AUTO reset.
 */
@Composable
fun ProControlPanel(
    pro: CameraSettings,
    settings: CameraSettings,
    afLocked: Boolean,
    isoRange: IntRange = 100..3200,
    onIntent: (CameraIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // NOTE: `pro` is the full settings (kept name for call-site compat).
    val p = pro.proControls
    Column(
        modifier = modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ISO  ${p.iso?.toString() ?: "AUTO"}", modifier = Modifier.weight(1f))
            if (p.iso != null) AssistChip(onClick = { onIntent(CameraIntent.SetIso(null)) }, label = { Text("AUTO") })
        }
        Slider(
            value = (p.iso ?: isoRange.first).toFloat(),
            onValueChange = { onIntent(CameraIntent.SetIso(it.toInt())) },
            valueRange = isoRange.first.toFloat()..isoRange.last.toFloat(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shutter  ${formatShutter(p.shutterSpeedSec)}", modifier = Modifier.weight(1f))
            if (p.shutterSpeedSec != null) AssistChip(onClick = { onIntent(CameraIntent.SetShutter(null)) }, label = { Text("AUTO") })
        }
        Slider(
            value = shutterToSlider(p.shutterSpeedSec),
            onValueChange = { onIntent(CameraIntent.SetShutter(sliderToShutter(it))) },
            valueRange = 0f..1f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("White balance  ${p.whiteBalanceKelvin?.let { "${it}K" } ?: "AUTO"}", modifier = Modifier.weight(1f))
            if (p.whiteBalanceKelvin != null) AssistChip(onClick = { onIntent(CameraIntent.SetWb(null)) }, label = { Text("AUTO") })
        }
        Slider(
            value = (p.whiteBalanceKelvin ?: 5500).toFloat(),
            onValueChange = { onIntent(CameraIntent.SetWb(it.toInt())) },
            valueRange = 2000f..10000f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Focus  ${p.manualFocusDistance?.let { "%.2f".format(it) } ?: "AF"}",
                modifier = Modifier.weight(1f),
            )
            if (p.manualFocusDistance != null) AssistChip(onClick = { onIntent(CameraIntent.SetFocus(null)) }, label = { Text("AF") })
        }
        Slider(
            value = p.manualFocusDistance ?: 0f,
            onValueChange = { onIntent(CameraIntent.SetFocus(it)) },
            valueRange = 0f..1f,
        )
        Text("EV  ${"%.1f".format(p.exposureCompensationEv)}")
        Slider(
            value = p.exposureCompensationEv,
            onValueChange = { onIntent(CameraIntent.SetEv(it)) },
            valueRange = -3f..3f,
        )
        FilterChip(
            selected = afLocked,
            onClick = { onIntent(CameraIntent.ToggleAfAeLock) },
            label = { Text(if (afLocked) "AF/AE locked — tap to release" else "Lock AF/AE (or long-press preview)") },
        )
        Text("AEB  ${settings.hdrFrames} frames × ±${settings.hdrStepEv} EV")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 5, 7).forEach { f ->
                FilterChip(selected = settings.hdrFrames == f, onClick = { onIntent(CameraIntent.SetHdrFrames(f)) }, label = { Text("$f") })
            }
            listOf(1, 2, 3).forEach { s ->
                FilterChip(selected = settings.hdrStepEv == s, onClick = { onIntent(CameraIntent.SetHdrStep(s)) }, label = { Text("±$s") })
            }
        }
        Text("Burst  ${settings.burstShots} shots · Timelapse  ${settings.timelapseShots} × ${settings.timelapseIntervalMs}ms")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20).forEach { b ->
                FilterChip(selected = settings.burstShots == b, onClick = { onIntent(CameraIntent.SetBurstShots(b)) }, label = { Text("$b") })
            }
            listOf(6, 12, 30).forEach { t ->
                FilterChip(selected = settings.timelapseShots == t, onClick = { onIntent(CameraIntent.SetTimelapseShots(t)) }, label = { Text("TL$t") })
            }
        }
        Text("Grid  ${settings.gridStyle.name}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GridStyle.entries.forEach { g ->
                FilterChip(selected = settings.gridStyle == g, onClick = { onIntent(CameraIntent.SetGridStyle(g)) }, label = { Text(g.name) })
            }
        }
        Text("Aspect mask  ${settings.aspectMask.name}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectMask.entries.forEach { a ->
                FilterChip(selected = settings.aspectMask == a, onClick = { onIntent(CameraIntent.SetAspectMask(a)) }, label = { Text(a.name) })
            }
        }
        FilterChip(
            selected = settings.levelEnabled,
            onClick = { onIntent(CameraIntent.SetLevel(!settings.levelEnabled)) },
            label = { Text("Horizon level") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = p.histogramEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.HISTOGRAM)) }, label = { Text("Histogram") })
            FilterChip(selected = p.zebraEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.ZEBRA)) }, label = { Text("Zebra") })
            FilterChip(selected = p.focusPeakingEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.PEAKING)) }, label = { Text("Peaking") })
        }
    }
}
