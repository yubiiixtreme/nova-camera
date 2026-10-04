package com.novacamera.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacamera.domain.model.FlashMode

/** Round, translucent icon button that stays legible over any viewfinder content. */
@Composable
fun ChromeButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: String? = null,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.38f))
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(22.dp),
            )
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 9.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp),
                )
            }
        }
    }
}

/** Top bar: flash · timer on the left; looks · pro · settings on the right. */
@Composable
fun QuickToolbar(
    flashMode: FlashMode,
    hasFlash: Boolean,
    timerSeconds: Int,
    filtersOpen: Boolean,
    filterActive: Boolean,
    proOpen: Boolean,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleFilters: () -> Unit,
    onToggleProPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasFlash) {
                ChromeButton(
                    icon = when (flashMode) {
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.TORCH -> Icons.Default.Highlight
                    },
                    description = "Flash: ${flashMode.name.lowercase()}. Tap to change",
                    onClick = onCycleFlash,
                    active = flashMode != FlashMode.OFF,
                )
            }
            ChromeButton(
                icon = if (timerSeconds == 0) Icons.Default.TimerOff else Icons.Default.Timer,
                description = if (timerSeconds == 0) "Self-timer off" else "Self-timer $timerSeconds seconds",
                onClick = onCycleTimer,
                active = timerSeconds != 0,
                badge = if (timerSeconds == 0) null else "${timerSeconds}s",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChromeButton(Icons.Default.AutoAwesome, "Looks and filters", onToggleFilters, active = filtersOpen || filterActive)
            ChromeButton(Icons.Default.Tune, "Pro controls", onToggleProPanel, active = proOpen)
            ChromeButton(Icons.Default.Settings, "Settings", onOpenSettings)
        }
    }
}
