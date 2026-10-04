# PR #1 — Fix camera binding, tap-to-focus, and EXIF privacy stripping

Branch: `fix/wire-camera-binding-and-exif-privacy`
PR: https://github.com/yubiiixtreme/nova-camera/pull/1

## What was found

A review of the codebase turned up one critical bug and two gaps between what
the README/code comments claimed was implemented and what actually ran.

### 1. The camera preview and capture never actually worked

`CameraScreen` (`presentation/camera/CameraScreen.kt`) created the
`PreviewView` and stored a reference to it, but nothing ever called
`CameraEngine.bind(lifecycleOwner, previewView, settings)`. The only place
that looked like it should do this was a `DisposableEffect` block whose body
was empty except for a comment: *"Simplified: binding happens through
AndroidView update block below"* — but the `update` block only reassigned the
`previewView` variable. `CameraViewModel` already held an injected
`CameraEngine` and even had an unused `CameraState.isBound` field, confirming
the binding call was planned but never wired up.

Net effect: the preview surface would stay blank and `takePhoto()` /
`takeBurst()` would fail with `IllegalStateException("Camera not bound")`
every time, in every build.

**Fix:** added `CameraViewModel.bindCamera(lifecycleOwner, previewView)`,
which calls `engine.bind(...)` and updates `CameraState.isBound` /
`error` on success/failure. `CameraScreen` now calls it from a
`LaunchedEffect` keyed on the preview view plus the settings that require a
rebind (lens facing, capture mode, video quality).

### 2. Tap-to-focus was documented but not wired to any gesture

The README lists "tap-to-focus" as implemented, and
`CameraEngine.tapToFocus(x, y)` / `CameraXEngine.tapToFocus` existed, but no
gesture in `CameraScreen` ever called it — only pinch-zoom and double-tap-to
switch-camera were wired. On top of that, `CameraXEngine.tapToFocus` built its
`SurfaceOrientedMeteringPointFactory` with size `(1f, 1f)`, which only makes
sense for normalized `0..1` input coordinates — but its own comment claimed
the caller passes raw view-relative pixels, which would have produced wildly
wrong focus points even if it had been called.

**Fix:** added an `onTap` handler to the existing `detectTapGestures` call in
`CameraScreen` that divides the tap offset by the pointer-input surface size
(giving normalized coordinates) and forwards it to a new
`CameraViewModel.onTapToFocus(xNorm, yNorm)`. Corrected the misleading
comment in `CameraXEngine`.

### 3. EXIF stripping left identifying metadata behind

`ExifStripper.strippedCopy()` is the "privacy" export path (strip EXIF before
sharing). It only cleared GPS lat/long/altitude and make/model/serial. It
still left behind: GPS timestamp/datestamp/processing method/area info,
lens make/model/serial, body serial number, camera owner name,
artist/copyright, image description, user comment, and all capture
date/time + UTC-offset tags — several of which are directly identifying
(owner name, serials) or indirectly identifying (precise capture time +
GPS timestamp can re-derive location via correlation).

It also silently wrote out a 0-byte temp file (and then ran EXIF stripping
on it without error) if `openInputStream` returned `null`.

**Fix:** expanded the stripped-tag list to cover all of the above, and made
the stream-copy step throw (`check(copied) { ... }`) instead of silently
continuing when the source can't be read.

## Files changed

- `app/src/main/java/com/novacamera/presentation/camera/CameraScreen.kt`
- `app/src/main/java/com/novacamera/presentation/camera/CameraViewModel.kt`
- `app/src/main/java/com/novacamera/core/camera/CameraXEngine.kt`
- `app/src/main/java/com/novacamera/security/ExifStripper.kt`

## Not done / known limitations

- Could not run a full Gradle build in the environment this was written in
  (no Android SDK, and the `gradlew` wrapper's JVM args are also broken —
  `Could not find or load main class "-Xmx64m"` — worth checking
  `gradle/wrapper/gradle-wrapper.properties` / `gradle.properties`
  `org.gradle.jvmargs` separately). Changes were reviewed by hand instead of
  compiled; please build and run through the manual test plan in the PR
  description before merging.
- The Private Vault screen (`VaultScreen.kt`) is still a stub: it has no file
  listing/gallery UI, and `VaultManager.openDecrypted()` has no caller. Not
  addressed here — it's a separate, larger feature gap rather than a bug in
  existing wiring.
