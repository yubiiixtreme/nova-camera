package com.novacamera.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacamera.core.camera.CameraCaps
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

/** Log-scale slider: equal travel per stop. 0 = AUTO. */
internal fun sliderToShutter(t: Float, min: Double = SHUTTER_MIN, max: Double = SHUTTER_MAX): Double? {
    if (t <= 0.01f) return null
    return min * Math.pow(max / min, t.toDouble())
}

internal fun shutterToSlider(sec: Double?, min: Double = SHUTTER_MIN, max: Double = SHUTTER_MAX): Float {
    if (sec == null || sec <= 0) return 0f
    return (ln(sec / min) / ln(max / min)).toFloat().coerceIn(0f, 1f)
}

private enum class ProTab(val label: String) { EXPOSURE("Exposure"), FOCUS_WB("Focus & WB"), ASSIST("Assist"), CAPTURE("Capture") }

/**
 * DSLR-style pro panel in four tabs. Controls the current camera can't do
 * (e.g. manual ISO on a LEGACY-level device) say so instead of silently doing nothing.
 */
@Composable
fun ProControlPanel(
    settings: CameraSettings,
    caps: CameraCaps,
    afLocked: Boolean,
    onIntent: (CameraIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    val p = settings.proControls
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProTab.entries.forEachIndexed { i, t ->
                val sel = i == tab
                Text(
                    t.label,
                    color = if (sel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { tab = i }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (ProTab.entries[tab]) {
                ProTab.EXPOSURE -> {
                    if (caps.hasEv) {
                        ProRow("Exposure", "%+.1f EV".format(p.exposureCompensationEv), canReset = p.exposureCompensationEv != 0f, resetLabel = "0") {
                            onIntent(CameraIntent.SetEv(0f))
                        }
                        Slider(
                            value = p.exposureCompensationEv.coerceIn(caps.evMin, caps.evMax),
                            onValueChange = { onIntent(CameraIntent.SetEv(it)) },
                            valueRange = caps.evMin..caps.evMax,
                        )
                    }
                    if (caps.manualSensor) {
                        ProRow("ISO", p.iso?.toString() ?: "AUTO", p.iso != null) { onIntent(CameraIntent.SetIso(null)) }
                        Slider(
                            value = (p.iso ?: caps.isoMin).toFloat().coerceIn(caps.isoMin.toFloat(), caps.isoMax.toFloat()),
                            onValueChange = { onIntent(CameraIntent.SetIso(it.toInt())) },
                            valueRange = caps.isoMin.toFloat()..caps.isoMax.toFloat(),
                        )
                        ProRow("Shutter", formatShutter(p.shutterSpeedSec), p.shutterSpeedSec != null) { onIntent(CameraIntent.SetShutter(null)) }
                        Slider(
                            value = shutterToSlider(p.shutterSpeedSec, caps.shutterMinSec, caps.shutterMaxSec),
                            onValueChange = { onIntent(CameraIntent.SetShutter(sliderToShutter(it, caps.shutterMinSec, caps.shutterMaxSec))) },
                            valueRange = 0f..1f,
                        )
                    } else {
                        Hint("Manual ISO and shutter aren't exposed by this camera.")
                    }
                }
                ProTab.FOCUS_WB -> {
                    if (caps.manualFocus) {
                        ProRow(
                            "Focus",
                            p.manualFocusDistance?.let { if (it < 0.02f) "∞" else "%.0f%%".format(it * 100) } ?: "AF",
                            p.manualFocusDistance != null,
                            resetLabel = "AF",
                        ) { onIntent(CameraIntent.SetFocus(null)) }
                        Slider(
                            value = p.manualFocusDistance ?: 0f,
                            onValueChange = { onIntent(CameraIntent.SetFocus(it)) },
                            valueRange = 0f..1f,
                        )
                    } else {
                        Hint("Manual focus isn't available on this camera.")
                    }
                    if (caps.manualWhiteBalance) {
                        ProRow("White balance", p.whiteBalanceKelvin?.let { "${it}K" } ?: "AUTO", p.whiteBalanceKelvin != null) {
                            onIntent(CameraIntent.SetWb(null))
                        }
                        Slider(
                            value = (p.whiteBalanceKelvin ?: 5500).toFloat(),
                            onValueChange = { onIntent(CameraIntent.SetWb(it.toInt())) },
                            valueRange = 2500f..7500f,
                        )
                    } else {
                        Hint("Manual white balance isn't available on this camera.")
                    }
                    FilterChip(
                        selected = afLocked,
                        onClick = { onIntent(CameraIntent.ToggleAfAeLock) },
                        label = { Text(if (afLocked) "AF/AE locked — tap to release" else "Lock AF/AE (or long-press the viewfinder)") },
                    )
                }
                ProTab.ASSIST -> {
                    Label("Overlays")
                    ChipRow {
                        FilterChip(p.histogramEnabled, { onIntent(CameraIntent.ToggleOverlay(OverlayKind.HISTOGRAM)) }, { Text("Histogram") })
                        FilterChip(p.zebraEnabled, { onIntent(CameraIntent.ToggleOverlay(OverlayKind.ZEBRA)) }, { Text("Zebra") })
                        FilterChip(p.focusPeakingEnabled, { onIntent(CameraIntent.ToggleOverlay(OverlayKind.PEAKING)) }, { Text("Peaking") })
                    }
                    ChipRow {
                        FilterChip(settings.gridEnabled, { onIntent(CameraIntent.ToggleGrid) }, { Text("Grid") })
                        FilterChip(settings.levelEnabled, { onIntent(CameraIntent.SetLevel(!settings.levelEnabled)) }, { Text("Level") })
                    }
                    Label("Grid style")
                    ChipRow {
                        GridStyle.entries.forEach { g ->
                            FilterChip(settings.gridStyle == g, { onIntent(CameraIntent.SetGridStyle(g)) }, { Text(g.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Label("Framing mask")
                    ChipRow {
                        AspectMask.entries.forEach { a ->
                            FilterChip(settings.aspectMask == a, { onIntent(CameraIntent.SetAspectMask(a)) }, { Text(if (a == AspectMask.FULL) "Full" else a.name.drop(1).let { n -> if (n.length == 2) "${n[0]}:${n[1]}" else n }) })
                        }
                    }
                }
                ProTab.CAPTURE -> {
                    Label("HDR bracket: ${settings.hdrFrames} frames × ±${settings.hdrStepEv} EV")
                    ChipRow {
                        listOf(3, 5, 7).forEach { f -> FilterChip(settings.hdrFrames == f, { onIntent(CameraIntent.SetHdrFrames(f)) }, { Text("$f") }) }
                        listOf(1, 2, 3).forEach { s -> FilterChip(settings.hdrStepEv == s, { onIntent(CameraIntent.SetHdrStep(s)) }, { Text("±$s") }) }
                    }
                    Label("Burst: ${settings.burstShots} shots")
                    ChipRow {
                        listOf(5, 10, 20).forEach { b -> FilterChip(settings.burstShots == b, { onIntent(CameraIntent.SetBurstShots(b)) }, { Text("$b") }) }
                    }
                    Label("Timelapse: ${settings.timelapseShots} frames, one every ${settings.timelapseIntervalMs / 1000.0}s")
                    ChipRow {
                        listOf(6, 12, 30).forEach { t -> FilterChip(settings.timelapseShots == t, { onIntent(CameraIntent.SetTimelapseShots(t)) }, { Text("$t") }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProRow(label: String, value: String, canReset: Boolean, resetLabel: String = "AUTO", onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (canReset) {
            Box(
                Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onReset).padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(resetLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Hint(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).horizontalScrollCompat(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
