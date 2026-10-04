package com.novacamera.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.GridStyle
import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.LiveFilter
import com.novacamera.domain.model.ProControls
import com.novacamera.domain.model.PhotoFormat
import com.novacamera.domain.model.StabilizationMode
import com.novacamera.domain.model.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore("camera_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object K {
        val MODE = stringPreferencesKey("mode")
        val LENS = stringPreferencesKey("lens")
        val FLASH = stringPreferencesKey("flash")
        val FORMAT = stringPreferencesKey("format")
        val QUALITY = stringPreferencesKey("quality")
        val ZOOM = floatPreferencesKey("zoom")
        val BOKEH = floatPreferencesKey("bokeh")
        val GRID = booleanPreferencesKey("grid")
        val LOC = booleanPreferencesKey("loc")
        val STRIP = booleanPreferencesKey("strip")
        val AZOOM = booleanPreferencesKey("azoom")
        val SLOW_FPS = intPreferencesKey("slow_fps")
        val TL_MS = longPreferencesKey("tl_ms")
        val TL_SHOTS = intPreferencesKey("tl_shots")
        val BURSTS = intPreferencesKey("bursts")
        val HDRF = intPreferencesKey("hdrf")
        val HDRSTEP = intPreferencesKey("hdrstep")
        val GRIDSTYLE = stringPreferencesKey("gridstyle")
        val ASPECT = stringPreferencesKey("aspect")
        val LEVEL = booleanPreferencesKey("level")
        val LUT = stringPreferencesKey("lut")
        val FILTER = stringPreferencesKey("filter")
        val TIMER = intPreferencesKey("timer")
        val SOUND = booleanPreferencesKey("sound")
        val P_FOCUS = floatPreferencesKey("p_focus")
        val P_PEAK = booleanPreferencesKey("p_peak")
        val P_ISO = intPreferencesKey("p_iso")
        val P_SHUTTER = doublePreferencesKey("p_shutter")
        val P_WB = intPreferencesKey("p_wb")
        val P_EV = floatPreferencesKey("p_ev")
        val P_ZEBRA = booleanPreferencesKey("p_zebra")
        val P_HIST = booleanPreferencesKey("p_hist")
    }

    private fun parse(p: Preferences): CameraSettings =
        CameraSettings(
            captureMode = runCatching { CaptureMode.valueOf(p[K.MODE] ?: "PHOTO") }.getOrDefault(CaptureMode.PHOTO),
            lensFacing = runCatching { LensFacing.valueOf(p[K.LENS] ?: "BACK") }.getOrDefault(LensFacing.BACK),
            flashMode = runCatching { FlashMode.valueOf(p[K.FLASH] ?: "OFF") }.getOrDefault(FlashMode.OFF),
            stabilization = StabilizationMode.OIS_PLUS_EIS,
            photoFormat = runCatching { PhotoFormat.valueOf(p[K.FORMAT] ?: "JPEG") }.getOrDefault(PhotoFormat.JPEG),
            videoQuality = runCatching { VideoQuality.valueOf(p[K.QUALITY] ?: "UHD_4K_30") }.getOrDefault(VideoQuality.UHD_4K_30),
            slowMotionFps = p[K.SLOW_FPS] ?: 240,
            timelapseIntervalMs = p[K.TL_MS] ?: 2000L,
            timelapseShots = (p[K.TL_SHOTS] ?: 12).coerceIn(2, 300),
            burstShots = (p[K.BURSTS] ?: 10).coerceIn(2, 50),
            hdrFrames = (p[K.HDRF] ?: 3).coerceIn(3, 7),
            hdrStepEv = (p[K.HDRSTEP] ?: 1).coerceIn(1, 3),
            zoomRatio = p[K.ZOOM] ?: 1f,
            bokehStrength = p[K.BOKEH] ?: 0.5f,
            gridEnabled = p[K.GRID] ?: true,
            gridStyle = runCatching { GridStyle.valueOf(p[K.GRIDSTYLE] ?: "THIRDS") }.getOrDefault(GridStyle.THIRDS),
            aspectMask = runCatching { AspectMask.valueOf(p[K.ASPECT] ?: "FULL") }.getOrDefault(AspectMask.FULL),
            levelEnabled = p[K.LEVEL] ?: true,
            locationTagging = p[K.LOC] ?: false,
            stripExifOnExport = p[K.STRIP] ?: true,
            audioZoomEnabled = p[K.AZOOM] ?: false,
            lutId = p[K.LUT],
            filter = runCatching { LiveFilter.valueOf(p[K.FILTER] ?: "NONE") }.getOrDefault(LiveFilter.NONE),
            timerSeconds = p[K.TIMER] ?: 0,
            shutterSound = p[K.SOUND] ?: true,
            proControls = ProControls(
                manualFocusDistance = p[K.P_FOCUS],
                focusPeakingEnabled = p[K.P_PEAK] ?: false,
                iso = p[K.P_ISO],
                shutterSpeedSec = p[K.P_SHUTTER],
                whiteBalanceKelvin = p[K.P_WB],
                exposureCompensationEv = p[K.P_EV] ?: 0f,
                zebraEnabled = p[K.P_ZEBRA] ?: false,
                histogramEnabled = p[K.P_HIST] ?: false,
            ),
        )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(CameraSettings())
    private val persistRequests = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var loaded = false
    private val pending = mutableListOf<(CameraSettings) -> CameraSettings>()

    /**
     * Hot, in-memory source of truth shared by every screen. Updates apply
     * synchronously (so sliders never fight a DataStore round-trip) and are
     * persisted in the background, coalescing bursts into the latest value.
     */
    val settings: StateFlow<CameraSettings> = state.asStateFlow()

    init {
        scope.launch {
            val disk = runCatching { parse(context.settingsStore.data.first()) }.getOrNull()
            synchronized(lock) {
                // Changes made before the disk read finished are replayed on top of what was saved.
                if (disk != null) state.value = pending.fold(disk) { acc, t -> t(acc) }
                pending.clear()
                loaded = true
            }
            persistRequests.trySend(Unit)
            for (ignored in persistRequests) {
                persist(state.value)
            }
        }
    }

    fun update(transform: (CameraSettings) -> CameraSettings) {
        synchronized(lock) {
            if (!loaded) pending += transform
            state.update(transform)
        }
        persistRequests.trySend(Unit)
    }

    private suspend fun persist(next: CameraSettings) {
        context.settingsStore.edit { e ->
            e[K.MODE] = next.captureMode.name
            e[K.LENS] = next.lensFacing.name
            e[K.FLASH] = next.flashMode.name
            e[K.FORMAT] = next.photoFormat.name
            e[K.QUALITY] = next.videoQuality.name
            e[K.ZOOM] = next.zoomRatio
            e[K.BOKEH] = next.bokehStrength
            e[K.GRID] = next.gridEnabled
            e[K.LOC] = next.locationTagging
            e[K.STRIP] = next.stripExifOnExport
            e[K.AZOOM] = next.audioZoomEnabled
            e[K.SLOW_FPS] = next.slowMotionFps
            e[K.TL_MS] = next.timelapseIntervalMs
            e[K.TL_SHOTS] = next.timelapseShots
            e[K.BURSTS] = next.burstShots
            e[K.HDRF] = next.hdrFrames
            e[K.HDRSTEP] = next.hdrStepEv
            e[K.GRIDSTYLE] = next.gridStyle.name
            e[K.ASPECT] = next.aspectMask.name
            e[K.LEVEL] = next.levelEnabled
            next.lutId?.let { e[K.LUT] = it } ?: e.remove(K.LUT)
            e[K.FILTER] = next.filter.name
            e[K.TIMER] = next.timerSeconds
            e[K.SOUND] = next.shutterSound
            val pc = next.proControls
            pc.manualFocusDistance?.let { e[K.P_FOCUS] = it } ?: e.remove(K.P_FOCUS)
            e[K.P_PEAK] = pc.focusPeakingEnabled
            pc.iso?.let { e[K.P_ISO] = it } ?: e.remove(K.P_ISO)
            pc.shutterSpeedSec?.let { e[K.P_SHUTTER] = it } ?: e.remove(K.P_SHUTTER)
            pc.whiteBalanceKelvin?.let { e[K.P_WB] = it } ?: e.remove(K.P_WB)
            e[K.P_EV] = pc.exposureCompensationEv
            e[K.P_ZEBRA] = pc.zebraEnabled
            e[K.P_HIST] = pc.histogramEnabled
        }
    }
}
