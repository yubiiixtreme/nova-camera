package com.novacamera.presentation.camera

import android.content.Intent
import android.media.MediaActionSound
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacamera.core.common.LevelMonitor
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.presentation.camera.components.AspectMaskOverlay
import com.novacamera.presentation.camera.components.BusyPill
import com.novacamera.presentation.camera.components.ChromeButton
import com.novacamera.presentation.camera.components.CountdownOverlay
import com.novacamera.presentation.camera.components.FilterStrip
import com.novacamera.presentation.camera.components.FocusPeakingOverlay
import com.novacamera.presentation.camera.components.FocusReticle
import com.novacamera.presentation.camera.components.GalleryThumbnail
import com.novacamera.presentation.camera.components.GridOverlay
import com.novacamera.presentation.camera.components.HistogramOverlay
import com.novacamera.presentation.camera.components.LevelOverlay
import com.novacamera.presentation.camera.components.ModeSelector
import com.novacamera.presentation.camera.components.ProControlPanel
import com.novacamera.presentation.camera.components.QuickToolbar
import com.novacamera.presentation.camera.components.RecordingPill
import com.novacamera.presentation.camera.components.ShutterButton
import com.novacamera.presentation.camera.components.ZebraOverlay
import com.novacamera.presentation.camera.components.ZoomChips
import com.novacamera.presentation.theme.NovaAmber
import com.novacamera.util.Permissions
import com.novacamera.util.ShutterEvents
import com.novacamera.util.tick
import kotlinx.coroutines.delay

/**
 * Camera screen. Photo modes show a true 4:3 viewfinder (what you see is what
 * you get); video modes go full-bleed. Controls follow a conventional layout:
 * toggles on top, zoom on the viewfinder, mode rail + shutter row below.
 */
@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenScan: () -> Unit,
    vm: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(Permissions.hasCamera(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCameraPermission = Permissions.hasCamera(context)
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch((Permissions.CAMERA + Permissions.legacyStorage()))
        }
    }

    if (!hasCameraPermission) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch((Permissions.CAMERA + Permissions.legacyStorage())) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        return
    }

    CameraContent(vm, onOpenGallery, onOpenSettings, onOpenScan)
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = NovaAmber, modifier = Modifier.size(64.dp))
            Text("NovaCamera needs your camera", style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Text(
                "Photos and videos are saved on your device only. Nothing is uploaded.",
                color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            )
            Button(onClick = onRequest) { Text("Allow camera access") }
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}

@Composable
private fun CameraContent(
    vm: CameraViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScan: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val stats by vm.frameStats.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current
    val settings = ui.settings
    val pro = settings.proControls

    val isVideoMode = settings.captureMode == CaptureMode.VIDEO || settings.captureMode == CaptureMode.SLOW_MOTION
    val wantAnalysis = !isVideoMode && (pro.histogramEnabled || pro.zebraEnabled || pro.focusPeakingEnabled)

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var bindNonce by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf(Offset.Zero) }
    var focusStamp by remember { mutableLongStateOf(0L) }
    var interactStamp by remember { mutableLongStateOf(0L) }
    var focusVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val flash = remember { Animatable(0f) }
    val sound = remember {
        MediaActionSound().also {
            it.load(MediaActionSound.SHUTTER_CLICK)
            it.load(MediaActionSound.START_VIDEO_RECORDING)
            it.load(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    val soundOn by rememberUpdatedState(settings.shutterSound)

    // Horizon level sensor only runs when the overlay is on.
    val level = remember { LevelMonitor() }
    val tilt by level.tilt.collectAsState()
    DisposableEffect(settings.levelEnabled) {
        if (settings.levelEnabled) level.start(context)
        onDispose { level.stop() }
    }

    // Microphone for video (clips still record silently if declined).
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(isVideoMode) {
        if (isVideoMode && !Permissions.hasMic(context)) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // Volume-key shutter: only the visible camera screen fires.
    LaunchedEffect(Unit) {
        ShutterEvents.presses.collect { vm.onIntent(CameraIntent.Shutter) }
    }

    // Shutter flash + click at the moment a still is actually taken.
    LaunchedEffect(Unit) {
        vm.shutterFired.collect {
            view.tick()
            if (soundOn) sound.play(MediaActionSound.SHUTTER_CLICK)
            flash.snapTo(0.55f)
            flash.animateTo(0f, tween(220))
        }
    }
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(ui.recording.active) {
        if (soundOn && ui.recording.active != wasRecording) {
            sound.play(if (ui.recording.active) MediaActionSound.START_VIDEO_RECORDING else MediaActionSound.STOP_VIDEO_RECORDING)
        }
        wasRecording = ui.recording.active
    }

    LaunchedEffect(ui.toast) {
        ui.toast?.let {
            snackbar.showSnackbar(it)
            vm.onIntent(CameraIntent.ClearToast)
        }
    }

    // (Re)bind whenever the surface is ready or a session-shaping setting changes.
    LaunchedEffect(previewView, lifecycle, settings.lensFacing, isVideoMode, wantAnalysis, settings.videoQuality, bindNonce) {
        previewView?.let { vm.bindCamera(lifecycle, it) }
    }

    // The tap-to-focus reticle lingers briefly; touching its EV handle keeps it alive.
    LaunchedEffect(focusStamp, interactStamp) {
        if (focusStamp == 0L) return@LaunchedEffect
        focusVisible = true
        delay(2500)
        focusVisible = false
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val portrait = maxHeight > maxWidth
        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
        // A true 4:3 frame needs room for the controls underneath; short (16:9) screens go full-bleed instead.
        val framed = !isVideoMode && portrait && maxHeight >= topPad + maxWidth * (4f / 3f) + 190.dp
        val viewfinderMod = if (!framed) {
            Modifier.fillMaxSize()
        } else {
            val ratio = 3f / 4f
            val width = minOf(maxWidth, (maxHeight - topPad) * ratio)
            Modifier.padding(top = topPad).align(Alignment.TopCenter).width(width).height(width / ratio)
        }

        // ---- viewfinder -------------------------------------------------
        Box(
            viewfinderMod
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            vm.onIntent(CameraIntent.SetZoom(vm.ui.value.settings.zoomRatio * zoomChange))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { view.tick(); vm.onIntent(CameraIntent.ToggleAfAeLock) },
                        onTap = { offset ->
                            focusPoint = offset
                            focusStamp = System.nanoTime()
                            vm.onTapToFocus(offset.x, offset.y)
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // COMPATIBLE = TextureView, which lets live looks grade the preview.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setBackgroundColor(android.graphics.Color.BLACK)
                        installLiveFilter()
                        previewView = this
                    }
                },
                update = { pv ->
                    pv.setLiveFilter(settings.filter)
                    if (previewView !== pv) previewView = pv
                },
                modifier = Modifier.fillMaxSize(),
            )
            GridOverlay(settings.gridEnabled, settings.gridStyle, Modifier.fillMaxSize())
            AspectMaskOverlay(settings.aspectMask, Modifier.fillMaxSize())
            LevelOverlay(settings.levelEnabled, tilt, Modifier.fillMaxSize())
            ZebraOverlay(pro.zebraEnabled, stats?.clippedFraction ?: 0f, Modifier.fillMaxSize())
            FocusPeakingOverlay(pro.focusPeakingEnabled, pro.manualFocusDistance != null, stats?.sharpness ?: 0f, Modifier.fillMaxSize())
            HistogramOverlay(pro.histogramEnabled, stats?.lumaHist, Modifier.align(Alignment.TopStart).padding(12.dp).width(140.dp))

            if (focusStamp != 0L) {
                AnimatedVisibility(focusVisible, enter = fadeIn(tween(80)), exit = fadeOut(tween(300))) {
                    FocusReticle(
                        position = focusPoint,
                        stamp = focusStamp,
                        ev = pro.exposureCompensationEv,
                        evMin = ui.caps.evMin,
                        evMax = ui.caps.evMax,
                        onEvChange = { vm.onIntent(CameraIntent.SetEv(it)) },
                        onInteract = { interactStamp = System.nanoTime() },
                    )
                }
            }

            CountdownOverlay(ui.countdown, Modifier.fillMaxSize())

            if (ui.camera.afLocked) {
                AssistChip(
                    onClick = { vm.onIntent(CameraIntent.ToggleAfAeLock) },
                    label = { Text("AF/AE locked") },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = if (!framed) topPad + 4.dp else 8.dp),
                )
            }
            if (ui.camera.thermalThrottled) {
                AssistChip(
                    onClick = {},
                    label = { Text("Device is warm — effects reduced") },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = if (!framed) topPad + 44.dp else 48.dp),
                )
            }
            ZoomChips(
                zoom = settings.zoomRatio,
                minZoom = ui.caps.minZoom,
                maxZoom = ui.caps.maxZoom,
                onZoom = { vm.onIntent(CameraIntent.SetZoom(it)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (framed) 14.dp else if (portrait) 230.dp else 84.dp),
            )
            ui.busyLabel?.let {
                BusyPill(it, Modifier.align(Alignment.BottomCenter).padding(bottom = if (framed) 70.dp else if (portrait) 290.dp else 140.dp))
            }
            if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        // Camera failed to start: say why and offer a retry instead of a silent black screen.
        if (!ui.camera.isBound && ui.camera.error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Can't start the camera", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(ui.camera.error ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                OutlinedButton(onClick = { bindNonce++ }) { Text("Try again") }
            }
        }

        // ---- top bar ------------------------------------------------------
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding()) {
            QuickToolbar(
                flashMode = settings.flashMode,
                hasFlash = ui.caps.hasFlash,
                timerSeconds = settings.timerSeconds,
                filtersOpen = ui.showFilters,
                filterActive = settings.filter != com.novacamera.domain.model.LiveFilter.NONE,
                proOpen = ui.showProPanel,
                onCycleFlash = {
                    val next = if (isVideoMode) {
                        if (settings.flashMode == FlashMode.TORCH) FlashMode.OFF else FlashMode.TORCH
                    } else {
                        when (settings.flashMode) {
                            FlashMode.OFF -> FlashMode.AUTO
                            FlashMode.AUTO -> FlashMode.ON
                            FlashMode.ON -> FlashMode.TORCH
                            FlashMode.TORCH -> FlashMode.OFF
                        }
                    }
                    vm.onIntent(CameraIntent.SetFlash(next))
                },
                onCycleTimer = { vm.onIntent(CameraIntent.CycleTimer) },
                onToggleFilters = { vm.onIntent(CameraIntent.ToggleFilters) },
                onToggleProPanel = { vm.onIntent(CameraIntent.ToggleProPanel) },
                onOpenSettings = onOpenSettings,
            )
            if (ui.recording.active) {
                RecordingPill(ui.recording.durationMs, ui.recording.paused, Modifier.align(Alignment.CenterHorizontally))
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
        ) { data -> Snackbar(data) }

        // ---- controls -----------------------------------------------------
        val panels: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = ui.showProPanel,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Color(0xF2131417), modifier = Modifier.fillMaxWidth()) {
                    ProControlPanel(
                        settings = settings,
                        caps = ui.caps,
                        afLocked = ui.camera.afLocked || ui.camera.aeLocked,
                        onIntent = vm::onIntent,
                        modifier = Modifier.heightIn(max = if (portrait) 300.dp else 190.dp).padding(top = 10.dp),
                    )
                }
            }
            AnimatedVisibility(visible = ui.showFilters, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                Column(Modifier.background(Color.Black.copy(alpha = 0.5f))) {
                    Spacer(Modifier.height(10.dp))
                    FilterStrip(settings.filter, { vm.onIntent(CameraIntent.SetFilter(it)) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        val modes: @Composable (Modifier) -> Unit = { mod ->
            ModeSelector(
                selected = settings.captureMode,
                enabled = !ui.recording.active && !ui.camera.isCapturing && ui.countdown == null,
                onSelectMode = { vm.onIntent(CameraIntent.SetMode(it)) },
                onScan = onOpenScan,
                modifier = mod,
            )
        }
        val thumbnail: @Composable () -> Unit = {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { GalleryThumbnail(ui.thumbnail, onOpenGallery) }
        }
        val shutter: @Composable () -> Unit = {
            ShutterButton(
                isCapturing = ui.camera.isCapturing && !isVideoMode,
                isVideo = isVideoMode,
                isRecording = ui.recording.active,
                cancellable = ui.countdown != null || (settings.captureMode == CaptureMode.TIMELAPSE && ui.camera.isCapturing),
                onClick = { vm.onIntent(CameraIntent.Shutter) },
            )
        }
        val sideAction: @Composable () -> Unit = {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (ui.recording.active) {
                    ChromeButton(
                        icon = if (ui.recording.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        description = if (ui.recording.paused) "Resume recording" else "Pause recording",
                        onClick = { vm.onIntent(CameraIntent.PauseResumeVideo) },
                    )
                } else if (ui.caps.canSwitchLens) {
                    ChromeButton(Icons.Default.Cameraswitch, "Switch camera", { vm.onIntent(CameraIntent.SwitchCamera) })
                }
            }
        }

        if (portrait) {
            val scrim = if (framed) Brush.verticalGradient(listOf(Color.Black, Color.Black))
            else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(scrim).navigationBarsPadding().padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                panels()
                modes(Modifier.padding(top = 4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    thumbnail(); shutter(); sideAction()
                }
            }
        } else {
            // Landscape: shutter cluster down the right edge, mode rail + panels along the bottom.
            val railWidth = 120.dp
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(railWidth)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .statusBarsPadding().navigationBarsPadding().padding(end = 8.dp, top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                thumbnail(); shutter(); sideAction()
            }
            Column(
                Modifier.align(Alignment.BottomStart).width(maxWidth - railWidth).navigationBarsPadding(),
            ) {
                panels()
                modes(Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(vertical = 2.dp))
            }
        }
    }
}
