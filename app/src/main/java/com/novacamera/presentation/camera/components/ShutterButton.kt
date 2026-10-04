package com.novacamera.presentation.camera.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.novacamera.presentation.theme.NovaRed

/**
 * Shutter: white ring + disc for stills, red disc for video, red rounded
 * square while recording, amber stop-square while a timer/timelapse can be cancelled.
 */
@Composable
fun ShutterButton(
    isCapturing: Boolean,
    isVideo: Boolean,
    isRecording: Boolean,
    cancellable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "shutterScale")

    val stopShape = isRecording || cancellable
    val inner by animateDpAsState(
        when {
            stopShape -> 30.dp
            isCapturing -> 44.dp
            pressed -> 56.dp
            else -> 62.dp
        },
        label = "shutterInner",
    )
    val innerColor = when {
        cancellable -> MaterialTheme.colorScheme.primary
        isVideo || isRecording -> NovaRed
        else -> Color.White
    }
    val label = when {
        isRecording -> "Stop recording"
        cancellable -> "Cancel"
        isVideo -> "Start recording"
        else -> "Take photo"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(82.dp)
            .scale(scale)
            .semantics { contentDescription = label; role = Role.Button }
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(inner)
                .clip(if (stopShape) RoundedCornerShape(8.dp) else CircleShape)
                .background(innerColor),
        )
        if (isCapturing && !stopShape) {
            CircularProgressIndicator(
                modifier = Modifier.size(74.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
    }
}
