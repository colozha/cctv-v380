"""Offline format-result tracing; never execute the APK or contact a camera.

Usage: python3 analysis/evidence/sd-format-completion-collect.py APK JADX_JAR
Requires rea, javac and java. Raw REA evidence stays in ignored references.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
APK, JAR = (Path(arg).resolve() for arg in sys.argv[1:])
OUT = ROOT / "analysis/evidence"
LOCAL = ROOT / "analysis/references/sd-format-completion-canonical"
LOCAL.mkdir(parents=True, exist_ok=True)
PREFIX = "sd-format-completion-"
QUERIES = [
    ("thread2", "com.macrovideo.v380.fragments.DeviceConfigSettingDeviceRecordFragment2.FormatSDCardThread", "run"),
    ("record-handler", "com.macrovideo.v380.fragments.DeviceConfigSettingDeviceRecordFragment", "handleMessage"),
    ("home-handler", "com.macrovideo.v380.fragments.DeviceConfigSettingHomePageFragment", "handleMessage"),
]
DEX_QUERIES = [
    ("thread2", "Lcom/macrovideo/v380/fragments/DeviceConfigSettingDeviceRecordFragment2$FormatSDCardThread;", "run", None),
    ("thread2-result", "Lcom/macrovideo/v380/fragments/DeviceConfigSettingDeviceRecordFragment2$FormatSDCardThread$2;", "run", None),
    ("record-handler", "Lcom/macrovideo/v380/fragments/DeviceConfigSettingDeviceRecordFragment;", "handleMessage", (0x40F, 0x45C)),
    ("home-handler", "Lcom/macrovideo/v380/fragments/DeviceConfigSettingHomePageFragment;", "handleMessage", (0x4CF, 0x507)),
]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command):
    return subprocess.run(command, check=True, capture_output=True, text=True,
                          timeout=180, env=dict(os.environ, REA_JADX_HEAP_MIB="2048")).stdout


files = {}
queries = []
for name, cls, method in QUERIES:
    command = ["rea", "inspect-android-method", str(APK), cls, method, "--format", "json"]
    raw = run(command)
    (LOCAL / (name + ".json")).write_text(raw)
    evidence = json.loads(raw)
    normalized = evidence["normalized_result"]
    source = normalized["source"]["text"]
    # Keep only format-result branches for the large handlers; DEX confirms their targets.
    if name == "record-handler":
        source = source[:source.index("        if (i == 80)")]
    elif name == "home-handler":
        source = source[:source.index("        if (i == 2)")]
    source = re.sub(r'"(?:[^"\\]|\\.)*"', '"<redacted>"', source)
    projection = {
        "kind": "sanitized noncanonical REA projection; static analysis only",
        "source_evidence_id": evidence["evidence_id"],
        "subject": evidence["subject"],
        "parameters": evidence["parameters"],
        "engine": normalized.get("engine"),
        "method": normalized["method"],
        "source_excerpt": source,
        "limitations": ["All Java string literals redacted; handler excerpts contain only the format-result branch.",
                        "Decompiler output requires DEX cross-check; UI success does not prove physical completion."],
    }
    path = OUT / (PREFIX + name + ".sanitized.json")
    path.write_text(json.dumps(projection, indent=2) + "\n")
    files[path.name] = digest(path)
    queries.append({"command": command, "source_evidence_id": evidence["evidence_id"], "file": path.name})
    print(name + ": REA collected", flush=True)

with tempfile.TemporaryDirectory(prefix="v380-format-dex-") as classes:
    run(["javac", "-cp", str(JAR), "-d", classes, str(OUT / "ap-dex-instructions.java")])
    for name, descriptor, method, bounds in DEX_QUERIES:
        command = ["java", "-Xmx768m", "-cp", classes + os.pathsep + str(JAR),
                   "ApDex", str(APK), descriptor, method]
        lines = run(command).splitlines()
        if not lines:
            raise ValueError("Missing DEX method: " + descriptor + "->" + method)
        if bounds:
            lines = [line for line in lines if line.startswith("DEX ") or
                     bounds[0] <= int(line.split()[0], 16) < bounds[1]]
        path = OUT / (PREFIX + name + ".dex.txt")
        path.write_text("\n".join(lines) + "\n")
        files[path.name] = digest(path)
        queries.append({"descriptor": descriptor, "method": method,
                        "code_unit_range": bounds, "file": path.name})
        print(name + ": DEX collected", flush=True)

summary = {
    "date": "2026-10-16", "kind": "offline static format-result tracing",
    "apk_sha256": digest(APK), "jadx_jar_sha256": digest(JAR),
    "collector_sha256": digest(Path(__file__)),
    "dex_dumper_sha256": digest(OUT / "ap-dex-instructions.java"),
    "reproduction": ["python3", "analysis/evidence/sd-format-completion-collect.py", str(APK), str(JAR)],
    "queries": queries, "files": files,
    "finding": "Vendor UI success follows setSDCardFormat result 1001; the existing IoT action maps result 256 to 1001. These branches provide no terminal readback or operation correlation.",
    "activation": "blocked: terminal contract still unproven; FORMAT_STATUS_VERIFIED remains false",
    "limitations": ["No APK/native library execution, camera connection or format operation.",
                    "Bounded paths, not a complete native call graph or proof of absence across firmware.",
                    "No new terminal enum is inferred from UI labels, ACK or the local record-state assignment."],
}
(OUT / (PREFIX + "summary.json")).write_text(json.dumps(summary, indent=2) + "\n")
