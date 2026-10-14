#!/usr/bin/env bash
# Prepared only. No boot/runtime retry while emulator infrastructure is unchanged.
# Only local UI, PixelCopy, storage and export fixtures; no socket/session tests.
set -euo pipefail
cd /home/husain/Documents/Works/Github/cctv-v380
export JAVA_HOME=/home/husain/.local/share/java/temurin-21
export ANDROID_HOME=/home/husain/Android/Sdk
: "${ANDROID_SERIAL:?Select an isolated emulator that has booted}"
./gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.cctv.v380.CameraAreaUiTest,dev.cctv.v380.CameraManagementUiTest,dev.cctv.v380.FeatureStorageAndExportTest \
  --console=plain
