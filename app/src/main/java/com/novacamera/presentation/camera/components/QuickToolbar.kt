package com.novacamera.presentation.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Customizable quick-action toolbar (top bar). */
@Composable
fun QuickToolbar(
    flashOn: Boolean,
    gridOn: Boolean,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = onToggleFlash) {
            Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Toggle flash")
        }
        IconButton(onClick = onSwitchCamera) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "Double-tap also switches camera")
        }
        IconButton(onClick = onOpenGallery) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Open gallery")
        }
        IconButton(onClick = onToggleGrid) {
            Icon(if (gridOn) Icons.Default.GridOn else Icons.Default.GridOff, contentDescription = "Toggle grid overlay")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
