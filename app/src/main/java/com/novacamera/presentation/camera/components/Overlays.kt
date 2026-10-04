package com.novacamera.presentation.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.novacamera.core.common.Tilt
import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.GridStyle
import kotlin.math.abs
import kotlin.math.max

/**
 * Real-time luma histogram from [FrameStats] (64 bins). Top bins drawn red:
 * anything stacked there is clipped.
 * TalkBack: contentDescription exposes exposure state.
 */
@Composable
fun HistogramOverlay(
    enabled: Boolean,
    hist: IntArray?,
    modifier: Modifier = Modifier,
) {
    if (!enabled || hist == null) return
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val peak = (hist.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val w = size.width / hist.size
        hist.forEachIndexed { i, c ->
            val v = c / peak
            drawLine(
                color = if (i >= 60 && c > 0) Color.Red else Color.Green,
                start = Offset(i * w, size.height),
                end = Offset(i * w, size.height - v * size.height),
                strokeWidth = max(1f, w * 0.8f),
            )
        }
    }
}

/** Zebra stripes whose strength tracks the measured clipped-highlight fraction. */
@Composable
fun ZebraOverlay(enabled: Boolean, clippedFraction: Float, modifier: Modifier = Modifier) {
    if (!enabled || clippedFraction < 0.005f) return
    val alpha = (0.15f + clippedFraction * 3f).coerceIn(0.15f, 0.7f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 24f
        var x = -size.height
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = alpha), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 4f)
            x += step
        }
    }
}

/**
 * Manual-focus aid: corner brackets plus a sharpness meter driven by the
 * measured variance-of-Laplacian focus score (0..1). Only shown for manual focus.
 */
@Composable
fun FocusPeakingOverlay(
    enabled: Boolean,
    manualFocus: Boolean,
    sharpness: Float,
    modifier: Modifier = Modifier,
) {
    if (!enabled || !manualFocus) return
    val good = sharpness > 0.45f
    val color = if (good) Color.Green else Color.Red
    Canvas(modifier = modifier.fillMaxSize()) {
        val m = size.minDimension
        val arm = m * 0.08f
        val corners = listOf(
            Offset(m * 0.3f, m * 0.3f), Offset(m * 0.7f, m * 0.3f),
            Offset(m * 0.3f, m * 0.7f), Offset(m * 0.7f, m * 0.7f),
        )
        // Centered square brackets around the focus area.
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = m * 0.2f
        val pts = listOf(
            Offset(cx - half, cy - half) to Offset(cx - half + arm, cy - half),
            Offset(cx - half, cy - half) to Offset(cx - half, cy - half + arm),
            Offset(cx + half, cy - half) to Offset(cx + half - arm, cy - half),
            Offset(cx + half, cy - half) to Offset(cx + half, cy - half + arm),
            Offset(cx - half, cy + half) to Offset(cx - half + arm, cy + half),
            Offset(cx - half, cy + half) to Offset(cx - half, cy + half - arm),
            Offset(cx + half, cy + half) to Offset(cx + half - arm, cy + half),
            Offset(cx + half, cy + half) to Offset(cx + half, cy + half - arm),
        )
        pts.forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 5f) }
        corners.forEach { drawCircle(color.copy(alpha = 0.15f), radius = 3f, center = it) }
        // Sharpness meter along the bottom.
        val bw = size.width * 0.6f
        val bx = (size.width - bw) / 2f
        val by = size.height - 28f
        drawLine(Color.White.copy(alpha = 0.4f), Offset(bx, by), Offset(bx + bw, by), strokeWidth = 8f)
        drawLine(color, Offset(bx, by), Offset(bx + bw * sharpness.coerceIn(0f, 1f), by), strokeWidth = 8f)
    }
}

/** Composition grids: thirds, golden-ratio (phi) lines, or center cross. */
@Composable
fun GridOverlay(enabled: Boolean, style: GridStyle, modifier: Modifier = Modifier) {
    if (!enabled) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.55f)
        when (style) {
            GridStyle.THIRDS -> {
                for (i in 1..2) {
                    val fx = size.width * i / 3f
                    val fy = size.height * i / 3f
                    drawLine(line, Offset(fx, 0f), Offset(fx, size.height), strokeWidth = 2f)
                    drawLine(line, Offset(0f, fy), Offset(size.width, fy), strokeWidth = 2f)
                }
            }
            GridStyle.GOLDEN -> {
                // phi ≈ 0.618 splits both axes.
                val phi = 0.618f
                listOf(phi, 1f - phi).forEach { f ->
                    drawLine(line, Offset(size.width * f, 0f), Offset(size.width * f, size.height), strokeWidth = 2f)
                    drawLine(line, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 2f)
                }
                drawCircle(line, radius = 6f, center = center)
            }
            GridStyle.CENTER -> {
                drawLine(line, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2f)
                drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
                drawCircle(line, radius = 40f, center = center, style = Stroke(width = 2f))
            }
        }
    }
}

/** Letterbox mask for the selected aspect ratio (capture stays full-frame). */
@Composable
fun AspectMaskOverlay(mask: AspectMask, modifier: Modifier = Modifier) {
    if (mask == AspectMask.FULL || mask.ratio <= 0f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val r = aspectRect(size.width, size.height, mask.ratio)
        val dim = Color.Black.copy(alpha = 0.55f)
        // Top / bottom / left / right bars outside the frame.
        drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, r.top))
        drawRect(dim, topLeft = Offset(0f, r.bottom), size = Size(size.width, size.height - r.bottom))
        drawRect(dim, topLeft = Offset(0f, r.top), size = Size(r.left, r.height))
        drawRect(
            dim,
            topLeft = Offset(r.right, r.top),
            size = Size(size.width - r.right, r.height),
        )
        drawRect(Color.White.copy(alpha = 0.8f), topLeft = r.topLeft, size = r.size, style = Stroke(width = 2f))
    }
}

/** Centered rect of [ratio] (w/h) inside a WxH view. Pure — unit-tested. */
fun aspectRect(viewW: Float, viewH: Float, ratio: Float): Rect {
    val viewRatio = viewW / viewH
    return if (viewRatio > ratio) {
        // View wider than target: pillarbox.
        val w = viewH * ratio
        Rect((viewW - w) / 2f, 0f, (viewW + w) / 2f, viewH)
    } else {
        // View taller: letterbox.
        val h = viewW / ratio
        Rect(0f, (viewH - h) / 2f, viewW, (viewH + h) / 2f)
    }
}

/** Artificial horizon: line tilts with roll, shifts with pitch; green when level. */
@Composable
fun LevelOverlay(enabled: Boolean, tilt: Tilt, modifier: Modifier = Modifier) {
    if (!enabled) return
    val level = tilt.isLevel
    val color = if (level) Color.Green else Color.Yellow
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.width * 0.3f
        // Roll rotates the horizon; pitch shifts it vertically (clamped).
        val rad = Math.toRadians(tilt.rollDeg.toDouble())
        val dy = (tilt.pitchDeg * 4f).coerceIn(-120f, 120f)
        val dx = kotlin.math.cos(rad).toFloat() * half
        val dyy = kotlin.math.sin(rad).toFloat() * half
        drawLine(color, Offset(cx - dx, cy + dy - dyy), Offset(cx + dx, cy + dy + dyy), strokeWidth = 4f)
        drawCircle(if (level) Color.Green else Color.White.copy(alpha = 0.6f), radius = 8f, center = Offset(cx, cy + dy))
        if (abs(tilt.rollDeg) > 0.5f) {
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 5f, center = Offset(cx, cy))
        }
    }
}
