package com.novacamera.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageCapture
import com.novacamera.domain.model.ProControls
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera2 fallback / pro-control bridge.
 * Applies DSLR-like manual settings through Camera2Interop without
 * abandoning the CameraX lifecycle (preview stays bound).
 * RAW (DNG) path uses a dedicated Camera2 session — see [captureRawDng].
 */
@Singleton
class Camera2ProController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cameraManager: CameraManager? =
        context.getSystemService(CameraManager::class.java)

    fun isFullManualSupported(): Boolean {
        val ids = runCatching { cameraManager?.cameraIdList }.getOrNull() ?: return false
        return ids.any { id ->
            val caps = cameraManager?.getCameraCharacteristics(id)
            val level = caps?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        }
    }

    fun isoRange(): IntRange {
        val id = cameraManager?.cameraIdList?.firstOrNull() ?: return 100..3200
        val chars = cameraManager?.getCameraCharacteristics(id) ?: return 100..3200
        val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        return if (range != null) range.lower..range.upper else 100..3200
    }

    fun shutterRangeSec(): ClosedRange<Double> {
        val id = cameraManager?.cameraIdList?.firstOrNull() ?: return (1.0 / 8000)..30.0
        val chars = cameraManager?.getCameraCharacteristics(id) ?: return (1.0 / 8000)..30.0
        val ns = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        return if (ns != null) (ns.lower / 1e9)..(ns.upper / 1e9.coerceAtMost(30e9)) else (1.0 / 8000)..30.0
    }

    /** Applies manual CaptureRequest options to the builder BEFORE build(). */
    fun applyManualSettings(builder: ImageCapture.Builder, pro: ProControls) {
        // Camera2Interop.Extender operates on the use-case Builder at bind
        // time, so the CameraX session keeps its lifecycle (preview stays bound).
        val extender = Camera2Interop.Extender(builder)

        // Manual focus distance (0 = infinity … 1 = macro → diopters mapping
        // is lens-specific; 0f focus distance = infinity in Camera2).
        pro.manualFocusDistance?.let {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            extender.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, it * 10f)
        }

        pro.iso?.let { iso ->
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF,
            )
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        }

        pro.shutterSpeedSec?.let { sec ->
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF,
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME, (sec * 1e9).toLong(),
            )
        }

        pro.whiteBalanceKelvin?.let {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF,
            )
            // Rough Kelvin → RGGB gains (approx; calibrated per-device via
            // Custom WB routine in ProControlPanel).
            val (r, b) = kelvinToGains(it)
            val gains = android.hardware.camera2.params.RggbChannelVector(r, 1f, 1f, b)
            extender.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            extender.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
        }
    }

    private fun kelvinToGains(k: Int): Pair<Float, Float> {
        val t = (k / 100f)
        val r = if (t <= 66) 255f else (329.698727446f * Math.pow((t - 60).toDouble(), -0.1332047592)).toFloat()
        val b = if (t >= 66) 255f else (138.5177312231f * Math.log(t.toDouble() - 10) - 305.0447927307f).toFloat()
        return (r / 255f * 2f).coerceIn(1f, 4f) to (b / 255f * 2f).coerceIn(1f, 4f)
    }
}
