#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="com.cortana.hologram"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
MODEL_DIR="/sdcard/Android/data/${PACKAGE_NAME}/files/models"

echo "=== [1/5] Building Cortana Holographic Overlay Project ==="
./gradlew assembleDebug

echo "=== [2/5] Checking Connected ADB Device ==="
adb devices | grep -v "List" | grep "device" || { echo "No ADB device detected!"; exit 1; }

echo "=== [3/5] Syncing Local Models to Device Storage ==="
adb shell mkdir -p "${MODEL_DIR}"
if [ -f "./assets/models/whisper-tiny.bin" ]; then
    adb push ./assets/models/whisper-tiny.bin "${MODEL_DIR}/"
fi
if [ -f "./assets/models/gemma-2b-it-cpu-int4.bin" ]; then
    adb push ./assets/models/gemma-2b-it-cpu-int4.bin "${MODEL_DIR}/"
fi

echo "=== [4/5] Installing APK ==="
adb install -r -g "${APK_PATH}"

echo "=== [5/5] Granting Overlay & Audio Permissions ==="
adb shell pm grant "${PACKAGE_NAME}" android.permission.RECORD_AUDIO
adb shell appops set "${PACKAGE_NAME}" SYSTEM_ALERT_WINDOW allow

echo "=== Launching Service ==="
adb shell am startforegroundservice -n "${PACKAGE_NAME}/.HolographicOverlayService"

echo "=== Deployment Complete: Cortana Active ==="
