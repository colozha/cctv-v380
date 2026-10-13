#!/usr/bin/env bash
# Preserve each successful runtime report before Gradle replaces connected-test output.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${ANDROID_SERIAL:?Select an isolated emulator or test device with ANDROID_SERIAL}"
api=$("$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$api" =~ ^[0-9]+$ ]]
report_dir="validation/runtime-api$api"
mkdir -p "$report_dir"
./gradlew :app:connectedDebugAndroidTest --console=plain > "$report_dir/build.log" 2>&1
cp app/build/outputs/androidTest-results/connected/debug/TEST-*.xml "$report_dir/"
python3 - "$api" "$ANDROID_SERIAL" <<'PY'
import hashlib,json,sys
from pathlib import Path
paths=sorted([*Path('app/src').rglob('*'),Path('app/build.gradle.kts'),Path('build.gradle.kts'),Path('settings.gradle.kts')])
h=hashlib.sha256()
for p in paths:
    if p.is_file(): h.update(str(p).encode()+b'\0'+p.read_bytes()+b'\0')
Path(f'validation/runtime-api{sys.argv[1]}/runtime.json').write_text(json.dumps({
    'api':int(sys.argv[1]),'serial':sys.argv[2],'physical_device':not sys.argv[2].startswith('emulator-'),
    'source_sha256':h.hexdigest(),'scope':'synthetic vectors, storage, Compose UI, loopback camera session and rendered AVC; Wi-Fi/AP radio and real camera not tested'
},indent=2)+'\n')
PY
cat "$report_dir/build.log" | tail -8
