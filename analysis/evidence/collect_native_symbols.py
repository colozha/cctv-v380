"""Read-only ELF symbol/dependency evidence; never loads native code or prints string tables."""
import hashlib,json,pathlib,re,struct,subprocess
src=json.load(open('analysis/evidence/extraction.json'))
root=pathlib.Path(src['normalized_result']['output_root'])
core={'libhsMediaLibrary.so','libhsUtils.so','libmideaCodec.so','libffavc.so','libHSMbedtls.so','libaes.so','libed25519.so','libzerolink.so','libsys_misc.so','libsys_misc-x86.so','libdevInfo.so','libcommonV2DevInfo.so','libavcodec.so','libavformat.so','libavutil.so','libmp4FilePlayer.so'}
relevant=re.compile(r'Java_com_macrovideo_sdk_media_HSMediaLibrary_(?:init|startPlay|stopPlay|setPTZ|setZoom|updateLogin|refresh|setLive|changeVs)|Java_org_ffavc|JNI_OnLoad|HSLiveData(?:Base|V[123])Transmitter|HSLivePlayer.*(?:startPlay|stopPlay|onRecvFrameData|videoDecodeThreadFunc|audioDecodeThreadFunc|setPTZ)|HSMediaManager.*(?:startPlay|stopPlay|setPTZ)|(?:initMediaDecoder|decodeFrameData|releaseMediaDecoder|createDecoder|HSFFMPEGH26[45]Decoder.*(?:initDecoder|decodeData)|h26[45]_parse_sps)|(?:media_data_decrypt_pre_2k|media_data_decrypt|media_data_encrypt|mvt_cipher_start|mvt_cipher_process|mvt_cipher_finish|iotc_ms_frame_cipher.*|_Z.*cipherJsonData.*)|(?:sendto|recvfrom|connect|socket|setsockopt|bind|listen|accept|recv|send|select|poll)(?:@|$)')
rows=[]
for p in sorted(root.glob('lib/*/*.so')):
 data=p.read_bytes(); bit=data[4]; endian='<' if data[5]==1 else '>'
 if bit==2:
  machine=struct.unpack_from(endian+'H',data,18)[0];phoff=struct.unpack_from(endian+'Q',data,32)[0];phsize,phnum=struct.unpack_from(endian+'HH',data,54);fmt=endian+'IIQQQQQQ'
 else:
  machine=struct.unpack_from(endian+'H',data,18)[0];phoff=struct.unpack_from(endian+'I',data,28)[0];phsize,phnum=struct.unpack_from(endian+'HH',data,42);fmt=endian+'IIIIIIII'
 segments=[]
 for i in range(phnum):
  h=struct.unpack_from(fmt,data,phoff+i*phsize)
  if h[0]!=1:continue
  off,va,size=(h[2],h[3],h[5]) if bit==2 else (h[1],h[2],h[4])
  segments.append((off,va,size))
 row={'path':str(p.relative_to(root)),'sha256':hashlib.sha256(data).hexdigest(),'size':len(data),'elf_class':64 if bit==2 else 32,'machine':{183:'AArch64',40:'ARM',3:'Intel 80386',62:'x86-64'}.get(machine,machine)}
 if p.name in core:
  dyn=subprocess.run(['readelf','-d',str(p)],text=True,capture_output=True,check=True).stdout
  row['needed']=re.findall(r'\(NEEDED\).*?\[(.*?)\]',dyn);row['soname']=re.findall(r'\(SONAME\).*?\[(.*?)\]',dyn)
  syms=subprocess.run(['readelf','--dyn-syms','-W',str(p)],text=True,capture_output=True,check=True).stdout
  selected=[];jni=[]
  for l in syms.splitlines():
   parts=l.split()
   if len(parts)<8 or not parts[0].endswith(':') or parts[3] not in ('FUNC','OBJECT','NOTYPE'):continue
   name=parts[7]
   if name.startswith('Java_'):jni.append(name)
   if not relevant.search(name):continue
   addr=int(parts[1],16);offset=next((o+addr-v for o,v,z in segments if v<=addr<v+z),None) if parts[6]!='UND' else None
   selected.append({'name':name,'virtual_address':hex(addr),'file_offset':hex(offset) if offset is not None else None,'size':int(parts[2]),'type':parts[3],'binding':parts[4],'section':parts[6]})
  row['selected_symbols']=selected;row['jni_exports']=jni
 rows.append(row)
result={'evidence_type':'supplementary-shell-static-elf','source_extraction_evidence_id':src['evidence_id'],'apk_sha256':src['subject']['digest']['sha256'],'tools':['Python standard library: hashlib, struct','GNU readelf (static ELF metadata)'],'scope':'All extracted lib/*/*.so headers/hashes; dynamic dependencies and selected function names for core media, utility, provision and codec libraries. No application or native library execution; no credential/token/key values read.','libraries':rows,'limitations':['Symbol names and DT_NEEDED establish linkage/API presence only, not observed behavior or exact wire protocol.','Native internal control flow, dynamically registered JNI, crypto negotiation, addresses/ports and packet fields not recovered here.','No native decompiler provider configured; supplementary analysis is not REA native function analysis.']}
pathlib.Path('analysis/evidence/native-symbols.json').write_text(json.dumps(result,indent=2)+'\n')
print('Saved',len(rows),'libraries,',sum(len(r.get('selected_symbols',[])) for r in rows),'selected symbols')
for row in rows:
 if row['path'].startswith('lib/arm64') and row['path'].split('/')[-1] in {'libhsMediaLibrary.so','libmideaCodec.so','libffavc.so'}:
  print(row['path'],row['sha256'],'dependencies',','.join(row['needed']))
