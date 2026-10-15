"""Bounded static ARM64 inspection. Never loads or executes vendor code."""
import ctypes as c
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

class Instruction(c.Structure):
    _fields_ = [("id", c.c_uint), ("address", c.c_uint64), ("size", c.c_uint16),
                ("bytes", c.c_ubyte * 24), ("mnemonic", c.c_char * 32),
                ("op_str", c.c_char * 160), ("detail", c.c_void_p)]

apk = Path(sys.argv[1])
output = Path(sys.argv[2])
member = "lib/arm64-v8a/libhsMediaLibrary.so"
with zipfile.ZipFile(apk) as archive:
    data = archive.read(member)
assert hashlib.sha256(data).hexdigest() == "1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc"
shoff = struct.unpack_from("<Q", data, 40)[0]
shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 58)
sections = [struct.unpack_from("<IIQQQQIIQQ", data, shoff + i * shentsize) for i in range(shnum)]
def section_bytes(section):
    return data[section[4]:section[4] + section[5]]
def string(table, offset):
    return table[offset:table.index(b"\0", offset)].decode("ascii", errors="replace")
names = section_bytes(sections[shstrndx])
named = {string(names, s[0]): s for s in sections}
symsec = named[".dynsym"]
strings = section_bytes(sections[symsec[6]])
symbols = []
for offset in range(symsec[4], symsec[4] + symsec[5], symsec[9]):
    name, info, other, index, addr, size = struct.unpack_from("<IBBHQQ", data, offset)
    symbols.append((string(strings, name), addr, size))
address_names = {addr: name for name, addr, size in symbols if addr}
if ".rela.plt" in named:
    rel = named[".rela.plt"]
    plt = named[".plt"][3]
    for i, offset in enumerate(range(rel[4], rel[4] + rel[5], rel[9])):
        _, info, _ = struct.unpack_from("<QQq", data, offset)
        address_names[plt + 32 + i * 16] = symbols[info >> 32][0] + "@plt"
def file_offset(addr):
    for s in sections:
        if s[1] != 8 and s[3] <= addr < s[3] + s[5]:
            return s[4] + addr - s[3]
    raise ValueError(hex(addr))
cs = c.CDLL("libcapstone.so.5")
cs.cs_open.argtypes = [c.c_int, c.c_int, c.POINTER(c.c_size_t)]
cs.cs_disasm.argtypes = [c.c_size_t, c.c_void_p, c.c_size_t, c.c_uint64, c.c_size_t, c.POINTER(c.POINTER(Instruction))]
cs.cs_disasm.restype = c.c_size_t
cs.cs_free.argtypes = [c.POINTER(Instruction), c.c_size_t]
handle = c.c_size_t()
assert cs.cs_open(1, 0, c.byref(handle)) == 0
selected = []
tokens = ("sendSpeakAudioData", "speakAudioSendThreadFunc", "10startSpeak", "9stopSpeak", "initIotcReq", "parseLiveMediaInfo",
          "iotc_ms_frame_cipher", "V3Transmitter25getData", "V3Transmitter9initParam", "V3Transmitter16startTransmitter",
          "V3Transmitter17dataGet", "setSpeak", "setAudioFormat")
for name, addr, size in symbols:
    if not size or "HSReplay" in name or not any(t in name for t in tokens):
        continue
    if "speakAudioSendThreadFunc" in name and "V3Transmitter" not in name:
        continue
    start = file_offset(addr)
    raw = c.create_string_buffer(data[start:start + size])
    instructions = c.POINTER(Instruction)()
    count = cs.cs_disasm(handle.value, raw, size, addr, 0, c.byref(instructions))
    ranges = [(addr, addr + size)]
    if "V3Transmitter25getData" in name:
        ranges = [(0x4f554, 0x4fd64), (0x501b8, 0x50720), (0x50c00, 0x50eb4),
                  (0x51b9c, 0x51d90), (0x52d08, 0x52eb8)]
    lines = []
    for i in range(count):
        ins = instructions[i]
        if not any(start <= ins.address < end for start, end in ranges):
            continue
        mnemonic = ins.mnemonic.decode()
        operands = ins.op_str.decode()
        target = None
        if mnemonic in ("b", "bl") and operands.startswith("#0x"):
            target = address_names.get(int(operands[1:], 16))
        line = {"va": hex(ins.address), "bytes": bytes(ins.bytes[:ins.size]).hex(), "mnemonic": mnemonic, "operands": operands}
        if target:
            line["target"] = target
        lines.append(line)
    cs.cs_free(instructions, count)
    selected.append({"symbol": name, "va": hex(addr), "symbol_size": size,
                     "selected_ranges": [[hex(a), hex(b)] for a, b in ranges], "instructions": lines})
public_keys = {0x1f6f0: "down", 0x275d1: "vattr", 0x1d098: "codec", 0x1f776: "width", 0x29f4f: "height",
               0x1da64: "aattr", 0x1d09e: "samplerate", 0x2c323: "bits", 0x2df78: "chns"}
for addr, expected in public_keys.items():
    start = file_offset(addr)
    assert data[start:start + len(expected) + 1] == expected.encode() + b"\0"
result = {"kind": "supplementary bounded static disassembly; not canonical REA/native decompilation", "library": member,
          "library_sha256": hashlib.sha256(data).hexdigest(), "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "tool": "system Capstone 5 C API, Python ctypes and ELF64 metadata", "functions": selected,
          "public_protocol_keys": {hex(k): v for k, v in public_keys.items()},
          "limitations": ["No target execution or camera/cloud connection.", "Instruction bytes and symbols only; no credentials, keys, runtime sessions or free-form data exported.", "Static instructions alone are not owner-camera acceptance."]}
output.write_text(json.dumps(result, indent=2) + "\n")
for fn in selected:
    print(fn["symbol"], fn["va"], fn["symbol_size"])
    if "--verbose" in sys.argv:
        for ins in fn["instructions"]:
            print(ins["va"], ins["mnemonic"], ins["operands"], ins.get("target", ""))
