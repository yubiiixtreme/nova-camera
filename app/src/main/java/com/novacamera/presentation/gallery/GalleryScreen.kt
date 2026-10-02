package com.novacamera.presentation.gallery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novacamera.presentation.camera.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit, vm: GalleryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Gallery — swipe down from camera to return") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
        )
    }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            items(items) { m ->
                AsyncImage(
                    model = m.uri, contentDescription = m.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(4.dp).fillMaxSize(),
                )
            }
        }
    }
}
