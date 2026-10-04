package com.novacamera.presentation.camera.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacamera.presentation.theme.NovaRed
import kotlin.math.roundToInt

/**
 * Tap-to-focus reticle that settles in, then fades. [stamp] changes on every
 * tap so repeated taps restart the animation. Includes a drag handle for
 * quick exposure compensation (sun icon, drag up = brighter).
 */
@Composable
fun FocusReticle(
    position: Offset,
    stamp: Long,
    ev: Float,
    evMin: Float,
    evMax: Float,
    onEvChange: (Float) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember(stamp) { Animatable(1.35f) }
    val alpha = remember(stamp) { Animatable(1f) }
    LaunchedEffect(stamp) {
        scale.animateTo(1f, tween(180))
    }
    val color = MaterialTheme.colorScheme.primary
    val ring = 72.dp
    val density = LocalDensity.current
    val half = with(density) { (ring / 2).roundToPx() }

    Box(
        modifier = modifier.offset { IntOffset(position.x.roundToInt() - half, position.y.roundToInt() - half) }
            .size(ring),
    ) {
        Canvas(Modifier.size(ring)) {
            val s = scale.value
            drawCircle(color.copy(alpha = alpha.value), radius = size.minDimension / 2 * s, style = Stroke(width = 2.5f * density.density))
            drawCircle(color.copy(alpha = alpha.value), radius = 3f * density.density, center = center)
        }
        if (evMax > evMin) {
            val pxPerEv = with(density) { 70.dp.toPx() }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 34.dp)
                    .size(36.dp, 56.dp)
                    .pointerInput(evMin, evMax) {
                        detectVerticalDragGestures(
                            onDragStart = { onInteract() },
                        ) { change, drag ->
                            change.consume()
                            onInteract()
                            onEvChange((ev - drag / pxPerEv).coerceIn(evMin, evMax))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = "Drag to adjust exposure", tint = color, modifier = Modifier.size(22.dp))
                Text(
                    "%+.1f".format(ev),
                    color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Red dot + elapsed time while recording (dot dims while paused). */
@Composable
fun RecordingPill(durationMs: Long, paused: Boolean, modifier: Modifier = Modifier) {
    val total = durationMs / 1000
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) Color.Gray else NovaRed))
        Spacer(Modifier.width(8.dp))
        Text("%02d:%02d".format(total / 60, total % 60), color = Color.White, fontWeight = FontWeight.Medium)
    }
}

/** Big self-timer number in the middle of the viewfinder. */
@Composable
fun CountdownOverlay(seconds: Int?, modifier: Modifier = Modifier) {
    if (seconds == null) return
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            seconds.toString(),
            color = Color.White,
            fontSize = 120.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

/** Small status pill with spinner, e.g. while an HDR or night stack is merging. */
@Composable
fun BusyPill(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
