#!/usr/bin/env bash
# Idempotent Cloud Agent setup for the Calle Android project.
# Installs the Android SDK packages the Gradle build needs and points the
# project at them via local.properties. Safe to run repeatedly.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="15859902"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools into $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  tmp_dir="$(mktemp -d)"
  curl -fsSL -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp_zip" -d "$tmp_dir"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp_zip" "$tmp_dir"
fi

echo "Accepting SDK licenses"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

echo "Installing SDK packages (platform 35, build-tools 35.0.0, platform-tools)"
"$SDKMANAGER" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" >/dev/null

# Point the Gradle build at the SDK. local.properties is git-ignored.
LOCAL_PROPS="$REPO_ROOT/local.properties"
if ! grep -qs "^sdk.dir=$ANDROID_HOME$" "$LOCAL_PROPS" 2>/dev/null; then
  echo "sdk.dir=$ANDROID_HOME" > "$LOCAL_PROPS"
fi

echo "Android SDK ready at $ANDROID_HOME"
