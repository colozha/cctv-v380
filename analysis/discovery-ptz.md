# Discovery LAN dan PTZ — analisis APK V380 6.4.90

Target SHA-256: `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Semua temuan APK adalah analisis statis REA/JADX; APK tidak dieksekusi atau dimodifikasi. Tidak ada komunikasi dengan kamera. Nilai credential default pada satu constructor dihilangkan dari evidence turunan.

## Discovery yang sudah terbukti

- `com.macrovideo.sdk.tools.DeviceLanUDPScanner.startSearching(int, int[])` mengosongkan Hashtable hasil, menaikkan generation counter, lalu memilih pasangan sender/receiver. Type 0 memakai BroadcastSenderThread/ReceiverThread; type 1 varian GroupDevice; type 2 AllDevice. Evidence `ev_cc8db726946c1f478bed406d0406e08e875b5818be4ab72b861bf30b93621906`, `evidence/discovery-startSearching.json`.
- `DeviceLanUDPScanner.BroadcastSenderThread.run()` mengubah field static `h` menjadi bytes; mengirim DatagramPacket ke UDP 10008, melalui alamat broadcast dari `DeviceLanUDPScanner.a()` dan `255.255.255.255`. Socket dibuat unbound, reuseAddress diaktifkan, lalu bind local UDP10008. Ada delay 10ms antara destination dan 100ms pada loop, tetapi dekompilasi memuat duplicated-block warning dan kondisi loop janggal, sehingga interval/flow perlu verifikasi bytecode atau capture. Evidence `ev_ff93ad2c5f12d48352a075edfd5278c02d63a2955e00d5c70d4a2f4fe9fe7aab`, `evidence/discovery-Sender-run.json`.
- `DeviceLanUDPScanner.BroadcastReceiverThread.run()` bind UDP10009 dengan reuseAddress; receive array4096 byte, socket receive buffer16384, timeout200ms. Menerima format binary CMD1280 pada old platform (`Functions.parseResult`) dan text legacy/modern (`Functions.ParseDevice`). Untuk modern platform, port nol diganti `Protocol.IOT_PLATFORM_DEVICE_DEFAULT_PORT`. Hasil dideduplikasi dengan device ID dan diberi online status101 serta timestamp. Original REA Evidence `ev_58da43d3c464389f6d5cd3ab537e9075116ecb2df136651f276a3ebe6aa59d75`; tersedia sebagai **excerpt turunan tersanitasi, noncanonical** `evidence/discovery-Receiver-run.sanitized.json`; raw tidak dipertahankan karena satu constructor mengandung literal credential default.
- Receiver memakai `datagramPacket.getData()` lalu berhenti pada NUL; implementasi independen sebaiknya memakai `getLength()` dengan validasi bounds dan tidak mengikuti asumsi padded-buffer ini.

## Pembanding repository

`PyanSofyan/V380Decoder` commit `14cb20fe70cf4c5639bbf15d3cfe453680c516ed`, `src/DeviceDiscovery.cs`, melakukan broadcast ASCII `NVDEVSEARCH^100` ke10008 dan bind10009. Parser menuntut `NVDEVRESULT`, minimal13 komponen `^`, mengambil MAC index2, IP3, subnet4, gateway5, device ID12; dedup berdasarkan MAC. Request dan sebagian field cocok dengan APK; perbedaan source-port serta field tambahan dijelaskan di bawah.

`umar14/v380-android` commit `afe9b13f1e119efa15d41fa604bae6a38f1d6fe7` menggunakan IP/port input pengguna dan TCP custom pada `CameraProtocol.java`; pencarian terbatas terhadap `NVDEVSEARCH|NVDEVRESULT|10008|10009|DeviceDiscovery` tidak menemukan discovery LAN di repo. Jangan menganggap tidak adanya fitur berdasarkan seluruh ekosistem.

`V380Decoder` `src/V380Commands.cs` menyediakan 16-byte control PTZ berawalan byte AA: setelah prefix4byte terdapat LE16 slots bernilai1000 netral, arah horizontal1001/1002, vertikal1003/1004; tail `00 00 01 00`. `V380Client.cs:661` meneruskan PtzRight/Left/Up/Down/Stop ke SendControl lalu SendData(streamStream,payload). Ini bukti source repo, belum bukti wire APK saat ini.

## Batasan discovery

Group/repeater/all-device discovery belum ditelusuri. Belum capture kamera, sehingga kompatibilitas firmware dan perilaku runtime belum dibuktikan. `<clinit>()` scanner terdeteksi tetapi REA mengembalikan no decompiled body, sehingga field initializer tidak boleh ditebak.

## Payload discovery terbukti

`DeviceLanUDPScanner.changeDevLanSearchProtocol(GlobalConfiguration.DeviceProtocolType)` menetapkan untuk `V380`: request `NVDEVSEARCH^100`, response prefix `NVDEVRESULT^100`, `NVDEVRESULT^200` (legacy), `NVDEVRESULT^300` (modern); request group `NVGROUPSEARCH^100^`. Ini mengonfirmasi request/ports V380Decoder cocok untuk subset discovery legacy. Keluarga XVV/SFV/ICS memakai prefix berbeda, sehingga adapter harus memisahkan protocol family. Evidence `ev_d648d10a75955df73d5b8cecbd68f00f3e8b48d4a9547b8c64f679801e8351d7`, `evidence/discovery-changeProtocol.json`.

## Parser discovery

`com.macrovideo.sdk.tools.Functions.ParseDevice(String): DeviceAsSearch` memakai separator `^`; index2 MAC,3IP,9 port (fallback8800 bila tidak ada/parse gagal),12 ID numerik (harus dapat di-parse untuk menghasilkan object),13 configID diteruskan ke constructor,14 device/net type dengan normalisasi1000→2 dan1001→1. Setter yang jelas: index15 `setBindState` (default-1),16 `setConfigNetAbility`,17 `setSpecDevType`. Ability bitmask selanjutnya dipetakan ke cara konfigurasi QR/WPS/BLE/PIN/Ethernet/LTE. APK tidak menggunakan index4/5 dalam method ini; V380Decoder menyimpan subnet/gateway tetapi tidak menyimpan port hasil discovery atau kemampuan binding. Original REA evidence `ev_1db6ba89c5f7c7c9c5ef84e97fbf3e570950d96132bb7eb2cc01eb946552780b`; tersedia sebagai excerpt turunan tersanitasi **noncanonical** `evidence/discovery-ParseDevice.sanitized.json` karena constructor memuat literal credential default; raw tidak dipertahankan.

## PTZ / preset

`com.macrovideo.sdk.media.HSMediaPlayer.setPTZAction(int,boolean,boolean,boolean,boolean,int): boolean` menolak state internal `m` di luar0..3, lalu memanggil `HSMediaLibrary.setPTZParam(this,i,false,0,false,0,z,z2,z3,z4,i2)`. Ini membuktikan bridge kontrol kontinu JNI; nama boolean arah dan wire16-byte dari V380Decoder **belum** terkonfirmasi dalam APK. Evidence `ev_553a3451e530d1a001103d08f8429d4f665d9adc28cb40e4a19f2a652b76d1d4`, `evidence/ptz-setPTZAction.json`.

`com.macrovideo.sdk.media.PTZXController.setPTZXPoint(LoginHandle,int channel,int presetID,int action,int deviceID): int` terdekompilasi tersedia; param semantic didukung string log. Modern platform memanggil `DeviceConfigSetting.startActionNewPlatform`: action104→`Protocol.DAID_PT_RECAL`, selain itu `Protocol.DAID_PT_PRESETS_CTRL` dengan JSON `{ctrl_flag:…,pos_mark_id_cnt:1,pos_mark_id:[presetID]}`. Mapping ctrl_flag: action100/102→1,103→2,101→3, lainnya0. Channel255 dipertahankan, channel>=0 dipetakan1<<channel, negatif0. Jangan beri nama semantik add/delete/goto ke seluruh mapping sebelum caller/firmware diverifikasi.

Legacy dengan `LoginHandle.getVersion() >= Functions.SETTING_VERSION` membangun LAN TCP request256byte: IntToBytes command329 offset0; deviceID4; byte8=1; session handle9; action13; presetID17; channel byte21. Menunggu response32byte dan memeriksa CMD429. Versi lebih tua membangun CMD129 dengan credential milik pengguna dari LoginHandle dan responseCMD229; fallback MR server memakai request412byte. Tidak ada nilai credential/token disalin. Method mencoba LAN (`connectToServer(...,2000,...)`) serta MR (`connectToMRServer(...,8000,...)`), namun JADX melaporkan banyak duplicated blocks dan kondisi janggal sehingga urutan fallback/retry/status return belum layak ditiru tanpa bytecode atau capture. Evidence `ev_864228b5a8ef62dc2348c061a61434454cf9d2c134bc6c65cfe12e724209076c`, `evidence/ptz-setPTZXPoint.json`.

## Rekomendasi modul

- Discovery adapter: UDP broadcast10008/listen10009, parse per-family/per-platform, batas panjang, timeout/cancel, dedupID dengan MAC sebagai metadata. Jangan mempercayai alamat/port/ability dari broadcast tanpa validasi.
- CameraControl: interface `move(direction,speed)`, `stop()`, `gotoPreset(id)`, `savePreset(id)`, `calibrate()`, `setLight(mode)`; backend legacy/native/new-platform terpisah. Selalu stop pada ACTION_UP/ACTION_CANCEL/background/disconnect dan timeout singkat agar kamera tidak bergerak tanpa henti.
- Pisahkan continuous PTZ yang mengikuti session media dan preset/settings yang membuat sesi command TCP. Tunda porting wire kontinu sampai capture kamera atau native dissector membuktikan payload; V380Decoder dapat jadi reference backend eksperimen berlabel model/firmware spesifik.

`HSMediaPlayer.callPTZXLocation(int,int)` memakai state guard0..3 lalu `HSMediaLibrary.setPTZXParam(this,i,103,i2)`. Nama method mendukung inferensi action103=recall/goto preset (modern ctrl_flag2), sedangkan action lain masih perlu caller. Evidence `ev_d06f4ececb676ff51796c85250956dc8fe9eae01eab0dcc217e4df2c766074d8`, `evidence/ptz-callPTZXLocation.json`.

`HSMediaPlayer.SendZoomAction(int action,int step)` memakai state guard0..3 lalu `HSMediaLibrary.setZoomParam(this,action,step,false,false)`. Evidence `ev_7f27781be137721232091ab793e92fe8ef8d755ef5c11f0382b380faa9ca78d9`, `evidence/ptz-SendZoomAction.json`. Action enum/step units dan payload wire zoom belum diketahui.

`HSMediaPlayer.setPTZXLocation(int,int,LoginHandle,int)` memanggil `PTZXController.setPTZXPoint(loginHandle,i,i2,102,i3)`, mendukung action102=set/save preset (modern ctrl_flag1) menurut nama caller. Evidence `ev_f0e5f6213f19a8d65a55e358485e6f7465336e57588af1b3600cfc2338121fc3`, `evidence/ptz-setPTZXLocation.json`.

Perbedaan socket discovery penting: APK memakai sender socket source10008 dan receiver socket10009; V380Decoder memakai socket sama yang bind10009 untuk send dan receive. Target broadcast tetap10008. Tidak ada bukti runtime apakah semua firmware menerima kedua source-port tersebut.

Status lingkup: discovery LAN legacy/text **terjawab statis**, binary CMD1280/group/repeater **parsial**, continuous PTZ/zoom **JNI bridge saja**, preset/save/recall/calibration **alur dan struktur Java terjawab statis**, semua kompatibilitas firmware/wire runtime **belum dibuktikan**.
