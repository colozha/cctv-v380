"""Build the offline oracle with hash-pinned local reference libraries. No device access."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

apk, sdk = map(Path, sys.argv[1:])
assert hashlib.sha256(apk.read_bytes()).hexdigest() == "7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b", "Unexpected reference APK"
root = Path(__file__).resolve().parent
out = root / "build/oracle-libs/arm64-v8a"
out.mkdir(parents=True, exist_ok=True)
hashes = {}
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
            content = archive.read(name)
            hashes[name] = hashlib.sha256(content).hexdigest()
            (out / Path(name).name).write_bytes(content)
assert hashes["lib/arm64-v8a/libhsMediaLibrary.so"] == "1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc"
assert hashes["lib/arm64-v8a/libmideaCodec.so"] == "43938dcd760acde7dd31b93ec1c132d2936137f3d9d38d6261e709b33c6717df"
compiler = sdk / "ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang++"
subprocess.run([str(compiler), "-std=c++17", "-shared", "-fPIC", "-O1", "-Wall", "-Wextra", "-Werror",
                "-static-libstdc++", str(root / "src/main/cpp/oracle.cpp"), "-ldl", "-o", str(out / "libtalkback_oracle.so")], check=True)
(root / "build/vendor-hashes.json").write_text(json.dumps(hashes, indent=2) + "\n")
