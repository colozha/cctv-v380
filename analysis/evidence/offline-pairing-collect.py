"""Bounded, read-only REA pass. Canonical output stays in ignored references."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

APK = Path(sys.argv[1])
ROOT = Path(__file__).resolve().parents[2]
QUERIES = [
    ("BindSender", "com.macrovideo.v380.fragments.AddDeviceApFragment.AnonymousClass35", "run", None),
    ("AddDeviceApFragment", "com.macrovideo.v380.fragments.AddDeviceApFragment", "getBindToken", None),
    ("AddDeviceApFragment", "com.macrovideo.v380.fragments.AddDeviceApFragment", "sendPinCode", None),
    ("AddDeviceApFragment", "com.macrovideo.v380.fragments.AddDeviceApFragment", "broadcastPinCode", None),
    ("QRContentUtils", "com.macrovideo.sdk.tools.QRContentUtils", "generateConfigNetQRContent", None),
    ("LoginHelper", "com.macrovideo.sdk.media.LoginHelper", "requestBindIotDeviceByAP", None),
    ("LoginHelper", "com.macrovideo.sdk.media.LoginHelper", "keyExchangeByLanSync", None),
    ("DeviceConfigSetting", "com.macrovideo.sdk.setting.DeviceConfigSetting", "startActionNewPlatform", 0),
]
FIELDS = {"bind_token", "action", "fastpin", "pincode", "bindinfo", "clicode", "timeout", "mode", "ssid", "pwd", "act", "verify", "cKM", "cS", "tz", "ts", "dK", "dS", "sid", "perm", "origin", "id", "chn", "method_id", "result", "code", "data"}

def sanitize(value):
    if isinstance(value, dict):
        return {k: sanitize(v) for k, v in value.items()}
    if isinstance(value, list):
        return [sanitize(v) for v in value]
    if isinstance(value, str):
        # Preserve field names only; omit all other Java string literals, including logs/defaults.
        return re.sub(r'"(?:[^"\\]|\\.)*"', lambda m: m[0] if m[0][1:-1] in FIELDS else '"<redacted>"', value)
    return value

def collect(query):
    short, cls, method, overload = query
    cmd = ["rea", "inspect-android-method", str(APK), cls, method, "--format", "json"]
    if overload is not None:
        cmd += ["--overload-index", str(overload)]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=180,
                            env=dict(os.environ, REA_JADX_HEAP_MIB="2048"))
    name = f"offline-pairing-{short}-{method}"
    if result.returncode:
        local = ROOT / "analysis/references/offline-pairing-canonical"
        local.mkdir(parents=True, exist_ok=True)
        (local / (name + ".stderr")).write_text(result.stderr + result.stdout)
        return {"method": cls + "." + method, "status": "unavailable", "reason": "REA command failed; inspect canonical local stderr"}
    evidence = json.loads(result.stdout)
    local = ROOT / "analysis/references/offline-pairing-canonical"
    local.mkdir(parents=True, exist_ok=True)
    (local / (name + ".json")).write_text(result.stdout)
    if "evidence_id" not in evidence:
        return {"method": cls + "." + method, "status": "unavailable", "reason": "No canonical Evidence ID; overload or backend limitation"}
    projection = {"kind": "sanitized noncanonical REA projection", "source_evidence_id": evidence["evidence_id"],
                  "subject": evidence.get("subject"), "parameters": evidence.get("parameters"),
                  "normalized_result": sanitize(evidence.get("normalized_result")), "limitations": evidence.get("limitations")}
    dest = ROOT / "analysis/evidence" / (name + ".sanitized.json")
    dest.write_text(json.dumps(projection, indent=2) + "\n")
    return {"method": cls + "." + method, "status": "collected", "source_evidence_id": evidence["evidence_id"],
            "file": dest.name, "sha256": hashlib.sha256(dest.read_bytes()).hexdigest()}

selected = QUERIES if len(sys.argv) < 3 else [q for q in QUERIES if q[2] in sys.argv[2:]]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(collect, selected))
summary_path = ROOT / "analysis/evidence/offline-pairing-summary.json"
if len(sys.argv) >= 3 and summary_path.exists():
    previous = json.loads(summary_path.read_text())["queries"]
    replaced = {r["method"] for r in results}
    results = [r for r in previous if r["method"] not in replaced] + results
summary = {"apk_sha256": hashlib.sha256(APK.read_bytes()).hexdigest(), "scope": "Static REA only; no APK execution, camera or cloud connection", "queries": results}
(ROOT / "analysis/evidence/offline-pairing-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
for row in results:
    print(row["method"], row["status"], flush=True)
