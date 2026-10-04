#!/usr/bin/env bash
# Setup Android SDK for NovaCamera (Linux).
# Installs cmdline-tools + platform-34 + build-tools 34.0.0 + platform-tools.
# Idempotent: skips downloads that already exist.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_VERSION="11076708"
CMDLINE_ZIP="commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/${CMDLINE_ZIP}"

echo "ANDROID_HOME=$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android cmdline-tools..."
  TMP_ZIP="$(mktemp --suffix=-cmdtools.zip)"
  curl -L -o "$TMP_ZIP" "$CMDLINE_URL"
  rm -rf "$ANDROID_HOME/cmdline-tools/cmdline-tools"
  unzip -q "$TMP_ZIP" -d "$ANDROID_HOME/cmdline-tools/"
  rm -f "$TMP_ZIP"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rmdir "$ANDROID_HOME/cmdline-tools/cmdline-tools" 2>/dev/null || true
else
  echo "cmdline-tools already installed, skipping download."
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Accepting licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

echo "Installing platform + build-tools + platform-tools..."
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"

echo ""
echo "Done. Installed:"
sdkmanager --list_installed | tail -n 10
echo ""
echo "Add to your shell (~/.bashrc / ~/.zshrc):"
echo "  export ANDROID_HOME=\$HOME/Android/Sdk"
echo "  export ANDROID_SDK_ROOT=\$HOME/Android/Sdk"
echo "  export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
echo ""
echo "Then build:"
echo "  ./gradlew :app:testDebugUnitTest"
echo "  ./gradlew :app:assembleDebug"
