package com.novacamera.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import com.novacamera.domain.model.ProControls
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manual sensor controls on top of the live CameraX session.
 *
 * Options are pushed through [Camera2CameraControl], so they take effect on
 * the running preview AND every capture (the old approach baked them into the
 * ImageCapture builder once, so sliders never changed anything live). All
 * capabilities are read from the camera that is actually bound, never from
 * "the first camera id", and unsupported controls are reported via [CameraCaps].
 */
@OptIn(ExperimentalCamera2Interop::class)
@Singleton
class Camera2ProController @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
) {
    /** Fills the manual-control fields of [base] from the bound camera's characteristics. */
    fun readCaps(camera: Camera, base: CameraCaps): CameraCaps {
        val info = runCatching { Camera2CameraInfo.from(camera.cameraInfo) }.getOrNull() ?: return base
        fun <T> ch(key: CameraCharacteristics.Key<T>): T? = runCatching { info.getCameraCharacteristic(key) }.getOrNull()

        val caps = ch(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val manualSensor = caps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val iso = ch(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exp = ch(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minFocus = ch(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val awb = ch(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)

        return base.copy(
            manualSensor = manualSensor && iso != null && exp != null,
            isoMin = iso?.lower ?: base.isoMin,
            isoMax = iso?.upper ?: base.isoMax,
            shutterMinSec = exp?.let { (it.lower / 1e9).coerceAtLeast(1.0 / 8000) } ?: base.shutterMinSec,
            // Preview freezes on very long exposures; cap the manual range at 1s.
            shutterMaxSec = exp?.let { (it.upper / 1e9).coerceAtMost(1.0) } ?: base.shutterMaxSec,
            manualFocus = minFocus > 0f,
            minFocusDiopters = minFocus,
            manualWhiteBalance = awb?.any { it in WB_PRESETS.map { p -> p.second } } == true,
        )
    }

    /** Pushes the full manual option set. Passing all-auto clears every override. */
    fun apply(camera: Camera, pro: ProControls, caps: CameraCaps) {
        val control = runCatching { Camera2CameraControl.from(camera.cameraControl) }.getOrNull() ?: return
        val b = CaptureRequestOptions.Builder()

        if (caps.manualSensor && (pro.iso != null || pro.shutterSpeedSec != null)) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                (pro.iso ?: 400).coerceIn(caps.isoMin, caps.isoMax),
            )
            val sec = (pro.shutterSpeedSec ?: (1.0 / 60)).coerceIn(caps.shutterMinSec, caps.shutterMaxSec)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, (sec * 1e9).toLong())
        }

        val focus = pro.manualFocusDistance
        if (caps.manualFocus && focus != null) {
            // 0 = infinity, 1 = closest focus; Camera2 wants diopters.
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus.coerceIn(0f, 1f) * caps.minFocusDiopters)
        }

        val kelvin = pro.whiteBalanceKelvin
        if (caps.manualWhiteBalance && kelvin != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, nearestWbPreset(kelvin))
        }

        control.setCaptureRequestOptions(b.build())
    }

    private fun nearestWbPreset(kelvin: Int): Int =
        WB_PRESETS.minBy { abs(it.first - kelvin) }.second

    private companion object {
        /** Approximate colour temperature of Camera2's AWB presets (widely supported, unlike raw gains). */
        val WB_PRESETS = listOf(
            2700 to CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
            3300 to CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT,
            4200 to CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT,
            5200 to CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
            6500 to CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            7500 to CameraMetadata.CONTROL_AWB_MODE_SHADE,
        )
    }
}
