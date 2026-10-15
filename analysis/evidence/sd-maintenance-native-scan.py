"""Static public ELF symbol metadata only. Never load or execute target libraries."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

apk = Path(sys.argv[1])
output = Path(sys.argv[2])
libraries = []
names = ("libhsMediaLibrary.so", "libhsUtils.so", "libdevInfo.so", "libcommonV2DevInfo.so")
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="v380-sd-symbols-") as temp:
    for name in names:
        member = "lib/arm64-v8a/" + name
        source = archive.read(member)
        path = Path(temp) / name
        path.write_bytes(source)
        symbols = subprocess.check_output(["readelf", "--dyn-syms", "--wide", str(path)], text=True)
        selected = [line.strip() for line in symbols.splitlines() if any(
            token in line.lower() for token in ("sdcard", "tfcard", "format", "delete", "recfile", "recordfile"))]
        libraries.append({"library": member, "sha256": hashlib.sha256(source).hexdigest(), "selected_symbols": selected})
output.write_text(json.dumps({
    "kind": "supplementary static ELF symbols; not a call graph, decompilation or runtime proof",
    "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "selection": "SD/TF card, format, delete, recfile and recordfile symbol substrings; case insensitive",
    "limitations": "Absence of named symbols does not exclude stripped, obfuscated or inlined commands. No wire or terminal enum can be inferred from symbol names alone.",
    "libraries": libraries,
}, indent=2) + "\n")
