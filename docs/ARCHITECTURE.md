# Architecture — NovaCamera

## Pattern: MVVM + MVI + Clean Architecture

```
UI (Compose) ──Intent──▶ CameraViewModel ──use case──▶ CameraEngine / Repos
     ▲                        │ StateFlow<CameraUiState>
     └──────── render ─────────┘
```

- **presentation/**: Compose screens + `CameraContract` (Intent/State) + `CameraViewModel`. Single source of truth, lifecycle-aware.
- **domain/**: `CameraSettings`, `CameraState`, `MediaItem`, use cases (`CapturePhotoUseCase`, …). No Android deps except Uri.
- **data/**: `MediaStoreDataSource` (scoped storage), `SettingsDataStore` (prefs), `MediaRepositoryImpl`.
- **core/camera**: `CameraEngine` interface; `CameraXEngine` primary; `Camera2ProController` manual/RAW bridge; `LowLatencyCaptureHandler` bracketing/burst; `ConcurrentCameraManager` dual-cam.
- **processing/ml/media/security**: GPU filters, ML Kit analyzers, video/audio, vault/EXIF.

## Threading

- `Dispatchers.IO` for MediaStore/PDF/crypto; `Default` for stacking/fusion; Main for Compose state. `DispatcherProvider` injectable for tests.

## Lifecycle & stability

- `bindToLifecycle(owner, …)` per settings generation; `unbindAll()` in `onCleared`. Preview survives rotation via `configChanges` + retained ViewModel.
- `ThermalMonitor` (PowerManager listener) → `thermalThrottled` flag → ViewModel disables ML analyzers/high-FPS.

## Future multi-module split

```
:core:camera :core:common :core:processing
:feature:capture :feature:gallery :feature:vault :feature:settings
:data :domain :ml
```
Keep `CameraEngine` interface stable; modules communicate via domain models only.
