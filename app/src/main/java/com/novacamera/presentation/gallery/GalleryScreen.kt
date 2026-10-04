package com.novacamera.presentation.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novacamera.presentation.camera.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenVault: () -> Unit,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (items.isEmpty()) "Gallery" else "Gallery · ${items.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera") }
                },
                actions = {
                    IconButton(onClick = onOpenVault) { Icon(Icons.Default.Lock, contentDescription = "Private vault") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                items.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 12.dp))
                        Text("No photos or videos yet", color = Color.White.copy(alpha = 0.7f))
                        Text("Shots you take with NovaCamera appear here.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items, key = { _, m -> m.uri.toString() }) { index, m ->
                        Box(
                            Modifier.padding(1.dp).aspectRatio(1f).clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenViewer(index) },
                        ) {
                            AsyncImage(
                                model = m.uri, contentDescription = m.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                            if (m.isVideo) {
                                Box(
                                    Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.padding(end = 2.dp))
                                        m.durationMs?.let { Text(formatDuration(it), color = Color.White, fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
