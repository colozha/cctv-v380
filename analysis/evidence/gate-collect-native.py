"""Offline instruction evidence for known exported functions, without data/string dereference."""
import hashlib, json, pathlib, re, subprocess, sys
sys.path.insert(0, '/tmp/v380-static-analysis-tools')
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

root = pathlib.Path(json.load(open('analysis/evidence/extraction.json'))['normalized_result']['output_root'])
p = root / 'lib/arm64-v8a/libhsMediaLibrary.so'
data = p.read_bytes()
symbols = []
for line in subprocess.check_output(['readelf', '--dyn-syms', '-W', str(p)], text=True).splitlines():
    parts = line.split()
    if len(parts) >= 8 and parts[3] == 'FUNC' and parts[6] != 'UND':
        symbols.append((int(parts[1], 16), int(parts[2]), parts[7]))

selected = re.compile(r'HSLiveData(?:Base|V1|V2)Transmitter.*(?:setPTZParam|initParam|getDataFromDevice|updateAesKey|actionCtrlThreadFunc|parseFrameDataV13|parseFrameDataV20)|HSMediaLibrary_(?:setPTZParam|startPlay)$|HSLivePlayer.*startPlay|HSLiveDataV2Transmitter.*updateLoginHandle|initCommonDataSendBuffer|iotc_ms_frame_cipher_init|setPlayParamWithJavaObject')
md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
reloc_text = subprocess.check_output(['readelf', '-r', '-W', str(p)], text=True)
relocs = {}
for line in reloc_text.splitlines():
    parts = line.split()
    if len(parts) >= 5 and parts[2] == 'R_AARCH64_JUMP_SLOT':
        relocs[int(parts[0], 16)] = parts[4]

# PLT stubs are instructions only: resolve their immediate GOT slot through metadata.
plt = {}
section_text = subprocess.check_output(['readelf', '-SW', str(p)], text=True)
for line in section_text.splitlines():
    match = re.search(r'\]\s+\.plt\s+PROGBITS\s+([0-9a-f]+)\s+([0-9a-f]+)\s+([0-9a-f]+)', line)
    if not match:
        continue
    va, off, size = (int(x, 16) for x in match.groups())
    block = list(md.disasm(data[off:off+size], va))
    for a, b in zip(block, block[1:]):
        if a.mnemonic == 'adrp' and a.op_str.startswith('x16, #') and b.mnemonic == 'ldr' and b.op_str.startswith('x17, [x16'):
            page = int(a.op_str.split('#')[1], 0)
            disp = int(b.op_str.split('#')[1].rstrip(']'), 0) if '#' in b.op_str else 0
            if page+disp in relocs:
                plt[a.address] = relocs[page+disp]

functions = []
for va, size, name in symbols:
    if not selected.search(name):
        continue
    instructions = []
    for ins in md.disasm(data[va:va+size], va):
        row = {'va': hex(ins.address), 'file_offset': hex(ins.address), 'bytes': ins.bytes.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str}
        if ins.address in {0x43e90, 0x43e98, 0x440a8, 0x440b0, 0x44190, 0x44198}:
            row.update(bytes='<excluded>', operands='w9, <constant-key-material-excluded>', redacted=True)
        if ins.mnemonic in ('bl', 'b') and ins.op_str.startswith('#'):
            target = int(ins.op_str[1:], 0)
            row['direct_target'] = plt.get(target) or next((n for v, _, n in symbols if v == target), None)
        instructions.append(row)
    functions.append({'name': name, 'virtual_address': hex(va), 'file_offset': hex(va), 'size': size, 'instructions': instructions})

out = {'evidence_type': 'supplementary-bounded-static-disassembly', 'tool': 'Capstone 5.0.6 and GNU readelf', 'source': 'lib/arm64-v8a/libhsMediaLibrary.so', 'sha256': hashlib.sha256(data).hexdigest(), 'functions': functions, 'limitations': ['No APK/library execution or camera/cloud connection.', 'No data/string/key values dereferenced.', 'VA equals file offset for selected text ranges; addresses are not runtime ASLR addresses.', 'Instruction/control-flow interpretation is supplementary shell analysis, not a REA native decompiler result.']}
pathlib.Path('analysis/evidence/gate-native.json').write_text(json.dumps(out, indent=2)+'\n')
print('Saved', len(functions), 'functions,', sum(len(f['instructions']) for f in functions), 'instructions')
