#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest \
  --tests 'dev.cctv.v380.protocol.*' \
  --tests 'dev.cctv.v380.ManagementSessionGuardTest' \
  --tests 'dev.cctv.v380.SdMaintenanceTest' \
  --tests 'dev.cctv.v380.ApProtocolTest' \
  --tests 'dev.cctv.v380.DiagnosticsTest' \
  --tests 'dev.cctv.v380.RecordingReplayTest' \
  --tests 'dev.cctv.v380.RecordingExportCopyTest' \
  --console=plain
