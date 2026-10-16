"""Decode only the labeled synthetic IMA fixture with the host's independent libsndfile."""
import ctypes as c
from ctypes.util import find_library
import hashlib
import json
import math
from pathlib import Path
import struct

root = Path(__file__).resolve().parents[2]
fixture = root / 'app/build/talkback-noise/synthetic-ima.wav'
original_file = fixture.with_name('synthetic-input.pcm')
class Info(c.Structure):
    _fields_ = [('frames', c.c_int64), ('samplerate', c.c_int), ('channels', c.c_int),
                ('format', c.c_int), ('sections', c.c_int), ('seekable', c.c_int)]
lib = c.CDLL(find_library('sndfile'))
lib.sf_open.argtypes = [c.c_char_p, c.c_int, c.POINTER(Info)]
lib.sf_open.restype = c.c_void_p
lib.sf_readf_short.argtypes = [c.c_void_p, c.POINTER(c.c_short), c.c_int64]
lib.sf_readf_short.restype = c.c_int64
lib.sf_close.argtypes = [c.c_void_p]
lib.sf_version_string.restype = c.c_char_p
info = Info()
handle = lib.sf_open(str(fixture).encode(), 0x10, c.byref(info))
assert handle
try:
    decoded = (c.c_short * (info.frames * info.channels))()
    frames = lib.sf_readf_short(handle, decoded, info.frames)
    original = struct.unpack('<10100h', original_file.read_bytes())
    assert len(original) == len(decoded) == frames == 10100
    assert info.samplerate == 16000 and info.channels == 1
    mse = sum((a-b)**2 for a,b in zip(original, decoded)) / frames
    rms = math.sqrt(sum(a*a for a in original) / frames)
    correlation = sum(a*b for a,b in zip(original, decoded)) / math.sqrt(
        sum(a*a for a in original) * sum(b*b for b in decoded))
    report = dict(decoder=lib.sf_version_string().decode(), sample_rate=info.samplerate,
                  channels=info.channels, samples=frames, normalized_rmse=math.sqrt(mse)/rms,
                  correlation=correlation, fixture='Synthetic440Hz tone only; no microphone or vendor code executed',
                  fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest())
    assert report['normalized_rmse'] < 0.15 and correlation > 0.98
    (Path(__file__).parent / 'independent-decode.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report))
finally:
    lib.sf_close(handle)
