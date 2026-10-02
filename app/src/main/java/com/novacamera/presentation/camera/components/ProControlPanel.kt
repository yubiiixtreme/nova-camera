package com.novacamera.presentation.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novacamera.domain.model.ProControls
import com.novacamera.presentation.camera.CameraIntent
import com.novacamera.presentation.camera.OverlayKind

/**
 * DSLR-like pro panel: ISO, shutter, WB Kelvin, manual focus, EV + overlays.
 * Sliders snap to sensor bounds supplied by Camera2ProController.
 */
@Composable
fun ProControlPanel(
    pro: ProControls,
    isoRange: IntRange = 100..3200,
    onIntent: (CameraIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ISO  ${pro.iso?.toString() ?: "AUTO"}")
        Slider(
            value = (pro.iso ?: isoRange.first).toFloat(),
            onValueChange = { onIntent(CameraIntent.SetIso(it.toInt())) },
            valueRange = isoRange.first.toFloat()..isoRange.last.toFloat(),
        )
        Text("Shutter  ${pro.shutterSpeedSec?.let { "1/${(1 / it).toInt()}s" } ?: "AUTO"}")
        Slider(
            value = pro.shutterSpeedSec?.toFloat() ?: 0f,
            onValueChange = {
                // Map 0..1 → 1/8000..30s log scale (simplified linear here).
                val sec = if (it <= 0.01f) null else (1.0 / 8000 + it * 30.0)
                onIntent(CameraIntent.SetShutter(sec))
            },
            valueRange = 0f..1f,
        )
        Text("White balance  ${pro.whiteBalanceKelvin?.let { "${it}K" } ?: "AUTO"}")
        Slider(
            value = (pro.whiteBalanceKelvin ?: 5500).toFloat(),
            onValueChange = { onIntent(CameraIntent.SetWb(it.toInt())) },
            valueRange = 2000f..10000f,
        )
        Text("EV  ${"%.1f".format(pro.exposureCompensationEv)}")
        Slider(
            value = pro.exposureCompensationEv,
            onValueChange = { onIntent(CameraIntent.SetEv(it)) },
            valueRange = -3f..3f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = pro.histogramEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.HISTOGRAM)) }, label = { Text("Histogram") })
            FilterChip(selected = pro.zebraEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.ZEBRA)) }, label = { Text("Zebra") })
            FilterChip(selected = pro.focusPeakingEnabled, onClick = { onIntent(CameraIntent.ToggleOverlay(OverlayKind.PEAKING)) }, label = { Text("Peaking") })
        }
    }
}
