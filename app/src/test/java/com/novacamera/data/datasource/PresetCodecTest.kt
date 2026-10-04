package com.novacamera.data.datasource

import com.novacamera.domain.model.AspectMask
import com.novacamera.domain.model.CameraSettings
import com.novacamera.domain.model.CaptureMode
import com.novacamera.domain.model.FlashMode
import com.novacamera.domain.model.GridStyle
import com.novacamera.domain.model.LensFacing
import com.novacamera.domain.model.PhotoFormat
import com.novacamera.domain.model.ProControls
import com.novacamera.domain.model.VideoQuality
import org.junit.Assert.*
import org.junit.Test

class PresetCodecTest {
    private fun distinct() = CameraSettings(
        captureMode = CaptureMode.HDR,
        lensFacing = LensFacing.FRONT,
        flashMode = FlashMode.ON,
        photoFormat = PhotoFormat.HEIF,
        videoQuality = VideoQuality.UHD_4K_60,
        slowMotionFps = 120,
        timelapseIntervalMs = 5000L,
        timelapseShots = 30,
        burstShots = 20,
        hdrFrames = 5,
        hdrStepEv = 2,
        zoomRatio = 2.5f,
        bokehStrength = 0.8f,
        gridEnabled = false,
        gridStyle = GridStyle.GOLDEN,
        aspectMask = AspectMask.R11,
        levelEnabled = false,
        locationTagging = true,
        stripExifOnExport = false,
        audioZoomEnabled = true,
        lutId = "warm",
        proControls = ProControls(
            manualFocusDistance = 0.3f,
            focusPeakingEnabled = true,
            iso = 400,
            shutterSpeedSec = 1.0 / 250,
            whiteBalanceKelvin = 3200,
            exposureCompensationEv = -1.5f,
            zebraEnabled = true,
            histogramEnabled = true,
            rawEnabled = true,
        ),
    )

    @Test fun roundTripsDistinctSettings() {
        val s = distinct()
        val decoded = PresetCodec.decode(PresetCodec.encode("Street", s))
        assertNotNull(decoded)
        assertEquals("Street", decoded!!.name)
        assertEquals(s, decoded.settings)
    }

    @Test fun roundTripsDefaults() {
        val s = CameraSettings()
        val decoded = PresetCodec.decode(PresetCodec.encode("d", s))
        assertEquals(s, decoded!!.settings)
    }

    @Test fun rejectsGarbage() {
        assertNull(PresetCodec.decode("garbage"))
        assertNull(PresetCodec.decode("v1|only-name"))
        assertNull(PresetCodec.decode("v0|" + "x|".repeat(31)))
    }
}
