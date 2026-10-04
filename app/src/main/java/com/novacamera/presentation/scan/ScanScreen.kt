package com.novacamera.presentation.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

/**
 * Student scan flow: pick a photo of notes/slides/whiteboard, straighten it,
 * copy the recognized text, and save/share a PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var pdfName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setSource(uri)
    }

    LaunchedEffect(ui.error) {
        ui.error?.let { snack.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan notes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ui.preview == null) {
                Text("Pick a photo of handwritten notes, slides, or a whiteboard.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch("image/*") }) { Text("Pick photo") }
                return@Column
            }

            AsyncImage(
                model = ui.scanned ?: ui.preview,
                contentDescription = if (ui.scanned != null) "Scanned page" else "Picked photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row {
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Change") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.scan() }, enabled = !ui.scanning && !ui.ocrRunning) {
                    Text(if (ui.scanned == null) "Scan document" else "Re-scan")
                }
            }

            if (ui.scanning || ui.ocrRunning) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
                Text(if (ui.scanning) "Straightening page…" else "Reading text…")
            }

            if (ui.scanned != null && ui.ocrText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(ui.ocrText, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { copyText(context, ui.ocrText) }) { Text("Copy") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { shareText(context, ui.ocrText) }) { Text("Share") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pdfName,
                    onValueChange = { pdfName = it },
                    label = { Text("PDF name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { vm.savePdf(pdfName) }) { Text("Save PDF") }
                    if (ui.pdfUri != null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { openPdf(context, ui.pdfUri.toString()) }) { Text("Open PDF") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { sharePdf(context, ui.pdfUri.toString()) }) { Text("Share PDF") }
                    }
                }
                if (ui.pdfUri != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Saved as ${ui.pdfName}.pdf in Downloads.")
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("scan", text))
}

private fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }.let { Intent.createChooser(it, "Share text") },
    )
}

private fun openPdf(context: Context, uri: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.parse(uri), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Open PDF") },
    )
}

private fun sharePdf(context: Context, uri: String) {
    context.startActivity(
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share PDF") },
    )
}
