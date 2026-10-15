"""Collect selected Java method projections; discard raw REA envelopes."""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys

apk = sys.argv[1]
queries = [
    ("HSMediaPlayer", "com.macrovideo.sdk.media.HSMediaPlayer", "startSpeak"),
    ("HSMediaPlayer", "com.macrovideo.sdk.media.HSMediaPlayer", "stopSpeak"),
    ("MicrophoneRecorder", "com.macrovideo.sdk.media.HSMediaPlayer.MicrophoneRecorder", "init"),
    ("MicrophoneRecorder", "com.macrovideo.sdk.media.HSMediaPlayer.MicrophoneRecorder", "a"),
    ("HSMediaPlayer", "com.macrovideo.sdk.media.HSMediaPlayer", "jniCallOnRecvMediaInfo"),
    ("IoTDevAbility", "com.macrovideo.sdk.objects.IoTDevAbility", "isSupportSpeak"),
    ("HSMediaLibrary", "com.macrovideo.sdk.media.HSMediaLibrary", "sendSpeakAudioData"),
]
def collect(query):
    short, cls, method = query
    command = ["rea", "inspect-android-method", apk, cls, method, "--format", "json"]
    result = subprocess.run(command, capture_output=True, text=True, check=True,
                            env=dict(os.environ, REA_JADX_HEAP_MIB="2048"))
    evidence = json.loads(result.stdout)
    projection = {"kind": "sanitized noncanonical REA projection", "source_evidence_id": evidence["evidence_id"],
                  "subject": evidence["subject"], "parameters": evidence["parameters"],
                  "normalized_result": evidence["normalized_result"], "limitations": evidence["limitations"]}
    path = Path(__file__).parent / f"talkback-{short}-{method}.sanitized.json"
    path.write_text(json.dumps(projection, indent=2) + "\n")
    print(path.name, evidence["evidence_id"], flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    list(pool.map(collect, queries))
