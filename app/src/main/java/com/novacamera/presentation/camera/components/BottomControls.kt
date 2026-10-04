package com.novacamera.presentation.camera.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.LiveFilter
import com.novacamera.processing.LiveFilters
import kotlin.math.abs

/** The modes shown on the rail, in order. [SCAN] opens the document scanner instead of changing mode. */
sealed interface RailItem {
    val label: String
    data class Mode(val mode: CaptureMode, override val label: String) : RailItem
    data object Scan : RailItem { override val label = "Scan" }
}

val RAIL_ITEMS: List<RailItem> = listOf(
    RailItem.Mode(CaptureMode.PHOTO, "Photo"),
    RailItem.Mode(CaptureMode.VIDEO, "Video"),
    RailItem.Mode(CaptureMode.NIGHT, "Night"),
    RailItem.Mode(CaptureMode.HDR, "HDR"),
    RailItem.Mode(CaptureMode.BURST, "Burst"),
    RailItem.Mode(CaptureMode.TIMELAPSE, "Timelapse"),
    RailItem.Scan,
)

/** Horizontally scrolling mode selector; the active mode is centred and highlighted. */
@Composable
fun ModeSelector(
    selected: CaptureMode,
    enabled: Boolean,
    onSelectMode: (CaptureMode) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val centers = remember { mutableStateMapOf<String, Float>() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        LaunchedEffect(selected, centers.size, viewportPx) {
            val key = RAIL_ITEMS.firstOrNull { it is RailItem.Mode && it.mode == selected }?.label ?: return@LaunchedEffect
            val c = centers[key] ?: return@LaunchedEffect
            scroll.animateScrollTo((c - viewportPx / 2f).toInt().coerceAtLeast(0))
        }
        Row(
            Modifier.horizontalScroll(scroll).padding(horizontal = maxWidth / 2 - 30.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RAIL_ITEMS.forEach { item ->
                val isSelected = item is RailItem.Mode && item.mode == selected
                Text(
                    item.label.uppercase(),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .onGloballyPositioned { centers[item.label] = it.positionInParent().x + it.size.width / 2f }
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(enabled = enabled) {
                            when (item) {
                                is RailItem.Mode -> onSelectMode(item.mode)
                                RailItem.Scan -> onScan()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Optical-style zoom steps (0.5× 1× 2× 5× …) limited to what the lens supports. */
@Composable
fun ZoomChips(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (maxZoom <= minZoom + 0.01f) return
    val steps = buildList {
        add(minZoom.coerceAtMost(1f))
        listOf(1f, 2f, 5f, 10f).forEach { if (it in minZoom..maxZoom) add(it) }
        if (maxZoom > 10f) add(maxZoom.coerceAtMost(30f))
    }.distinct().sorted()
    val nearest = steps.minBy { abs(it - zoom) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.forEach { step ->
            val active = step == nearest
            Box(
                Modifier
                    .size(if (active) 42.dp else 36.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.Black.copy(alpha = 0.6f) else Color.Transparent)
                    .clickable { onZoom(step) }
                    .semantics { contentDescription = "Zoom ${formatZoom(step)}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (active) formatZoom(zoom) else formatZoom(step),
                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                    fontSize = if (active) 12.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun formatZoom(z: Float): String =
    if (abs(z - z.toInt()) < 0.05f) "${z.toInt()}×" else "%.1f×".format(z)

/** Strip of colour looks; each swatch previews the look's key colour. */
@Composable
fun FilterStrip(
    selected: LiveFilter,
    onSelect: (LiveFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(LiveFilter.entries) { f ->
            val active = f == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(f) }.semantics { contentDescription = "${f.label} look" },
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(LiveFilters.swatch(f)))
                        .border(
                            if (active) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    f.label,
                    fontSize = 11.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Last-capture thumbnail; opens the gallery. Shows a placeholder before the first shot. */
@Composable
fun GalleryThumbnail(
    uri: android.net.Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open gallery" },
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp))
        } else {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
        }
    }
}
