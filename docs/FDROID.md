# F-Droid submission guide

The `foss` product flavor exists for exactly this: zero Google libraries
(no ML Kit, no Play references), so it passes F-Droid's free-software rules.
The standard `gms` flavor can never be listed (proprietary ML Kit clients).

> I can't file the listing for you — inclusion happens through a merge
> request to F-Droid's own data repo from your account. Everything below
> is prepared so that request is copy-paste.

## Preconditions (already true in this repo)

- License: MIT (`LICENSE`)
- `minSdk 24`, monotonic `versionCode` per release
- No ads, no tracking, no proprietary deps in `foss` (verify: `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i google` should show nothing from `com.google.android.gms` / `com.google.mlkit`)
- Store metadata ready: `fastlane/metadata/android/en-US/` (title, descriptions, icon, changelog)

## 1. Tag a release

F-Droid builds from tags. Tag the commit you want listed (e.g. `v1.2.1`)
and push the tag.

## 2. File the inclusion request

1. Account at https://gitlab.com, fork https://gitlab.com/fdroid/fdroiddata
2. Add `metadata/com.novacamera.yml` (draft — adjust after their review):

```yaml
Categories:
  - Multimedia
License: MIT
SourceCode: https://github.com/yubiiixtreme/nova-camera
IssueTracker: https://github.com/yubiiixtreme/nova-camera/issues

AutoName: NovaCamera
Summary: Deliberate camera with manual controls and notes scanner
Description: |-
  Full manual exposure, bracketing, timelapse, live telemetry overlays,
  notes scan-to-PDF, and an encrypted private vault. The FOSS build
  excludes all Google libraries.

RepoType: git
Repo: https://github.com/yubiiixtreme/nova-camera.git

Builds:
  - versionName: 1.2.1
    versionCode: 4
    commit: v1.2.1
    subdir: .
    gradle:
      - foss
    prebuild: echo sdk.dir=$ANDROID_HOME > local.properties

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
```

3. Open the merge request against `fdroid/fdroiddata`. Their CI (`fdroid lint`,
   `fdroid build`) will verify the recipe; reviewers may ask for tweaks
   (common ones: prebuilt-jar notes for `gradle-wrapper.jar`, which is the
   standard Gradle bootstrap most apps ship).

## 3. After acceptance

- F-Droid signs and publishes its own build; updates flow automatically
  via `AutoUpdateMode` as long as tags stay `v<versionName>` matching
  `versionCode` bumps.
- Keep the `foss` flavor Google-free: any new dependency must be checked
  with the `./gradlew :app:dependencies` command above.

## Self-hosted alternative (no review wait)

If you want downloads today without F-Droid's queue, publish the
`foss` APKs from GitHub Releases (already done per release) and point
users at them, or run `fdroid server` yourself for an F-Droid-compatible
repo under your own signing key.
