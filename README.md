# NovaCamera — Advanced High-Performance Android Camera

MVVM + MVI (single `CameraUiState`) with Clean Architecture layers, CameraX primary engine with Camera2 fallback for manual/RAW, Jetpack Compose + Material 3 UI, Coroutines/Flow, Hilt DI, OpenGL ES 3.0 real-time shaders, MediaStore scoped storage, ML Kit, and Keystore-encrypted Private Vault.

- Min SDK 26 · Target/Compile 34 · Kotlin 1.9.24 · AGP 8.5.2 · Java 17

## Project layout

```
nova-camera/
├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/novacamera/
│       │   ├── NovaCameraApp.kt / MainActivity.kt
│       │   ├── core/common/      Result, DispatcherProvider, ThermalMonitor
│       │   ├── core/camera/      CameraEngine, CameraXEngine, Camera2ProController,
│       │   │                     LowLatencyCaptureHandler, BurstManager, ConcurrentCameraManager
│       │   ├── domain/model/     CameraSettings, CameraState, MediaItem
│       │   ├── domain/usecase/   CapturePhoto/Burst, Zoom, Torch
│       │   ├── data/             MediaStoreDataSource, SettingsDataStore, MediaRepository
│       │   ├── di/               AppModule (Hilt binds)
│       │   ├── ml/               SceneRecognizer, FrameAnalyzers, DocumentScanner, SubjectTracker
│       │   ├── processing/       RealtimeFilterRenderer (GLES 3.0), LutManager, Hdr/Night/Astro
│       │   ├── media/            VideoRecorderController, AudioController
│       │   ├── security/         VaultManager, BiometricGate, ExifStripper
│       │   ├── presentation/     CameraScreen + ViewModel (MVI), ProControlPanel,
│       │   │                     Histogram/Zebra/Peaking overlays, Gallery, Vault, Settings
│       │   └── util/             Permissions, haptics
│       └── res/ values/ xml/
├── docs/ARCHITECTURE.md
└── .github/workflows/android.yml
```

Multi-module split (`:core:camera`, `:feature:capture`, …) is documented in `docs/ARCHITECTURE.md`; the repo ships as a single `:app` module to stay package-ready out of the box.

## Critical components (implemented)

1. **CameraX init + Camera2 fallback** — `core/camera/CameraXEngine.kt`
   `ProcessCameraProvider.bindToLifecycle(...)` with ZSL (`CAPTURE_MODE_MINIMIZE_LATENCY`), adaptive `PreviewView`, Camera2Interop manual overrides via `Camera2ProController`.
2. **OpenGL ES 3.0 / RenderEffect pipeline** — `processing/FilterPipeline.kt`
   `RealtimeFilterRenderer : GLSurfaceView.Renderer` with LUT + exposure/bokeh uniforms; API 33+ can swap to `RenderEffect` for the lightweight path.
3. **Low-latency capture handlers** — `core/camera/LowLatencyCaptureHandler.kt`
   Single-shot ZSL, burst stacks (`BurstManager`), HDR bracketing (EV −2/0/+2 → `HdrMerger`), night stacking (`NightStacker`), async MediaStore `IS_PENDING` writes.

## Feature coverage

| Requirement | Where |
|---|---|
| Single-shot ZSL, Burst stack, Portrait bokeh slider, Night stacking, HDR fusion, Document scan + PDF | `CameraXEngine`, `LowLatencyCaptureHandler`, `HdrMerger`/`NightStacker`, `DocumentScanner` |
| 4K60/8K30 video, OIS+EIS, slow-mo 120/240, timelapse intervalometer, audio zoom + BT/USB mic, pause/resume single-file | `VideoRecorderController`, `AudioController` |
| Pro: focus peaking, ISO, shutter 1/8000–30s, WB 2000–10000K, EV ±3, RAW+DNG flag, histogram/zebra | `Camera2ProController`, `ProControlPanel`, `Overlays` |
| Dual-cam concurrent, AI scene tuning, astrophoto stacking, subject tracking, LUT filters, EXIF strip + encrypted vault | `ConcurrentCameraManager`, `SceneRecognizer`, `AstrophotoStacker`, `SubjectTracker`, `LutManager`, `ExifStripper`, `VaultManager` |
| Pinch zoom, double-tap switch, tap-to-focus, AF/AE lock, rotation-safe, haptics, floating shutter, TalkBack, volume-key shutter hook | `CameraScreen`, `Permissions`, `util/Extensions` |
| Lifecycle-aware bind/unbind, aspect-safe `PreviewView`, foldable reflow, thermal shed-load, async writes | `CameraXEngine`, `ThermalMonitor`, `MediaStoreDataSource` |

## Build & package

```bash
./scripts/setup-android-sdk.sh  # once per machine: platform-34 + build-tools 34.0.0
./gradlew :app:assembleDebug      # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # release AAB/APK (minify + shrink on)
./gradlew :app:testDebugUnitTest  # unit tests
```

Full guide (SDK, CI artifact download, signing, troubleshooting): `docs/BUILD_APK.md`.

Release signing: add `keystore.properties` (never commit) or configure Play App Signing; `proguard-rules.pro` keeps CameraX/ML Kit/Hilt.

## Permissions & privacy

Camera, mic, media-read, and optional location (default OFF). `stripExifOnExport` defaults ON. Vault files live in app-private storage, excluded from backup, AES256-GCM via Keystore + biometric gate.

## License

MIT — see `LICENSE`.
