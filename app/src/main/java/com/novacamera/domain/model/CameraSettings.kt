package com.novacamera.domain.model

/** All capture modes supported by NovaCamera. */
enum class CaptureMode {
    PHOTO,
    BURST,
    PORTRAIT,
    NIGHT,
    HDR,
    DOCUMENT,
    VIDEO,
    SLOW_MOTION,
    TIMELAPSE,
    ASTRO,
    DUAL_CAM,
}

/** Lens facing selection. */
enum class LensFacing { BACK, FRONT, EXTERNAL }

/** Flash / torch behavior. */
enum class FlashMode { OFF, ON, AUTO, TORCH }

/** Stabilization pipeline selection. */
enum class StabilizationMode { OFF, EIS, OIS_PLUS_EIS }

/** Output container for stills. */
enum class PhotoFormat { JPEG, HEIF, RAW_DNG, RAW_PLUS_JPEG }

/** AI scene hints driving auto-tuning. */
enum class SceneType {
    UNKNOWN, LANDSCAPE, PORTRAIT, FOOD, SUNSET, PETS,
    NIGHT_SKY, DOCUMENT, MACRO, SPORTS, BACKLIT,
}

/** Manual pro-control snapshot (DSLR-like). Null = auto. */
data class ProControls(
    val manualFocusDistance: Float? = null, // 0f (infinity) .. 1f (macro), null = AF
    val focusPeakingEnabled: Boolean = false,
    val iso: Int? = null,
    val isoRange: IntRange = 100..3200,
    val shutterSpeedSec: Double? = null, // e.g. 1/8000 .. 30.0
    val whiteBalanceKelvin: Int? = null, // 2000..10000, null = AWB
    val exposureCompensationEv: Float = 0f, // -3.0 .. +3.0
    val zebraEnabled: Boolean = false,
    val histogramEnabled: Boolean = false,
    val rawEnabled: Boolean = false,
)

/** Aggregate camera settings persisted via DataStore. */
data class CameraSettings(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: LensFacing = LensFacing.BACK,
    val flashMode: FlashMode = FlashMode.OFF,
    val stabilization: StabilizationMode = StabilizationMode.OIS_PLUS_EIS,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val videoQuality: VideoQuality = VideoQuality.UHD_4K_30,
    val slowMotionFps: Int = 240,
    val timelapseIntervalMs: Long = 2_000L,
    val zoomRatio: Float = 1f,
    val bokehStrength: Float = 0.5f, // portrait blur 0..1
    val gridEnabled: Boolean = true,
    val locationTagging: Boolean = false, // privacy default OFF
    val stripExifOnExport: Boolean = true,
    val audioZoomEnabled: Boolean = false,
    val lutId: String? = null,
    val proControls: ProControls = ProControls(),
)

enum class VideoQuality(val width: Int, val height: Int, val fps: Int) {
    FHD_30(1920, 1080, 30),
    FHD_60(1920, 1080, 60),
    UHD_4K_30(3840, 2160, 30),
    UHD_4K_60(3840, 2160, 60),
    UHD_8K_30(7680, 4320, 30),
}
