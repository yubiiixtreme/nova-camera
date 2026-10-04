package com.novacamera.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacamera.presentation.camera.CameraIntent
import com.novacamera.presentation.camera.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: CameraViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var presetName by remember { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            SettingRow("Location tagging (adds GPS EXIF)", ui.settings.locationTagging,
                { vm.onIntent(CameraIntent.SetLocationTagging(it)) })
            SettingRow("Strip EXIF location on export (privacy)", ui.settings.stripExifOnExport,
                { vm.onIntent(CameraIntent.SetStripExif(it)) })
            SettingRow("Grid overlay", ui.settings.gridEnabled,
                { vm.onIntent(CameraIntent.SetGrid(it)) })
            SettingRow("Audio zoom", ui.settings.audioZoomEnabled,
                { vm.onIntent(CameraIntent.SetAudioZoom(it)) })
            Text("Volume-key shutter, high-contrast UI, and TalkBack labels are always on.", modifier = Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(16.dp))
            Text("Shooting presets")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Name this setup") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.savePreset(presetName); presetName = "" }) { Text("Save") }
            }
            ui.presets.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(p.name, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.applyPreset(p.name) }) { Text("Apply") }
                    IconButton(onClick = { vm.deletePreset(p.name) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${p.name}")
                    }
                }
            }
            if (ui.presets.isEmpty()) {
                Text("No presets yet — dial in a look, then save it here.", modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
