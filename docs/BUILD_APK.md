# Build & package NovaCamera APK

Prereqs: Java 17, ~1GB free for SDK + Gradle caches.

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
./gradlew :app:testDebugUnitTest
```

Report: `app/build/reports/tests/testDebugUnitTest/index.html`

## 3. Debug APK (installable)

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~195MB, ML Kit models bundled).

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Release AAB/APK

```bash
./gradlew :app:assembleRelease   # minify + shrink on
./gradlew :app:bundleRelease     # Play AAB
```

Signing: create `keystore.properties` (never commit, gitignored) or use Play App Signing.
`app/proguard-rules.pro` keeps CameraX / ML Kit / Hilt.

## 5. Download APK from CI

Push to `main` or open a PR → GitHub Actions runs `.github/workflows/android.yml`:
`assembleDebug` + `testDebugUnitTest`, uploads artifact `nova-camera-debug`
containing `app-debug.apk`. Download it from the Actions run page.

## Troubleshooting

- `./gradlew` fails with `Could not find or load main class "-Xmx64m"`: fixed by the real
  wrapper in `gradle/wrapper/` (don't use the old stub). Wrapper needs
  `networkTimeout=60000` on slow networks for the 130MB Gradle 8.7 download.
- `SDK location not found`: set `ANDROID_HOME` (see step 1).
- `SDK package not found` / license errors: re-run `./scripts/setup-android-sdk.sh`.
