package com.novacamera.presentation.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Real-time RGB histogram overlay (luma-sampled placeholder driven by a
 * frame-luma Flow in production; static demo curve here).
 * TalkBack: contentDescription exposes exposure state.
 */
@Composable
fun HistogramOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val bars = remember {
        // Simulated luma distribution; production samples ImageAnalysis Y plane.
        List(64) { i ->
            val x = i / 64f
            (0.15f + 0.85f * kotlin.math.exp(-((x - 0.45f) * (x - 0.45f)) / 0.08f)).coerceIn(0f, 1f)
        }
    }
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val w = size.width / bars.size
        bars.forEachIndexed { i, v ->
            drawLine(
                color = if (v > 0.95f) Color.Red else Color.Green,
                start = Offset(i * w, size.height),
                end = Offset(i * w, size.height - v * size.height),
                strokeWidth = w * 0.8f,
            )
        }
    }
}

/** Zebra-pattern overexposure overlay (diagonal stripes on blown highlights). */
@Composable
fun ZebraOverlay(enabled: Boolean, overexposed: Boolean, modifier: Modifier = Modifier) {
    if (!enabled || !overexposed) return
    Canvas(modifier = modifier) {
        val step = 24f
        var x = -size.height
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 4f)
            x += step
        }
    }
}

/** Focus-peaking overlay: green edge shimmer when manual focus is active. */
@Composable
fun FocusPeakingOverlay(enabled: Boolean, inFocus: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    Canvas(modifier = modifier) {
        drawRect(Color.Transparent)
        if (inFocus) drawCircle(Color.Green.copy(alpha = 0.25f), radius = size.minDimension / 3f, center = center)
    }
}
