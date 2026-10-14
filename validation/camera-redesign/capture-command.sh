#!/usr/bin/env bash
set -euo pipefail
cd /home/husain/Documents/Works/Github/cctv-v380
# Run only after the owner unlocks the connected phone. No security setting changes.
adb -s 3d97bee3 shell am instrument -w -r -e class dev.cctv.v380.CameraRedesignUiTest,dev.cctv.v380.UiTest dev.cctv.v380.test/androidx.test.runner.AndroidJUnitRunner > validation/camera-redesign/native-ui.log 2>&1
mkdir -p validation/camera-redesign/review
adb -s 3d97bee3 pull /sdcard/Android/data/dev.cctv.v380/files/camera-redesign validation/camera-redesign/review/
