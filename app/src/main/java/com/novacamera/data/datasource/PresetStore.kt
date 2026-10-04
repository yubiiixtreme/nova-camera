package com.novacamera.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.GridStyle
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.PhotoFormat
import com.novacamera.domain.model.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.presetStore by preferencesDataStore("presets")

data class Preset(val name: String, val settings: CameraSettings)

/**
 * Named shooting setups ("Street", "Landscape RAW") persisted as compact
 * strings. Codec is pure and unit-tested; storage is a string set.
 */
object PresetCodec {
    fun encode(name: String, s: CameraSettings): String {
        val p = s.proControls
        return listOf(
            "v1",
            name.replace('|', ' ').take(40),
            s.captureMode.name, s.lensFacing.name, s.flashMode.name,
            s.photoFormat.name, s.videoQuality.name,
            s.slowMotionFps.toString(), s.timelapseIntervalMs.toString(),
            s.timelapseShots.toString(), s.burstShots.toString(),
            s.hdrFrames.toString(), s.hdrStepEv.toString(),
            s.zoomRatio.toString(), s.bokehStrength.toString(),
            s.gridEnabled.toString(), s.gridStyle.name, s.aspectMask.name,
            s.levelEnabled.toString(), s.locationTagging.toString(),
            s.stripExifOnExport.toString(), s.audioZoomEnabled.toString(),
            s.lutId ?: "",
            p.manualFocusDistance?.toString() ?: "",
            p.focusPeakingEnabled.toString(),
            p.iso?.toString() ?: "",
            p.shutterSpeedSec?.toString() ?: "",
            p.whiteBalanceKelvin?.toString() ?: "",
            p.exposureCompensationEv.toString(),
            p.zebraEnabled.toString(), p.histogramEnabled.toString(),
            p.rawEnabled.toString(),
        ).joinToString("|")
    }

    fun decode(raw: String): Preset? = runCatching {
        val f = raw.split('|')
        require(f.size == 32 && f[0] == "v1") { "bad preset" }
        fun bool(i: Int, d: Boolean) = f[i].toBooleanStrictOrNull() ?: d
        fun int(i: Int, d: Int) = f[i].toIntOrNull() ?: d
        fun long(i: Int, d: Long) = f[i].toLongOrNull() ?: d
        fun float(i: Int, d: Float) = f[i].toFloatOrNull() ?: d
        fun <T : Enum<T>> enum(i: Int, d: T, of: (String) -> T): T =
            runCatching { of(f[i]) }.getOrDefault(d)
        val pro = com.novacamera.domain.model.ProControls(
            manualFocusDistance = f[23].toFloatOrNull(),
            focusPeakingEnabled = bool(24, false),
            iso = f[25].toIntOrNull(),
            shutterSpeedSec = f[26].toDoubleOrNull(),
            whiteBalanceKelvin = f[27].toIntOrNull(),
            exposureCompensationEv = float(28, 0f),
            zebraEnabled = bool(29, false),
            histogramEnabled = bool(30, false),
            rawEnabled = bool(31, false),
        )
        Preset(
            f[1],
            CameraSettings(
                captureMode = enum(2, CaptureMode.PHOTO, CaptureMode::valueOf),
                lensFacing = enum(3, LensFacing.BACK, LensFacing::valueOf),
                flashMode = enum(4, FlashMode.OFF, FlashMode::valueOf),
                photoFormat = enum(5, PhotoFormat.JPEG, PhotoFormat::valueOf),
                videoQuality = enum(6, VideoQuality.UHD_4K_30, VideoQuality::valueOf),
                slowMotionFps = int(7, 240),
                timelapseIntervalMs = long(8, 2000L),
                timelapseShots = int(9, 12),
                burstShots = int(10, 10),
                hdrFrames = int(11, 3),
                hdrStepEv = int(12, 1),
                zoomRatio = float(13, 1f),
                bokehStrength = float(14, 0.5f),
                gridEnabled = bool(15, true),
                gridStyle = enum(16, GridStyle.THIRDS, GridStyle::valueOf),
                aspectMask = enum(17, AspectMask.FULL, AspectMask::valueOf),
                levelEnabled = bool(18, true),
                locationTagging = bool(19, false),
                stripExifOnExport = bool(20, true),
                audioZoomEnabled = bool(21, false),
                lutId = f[22].ifEmpty { null },
                proControls = pro,
            ),
        )
    }.getOrNull()
}

@Singleton
class PresetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object K {
        val SET = stringSetPreferencesKey("preset_set")
    }

    val presets: Flow<List<Preset>> = context.presetStore.data.map { p ->
        (p[K.SET] ?: emptySet()).mapNotNull { PresetCodec.decode(it) }.sortedBy { it.name }
    }

    suspend fun save(name: String, settings: CameraSettings) {
        val clean = name.replace('|', ' ').trim().take(40)
        if (clean.isEmpty()) return
        context.presetStore.edit { e ->
            val cur = (e[K.SET] ?: emptySet()).filterNot {
                PresetCodec.decode(it)?.name.equals(clean, ignoreCase = true)
            }.toMutableSet()
            cur += PresetCodec.encode(clean, settings)
            e[K.SET] = cur
        }
    }

    suspend fun delete(name: String) {
        context.presetStore.edit { e ->
            e[K.SET] = (e[K.SET] ?: emptySet()).filterNot {
                PresetCodec.decode(it)?.name == name
            }.toSet()
        }
    }
}
