#!/usr/bin/env bash
# Prepared, not executed: both emulator boot attempts failed. Only local UI/storage fixtures.
set -euo pipefail
cd /home/husain/Documents/Works/Github/cctv-v380
export JAVA_HOME=/home/husain/.local/share/java/temurin-21
export ANDROID_HOME=/home/husain/Android/Sdk
: "${ANDROID_SERIAL:?Select an isolated emulator that has booted}"
./gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.cctv.v380.CameraManagementUiTest,dev.cctv.v380.FeatureStorageAndExportTest \
  --console=plain
