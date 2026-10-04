package com.novacamera.presentation.camera

import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacamera.core.common.LevelMonitor
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.presentation.camera.components.AspectMaskOverlay
import com.novacamera.presentation.camera.components.FocusPeakingOverlay
import com.novacamera.presentation.camera.components.GridOverlay
import com.novacamera.presentation.camera.components.HistogramOverlay
import com.novacamera.presentation.camera.components.LevelOverlay
import com.novacamera.presentation.camera.components.ProControlPanel
import com.novacamera.presentation.camera.components.QuickToolbar
import com.novacamera.presentation.camera.components.ShutterButton
import com.novacamera.presentation.camera.components.ZebraOverlay
import com.novacamera.util.Permissions
import com.novacamera.util.ShutterEvents
import com.novacamera.util.tick

/**
 * Main camera screen: lifecycle-aware PreviewView + gesture zoom/focus,
 * mode rail, floating shutter, pro panel, and exposure overlays.
 * Foldable-aware: controls re-flow via weight; PreviewView avoids distortion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenScan: () -> Unit,
    vm: CameraViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val stats by vm.frameStats.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var hasCameraPermission by remember { mutableStateOf(Permissions.hasCamera(context)) }
    var chromeVisible by remember { mutableStateOf(true) }
    val level = remember { LevelMonitor() }
    val tilt by level.tilt.collectAsState()

    DisposableEffect(context) {
        level.start(context)
        onDispose { level.stop() }
    }

    // Volume-key shutter: only the visible camera screen fires.
    LaunchedEffect(Unit) {
        ShutterEvents.presses.collect {
            view.tick()
            vm.onIntent(CameraIntent.Shutter)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasCameraPermission = grants.values.all { it } || Permissions.hasCamera(context)
    }

    LaunchedEffect(Unit) {
        if (!Permissions.hasCamera(context)) {
            permissionLauncher.launch((Permissions.CAMERA + Permissions.mediaRead()).distinct().toTypedArray())
        }
    }

    // (Re)bind camera whenever the preview surface is ready or session-affecting settings change.
    LaunchedEffect(previewView, lifecycle, ui.settings.lensFacing, ui.settings.captureMode, ui.settings.videoQuality) {
        previewView?.let { vm.bindCamera(lifecycle, it) }
    }

    if (!hasCameraPermission) {
        Scaffold { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access is needed for preview and capture.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        permissionLauncher.launch((Permissions.CAMERA + Permissions.mediaRead()).distinct().toTypedArray())
                    }) { Text("Grant camera permission") }
                }
            }
        }
        return
    }

    Scaffold { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (chromeVisible) {
                QuickToolbar(
                    flashOn = ui.settings.flashMode != FlashMode.OFF,
                gridOn = ui.settings.gridEnabled,
                onToggleFlash = {
                    vm.onIntent(
                        CameraIntent.SetFlash(
                            if (ui.settings.flashMode == FlashMode.OFF) FlashMode.ON else FlashMode.OFF,
                        ),
                    )
                },
                onToggleGrid = { vm.onIntent(CameraIntent.ToggleGrid) },
                onSwitchCamera = { vm.onIntent(CameraIntent.SwitchCamera) },
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings,
                onToggleChrome = { chromeVisible = false },
            )
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val next = (ui.settings.zoomRatio * zoomChange).coerceIn(1f, 10f)
                            vm.onIntent(CameraIntent.SetZoom(next))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { vm.onIntent(CameraIntent.SwitchCamera) },
                            onLongPress = { vm.onIntent(CameraIntent.ToggleAfAeLock) },
                            onTap = { offset ->
                                vm.onTapToFocus(offset.x / size.width, offset.y / size.height)
                            },
                        )
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    },
                    update = { pv -> previewView = pv },
                    modifier = Modifier.fillMaxSize(),
                )
                GridOverlay(enabled = ui.settings.gridEnabled, style = ui.settings.gridStyle, modifier = Modifier.fillMaxSize())
                AspectMaskOverlay(mask = ui.settings.aspectMask, modifier = Modifier.fillMaxSize())
                LevelOverlay(enabled = ui.settings.levelEnabled, tilt = tilt, modifier = Modifier.fillMaxSize())
                HistogramOverlay(enabled = ui.settings.proControls.histogramEnabled, hist = stats?.lumaHist, modifier = Modifier.align(Alignment.TopCenter))
                ZebraOverlay(enabled = ui.settings.proControls.zebraEnabled, clippedFraction = stats?.clippedFraction ?: 0f, modifier = Modifier.fillMaxSize())
                FocusPeakingOverlay(
                    enabled = ui.settings.proControls.focusPeakingEnabled,
                    manualFocus = ui.settings.proControls.manualFocusDistance != null,
                    sharpness = stats?.sharpness ?: 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                if (ui.camera.afLocked) {
                    AssistChip(onClick = { vm.onIntent(CameraIntent.ToggleAfAeLock) }, label = { Text("AF/AE locked") }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                if (ui.camera.thermalThrottled) {
                    AssistChip(onClick = {}, label = { Text("Thermal saver: effects reduced") }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
                }
                if (!chromeVisible) {
                    IconButton(onClick = { chromeVisible = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show interface")
                    }
                }
            }

            if (chromeVisible) {
            // Mode rail
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState())) {
                listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.BURST, CaptureMode.TIMELAPSE, CaptureMode.PORTRAIT, CaptureMode.NIGHT, CaptureMode.HDR, CaptureMode.DOCUMENT).forEach { m ->
                    FilterChip(
                        selected = ui.settings.captureMode == m,
                        onClick = { vm.onIntent(CameraIntent.SetMode(m)) },
                        label = { Text(m.name) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            // Zoom slider (pinch also supported)
            Slider(
                value = ui.settings.zoomRatio,
                onValueChange = { vm.onIntent(CameraIntent.SetZoom(it)) },
                valueRange = 1f..10f,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { vm.onIntent(CameraIntent.ToggleProPanel) }, label = { Text("PRO") })
                Spacer(Modifier.width(12.dp))
                AssistChip(onClick = onOpenScan, label = { Text("Scan") })
                Spacer(Modifier.width(12.dp))
                AssistChip(onClick = onOpenVault, label = { Text("Vault") })
                Spacer(Modifier.weight(1f))
                ShutterButton(
                    isCapturing = ui.camera.isCapturing,
                    isVideo = ui.settings.captureMode == CaptureMode.VIDEO,
                    onClick = { view.tick(); vm.onIntent(CameraIntent.Shutter) },
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(96.dp))
            }

            if (ui.showProPanel) {
                ProControlPanel(
                    pro = ui.settings,
                    settings = ui.settings,
                    afLocked = ui.camera.afLocked || ui.camera.aeLocked,
                    onIntent = vm::onIntent,
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }
            }
        }
    }
}
