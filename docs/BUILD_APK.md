# Build & package NovaCamera APK

Prereqs: Java 17, ~1GB free for SDK + Gradle caches.
Min SDK 24 (Android 7.0) · Target/Compile 34.

## 1. Install the Android SDK (once per machine)

```bash
./scripts/setup-android-sdk.sh
```

This installs to `~/Android/Sdk`:
- `cmdline-tools;latest`
- `platforms;android-34`
- `build-tools;34.0.0`
- `platform-tools`

Then export (add to `~/.bashrc` / `~/.zshrc`):

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
```

No `local.properties` needed when `ANDROID_HOME` is set.

## 2. Tests

```bash
./gradlew :app:testGmsDebugUnitTest :app:testFossDebugUnitTest
```

Report: `app/build/reports/tests/testGmsDebugUnitTest/index.html`

## 3. APKs (per-ABI splits, per flavor)

Builds emit one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) —
pick the one matching the device (almost all modern phones: `arm64-v8a`).
Two flavors: `gms` (ML via Play Services) and `foss` (no Google libs).

```bash
./gradlew :app:assembleGmsDebug :app:assembleFossDebug     # installable (~20MB/ABI gms)
./gradlew :app:assembleGmsRelease :app:assembleFossRelease # minified (~3MB/ABI, unsigned)
adb install -r app/build/outputs/apk/gmsDebug/app-gms-arm64-v8a-debug.apk
```

### Why so small? (195MB -> ~3MB)

| Build | Size | What changed |
|---|---|---|
| v1.0.0 debug (fat) | ~195MB | bundled ML Kit models + 4 ABIs + no shrink |
| debug per-ABI | ~20MB | ABI splits |
| release per-ABI | ~3.3MB | + R8 minify/shrink, + Play thin ML Kit clients |

Size wins come from:
- **ML Kit thin clients** (`play-services-mlkit-*`): OCR/barcode/face models
  download via Google Play instead of shipping in the APK. Pose detection
  (biggest native lib, no wired feature) was removed.
- **ABI splits**: native libs shipped 4x; each APK carries one ABI.
- **R8 + resource shrinking** on release builds.
- Devices without Google Play Services lose OCR/barcode/face ML features;
  camera, vault, and PDF scan-warp keep working.

## 4. Release signing

Release APKs from `assemble*Release` are **unsigned**. For sideload previews,
signing with the debug key is enough:

```bash
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out nova-camera.apk app-arm64-v8a-release-unsigned.apk
```

For the Play Store: create `keystore.properties` (never commit, gitignored)
or use Play App Signing, and prefer `./gradlew :app:bundleGmsRelease` (AAB).
`app/proguard-rules.pro` keeps CameraX / ML Kit / Hilt.

## 5. Download APK from CI / Releases

- Push to `main` or open a PR → GitHub Actions runs `.github/workflows/android.yml`:
  `assembleGmsDebug`/`assembleFossDebug` + unit tests, uploads artifact `nova-camera-debug`.
- Stable builds live under GitHub **Releases** (e.g. `nova-camera-v1.1.0-arm64-v8a.apk`).

## Troubleshooting

- `./gradlew` fails with `Could not find or load main class "-Xmx64m"`: fixed by the real
  wrapper in `gradle/wrapper/` (don't use the old stub). Wrapper needs
  `networkTimeout=60000` on slow networks for the 130MB Gradle 8.7 download.
- `SDK location not found`: set `ANDROID_HOME` (see step 1).
- `SDK package not found` / license errors: re-run `./scripts/setup-android-sdk.sh`.
- OCR shows "needs Google Play Services": update Play Services on the device.
