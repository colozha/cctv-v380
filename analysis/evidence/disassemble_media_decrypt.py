import json,pathlib,sys,hashlib
sys.path.insert(0,'/tmp/v380-static-analysis-tools')
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_ARM
src=json.load(open('analysis/evidence/extraction.json'))
p=pathlib.Path(src['normalized_result']['output_root'])/'lib/arm64-v8a/libaes.so'
b=p.read_bytes()
md=Cs(CS_ARCH_ARM64,CS_MODE_ARM)
entries=[('media_data_decrypt',0x3ffc,160),('media_data_decrypt_pre_2k',0x409c,112),('PLT_aes128_ecb_encrypt',0x41d0,16),('aes128_ecb_encrypt',0x3b68,468),('AES_ecb_encrypt',0x3a70,16),('PLT_private_AES_set_decrypt_key',0x4170,16),('PLT_AES_ecb_encrypt',0x41a0,16),('PLT_AES_decrypt',0x4190,16)]
results=[]
for name,off,size in entries:
 lines=[{'va':hex(i.address),'file_offset':hex(i.address),'bytes':i.bytes.hex(),'mnemonic':i.mnemonic,'operands':i.op_str} for i in md.disasm(b[off:off+size],off)]
 results.append({'name':name,'virtual_address':hex(off),'file_offset':hex(off),'size':size,'instructions':lines})
 print(name)
 for line in lines:print(line['va'],line['mnemonic'],line['operands'])
j={'evidence_type':'supplementary-bounded-static-disassembly','tool':'Capstone 5.0.6','source_path':'lib/arm64-v8a/libaes.so','sha256':hashlib.sha256(b).hexdigest(),'functions':results,'restrictions':'No target execution; no string/data/key contents dereferenced. Addresses from dynamic symbol sizes; virtual address equals file offset for selected functions.'}
import subprocess
relocs=subprocess.check_output(['readelf','-r','-W',str(p)],text=True)
j['selected_relocations']=[l.strip() for l in relocs.splitlines() if any(n in l for n in ['private_AES_set_decrypt_key','AES_decrypt +','AES_ecb_encrypt +','aes128_ecb_encrypt +'])]
pathlib.Path('analysis/evidence/native-media-decrypt-disassembly.json').write_text(json.dumps(j,indent=2)+'\n')
