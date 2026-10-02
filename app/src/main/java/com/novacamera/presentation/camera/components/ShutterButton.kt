package com.novacamera.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Floating shutter button: large hit-target, haptic on press, TalkBack labeled. */
@Composable
fun ShutterButton(
    isCapturing: Boolean,
    isVideo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = 84.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outer)
            .semantics { contentDescription = if (isVideo) "Record video" else "Take photo" }
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(if (isCapturing) 40.dp else 64.dp)
                .clip(if (isVideo) androidx.compose.foundation.shape.RoundedCornerShape(12.dp) else CircleShape)
                .background(if (isVideo) Color.Red else MaterialTheme.colorScheme.primary),
        )
    }
}
