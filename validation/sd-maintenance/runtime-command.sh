#!/usr/bin/env bash
# Prepared, not run: API37 emulator failed boot with SIGSEGV, with and without KVM.
# No sockets, physical cameras, or destructive camera commands in this selection.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_SERIAL:?Select an isolated booted emulator}"
./gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.cctv.v380.SdMaintenanceUiTest,dev.cctv.v380.FeatureStorageAndExportTest,dev.cctv.v380.RecordingWorkflowTest \
  --console=plain
