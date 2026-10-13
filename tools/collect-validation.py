#!/usr/bin/env python3
"""Summarize existing build reports; this script does not run camera tests."""
import hashlib
import argparse
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
EXPECTED_VENDOR_SHA = "7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b"
os.chdir(ROOT)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--without-connection-tests", action="store_true",
                    help="Keep older runtime reports explicitly historical; do not claim final connection validation")
args = parser.parse_args()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def suites(pattern):
    reports = sorted(ROOT.glob(pattern))
    if not reports:
        raise SystemExit(f"Missing test reports: {pattern}")
    totals = Counter(tests=0, failures=0, errors=0, skipped=0)
    details = []
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in tuple(totals):
            totals[key] += int(suite.get(key, "0"))
        cases = [node for node in suite.iter() if node.tag.rsplit("}", 1)[-1] == "testcase"]
        details.append({
            "report": str(report.relative_to(ROOT)),
            "timestamp": suite.get("timestamp"),
            "cases": [f"{case.get('classname')}.{case.get('name')}" for case in cases],
        })
    return {**totals, "reports": details}


sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
signer = sdk / "build-tools/36.0.0/apksigner"
signature = subprocess.run([str(signer), "verify", "--verbose", str(APK)],
                           check=True, text=True, capture_output=True).stdout.strip()
lint_file = ROOT / "app/build/reports/lint-results-debug.xml"
lint = Counter(issue.get("severity") for issue in ET.parse(lint_file).getroot().findall("issue"))
with zipfile.ZipFile(APK) as archive:
    native_libraries = sorted(name for name in archive.namelist() if name.endswith(".so"))
vendor_libraries = {"libhsMediaLibrary.so", "libaes.so", "libmideaCodec.so", "libffavc.so"}
assert not any(Path(name).name in vendor_libraries for name in native_libraries)
vendor_apk = Path("/home/husain/Downloads/v380-6-4-90.apk")
vendor_sha = digest(vendor_apk)
assert vendor_sha == EXPECTED_VENDOR_SHA, "Vendor APK hash changed"

def source_digest():
    paths = sorted([*Path("app/src").rglob("*"), Path("app/build.gradle.kts"),
                    Path("build.gradle.kts"), Path("settings.gradle.kts")])
    h = hashlib.sha256()
    for path in paths:
        if path.is_file():
            h.update(str(path).encode() + b"\0" + path.read_bytes() + b"\0")
    return h.hexdigest()


source_sha = source_digest()
observation_path = ROOT / "validation/user-stream-observation.json"
observation = json.loads(observation_path.read_text()) if observation_path.exists() else {}
runtimes = []
for runtime_file in sorted((ROOT / "validation").glob("runtime-api*/runtime.json")):
    runtime = json.loads(runtime_file.read_text())
    runtime["source_matches_final"] = runtime["source_sha256"] == source_sha
    if not args.without_connection_tests:
        assert runtime["source_matches_final"], f"Stale runtime reports: {runtime_file.parent}"
    runtime["status"] = "passed_final_source" if runtime["source_matches_final"] else "historical_pass_only"
    runtime["tests"] = suites(str(runtime_file.parent.relative_to(ROOT)) + "/TEST-*.xml")
    runtime["report_metadata"] = str(runtime_file.relative_to(ROOT))
    runtimes.append(runtime)
assert runtimes, "Run tools/run-android-validation.sh first"

result = {
    "collected_at_utc": datetime.now(timezone.utc).isoformat(),
    "scope": "Final offline build/parser/cipher tests; prior emulator tests identified separately; no camera acceptance",
    "source_sha256": source_sha,
    "build_command": "See validation/offline-build-command.txt; selected pure JVM cases only",
    "runtime_command": "ANDROID_SERIAL=<serial> tools/run-android-validation.sh",
    "jvm": suites("app/build/test-results/testDebugUnitTest/TEST-*.xml"),
    "android_runtimes": runtimes,
    "connection_testing": {"deferred_by_user": args.without_connection_tests,
                           "final_connection_tests_run": not args.without_connection_tests,
                           "latest_iot_session_attempt_passed": False,
                           "latest_attempt": "Synthetic IoT integration attempt failed at ActivityScenario teardown (PAUSED, expected DESTROYED); not rerun after user deferred connection testing",
                           "record": "validation/connection-tests-deferred/status.json",
                           "api35_tests_run": False},
    "lint": {"errors": lint["Error"] + lint["Fatal"], "warnings": lint["Warning"],
             "hints": lint["Hint"] + lint["Information"], "report": str(lint_file.relative_to(ROOT)),
             "issues": dict(Counter(issue.get("id") for issue in ET.parse(lint_file).getroot().findall("issue")))},
    "apk": {"path": str(APK.relative_to(ROOT)), "bytes": APK.stat().st_size,
            "version_name": re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / "app/build.gradle.kts").read_text()).group(1),
            "sha256": digest(APK), "signature_verification": signature,
            "native_libraries": native_libraries, "proprietary_vendor_libraries_present": False},
    "vendor_apk_sha256_after_work": vendor_sha,
    "protocol_gate": {"camera_acceptance_passed": False,
                      "debug_experimental_wire_profiles": ["legacy-v2", "iot-lan"],
                      "release_verified_profiles": [],
                      "reported_firmware": "Hw_EV2X_NA1_XHR_V1.0_WF_20250804",
                      "agent_camera_auth_video_ptz_tested": False,
                      "user_reported_local_auth_and_video": observation.get("user_reported_local_login_and_video", False),
                      "user_observation_reference": "validation/user-stream-observation.json" if observation else None,
                      "fresh_iot_ap_pin_binding": "CloudRequired for the observed vendor flow; no offline substitute proved",
                      "references": ["analysis/legacy-wire-spec.md", "analysis/iot-lan-protocol.md", "analysis/ap-provisioning.md", "analysis/stream-disconnect.md"]},
    "screenshots": [str(path.relative_to(ROOT)) for path in sorted((ROOT / ".impeccable/review").glob("phone-*-v2.png"))],
    "screenshots_scope": "Historical UI captures; no final camera connectivity or live footage validation",
    "not_validated": ["agent-run camera tests; user has reported authentication/render but stable compatibility remains unverified",
                      "AP pairing and physical Wi-Fi provisioning three repetitions",
                      "camera first frame <=5s and 30 minute stream/memory/ANR",
                      "SD/HD and HEVC real camera stream", "PTZ motor directions and stop",
                      "network recovery, permission revocation and offline LAN on hardware",
                      "final APK connection tests and active PTZ cancellation at runtime",
                      "API35 runtime test suite; final IoT integration test pass"],
}
assert result["jvm"]["failures"] == result["jvm"]["errors"] == result["jvm"]["skipped"] == 0
assert all(runtime["tests"]["failures"] == runtime["tests"]["errors"] == runtime["tests"]["skipped"] == 0
           for runtime in runtimes)
assert result["lint"]["errors"] == 0
output = ROOT / "validation/results.json"
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
print(f"{output.relative_to(ROOT)}: {result['jvm']['tests']} JVM; "
      + ", ".join(f"API{r['api']}: {r['tests']['tests']} Android ({r['status']})" for r in runtimes)
      + "; camera acceptance pending")
