# Gerbang protokol Android V380 LAN

**Aturan terbaru14 Oktober2026 menggantikan pembatasan adapter pada catatan awal di bawah:** sesuai rencana pengguna, penelitian dan adapter eksperimental berjalan bersama. `legacy-v2` dan `iot-lan` aktif pada debug, dengan serializer/auth/stream/cipher/PTZ nyata serta AP setting terautentikasi. Gerbang tetap berlaku untuk pernyataan kompatibilitas/rilis pengguna: belum ada kamera verified. Detail koreksi dan kontrak ada di [Legacy wire](legacy-wire-spec.md), [IoT LAN](iot-lan-protocol.md), [AP provisioning](ap-provisioning.md), dan [status implementasi](../IMPLEMENTATION.md). Konstanta protokol publik yang diperlukan wire ada pada source; tidak ada kredensial pemilik atau token akun yang diekstrak.


**Pembaruan 13 Oktober 2026:** pengguna telah menyampaikan versi firmware dan screenshot opsi ONVIF yang nonaktif; lihat [observasi kamera](camera-observation.md). Pernyataan firmware belum tersedia di bawah merekam keadaan saat investigasi statis awal. Model, jalur protokol dan uji live tetap belum tersedia; gerbang belum lulus.

Investigasi lanjutan ini memperjelas implementasi APK V380 6.4.90, tetapi **belum meluluskan tahap 0**. Model/firmware kamera milik pengguna, respons autentikasi yang sebenarnya, hasil negosiasi stream, dan keberhasilan PTZ belum tersedia. Tidak ada koneksi kamera/cloud, eksekusi APK/native library, atau perubahan APK. Implementasi aplikasi boleh melanjutkan discovery, penyimpanan profil, UI, transport umum, dan decoder; adapter autentikasi/stream/PTZ harus tetap tidak aktif sampai profil kamera diverifikasi.

Input APK SHA-256 tetap `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Bukti Android baru berasal dari REA/JADX. Instruksi DEX dan AArch64 merupakan bukti pelengkap statis, bukan dekompilasi native REA atau pengamatan runtime. Nilai kunci protokol, kredensial, dan token tidak dicantumkan; initializer kunci serta immediate pembentuk kunci dikeluarkan dari artefak bukti tambahan. Provenance dan hash artefak ada pada [gate-provenance.json](evidence/gate-provenance.json).

## 1. Autentikasi LAN yang dipulihkan

### Transformasi password

`com.macrovideo.sdk.tools.GlobalDefines.changeDevPwd(String)` memiliki perilaku berikut:

1. `null` atau panjang maksimal 28 **UTF-16 code units Java** dikembalikan tanpa perubahan.
2. Input lebih panjang diproses oleh `md5_16`: helper `md5(String)` menghasilkan 32 karakter hexadecimal lowercase dari input UTF-8; `substring(8,24)` mengambil 16 karakter tengah.
3. Enam belas karakter hex tersebut di-encode Base64 dengan flag Android `2` (`NO_WRAP`). Ini bukan Base64 dari 16 byte digest mentah.
4. Jika helper tidak menghasilkan substring valid/nonkosong, input asli dipertahankan.

Bukti REA:

| Method | Evidence ID | Artefak |
|---|---|---|
| `changeDevPwd` | `ev_8506a106a74fd43a557ba148c384453e9315e614ffdca2f411e0b0ba62199467` | [gate-changeDevPwd.json](evidence/gate-changeDevPwd.json) |
| `md5_16` | `ev_860b10bfb6845b2ec5c968c38918bf4a4cf927c404747ead4e4ded701dd9b8bd` | [gate-md5_16.json](evidence/gate-md5_16.json) |
| `md5(String)`, overload 0 | `ev_fb4d6967bf91246dab48cf0f37178106ff6d13f0bd3ef134f45aceedb3631d39` | [gate-md5.json](evidence/gate-md5.json) |

`LoginHelper.lanLogin` menerapkan transformasi ini sebelum memanggil `b(ip, username, transformedPassword, port, deviceId)`. `b` memakai dua lapis `Functions.encrypt`: password dienkripsi dengan konstanta protokol, lalu ciphertext dienkripsi dengan nonce 16 karakter yang dikirim pada request. Bukti terdahulu `Functions.encrypt` menetapkan `AES/ECB/PKCS5Padding` untuk **kedua lapis**, bukan zero padding. Tidak ada nilai kunci disalin ke laporan/client.

### Konstanta dan urutan I/O

Initializer `LoginHelper` mengatur `b/f=1167`, `c/g=1168`, `d=31167`, `e=31168`. `changeDevLoginProtocol(V380)` memilih pasangan 1167/1168, sedangkan `XVV` memilih 31167/31168 dan menonaktifkan `sIsUseV30Login`. Bukti: [initializer tersanitasi](evidence/gate-LoginHelper-clinit.sanitized.json), yang mencatat source Evidence ID, dan REA `ev_800279025fab79aabb0f89f7ba650b875921d6df4ed5e87b9ef20d3ac848354a` pada [gate-changeDevLoginProtocol.json](evidence/gate-changeDevLoginProtocol.json).

Dekompilasi Java terdahulu menampilkan read sebelum write dan variabel belum diinisialisasi. Instruksi asli **mengoreksi artefak decompiler tersebut**:

| Lokasi `classes5.dex`, method `LoginHelper.b` | Perilaku |
|---|---|
| code unit `0x016a..0x0184` | dua pemanggilan encrypt, copy ciphertext ke request offset 97 |
| `0x018f`, DEX file offset `0x0078677e` | `OutputStream.write(buffer,0,520)` |
| `0x01b5`, offset `0x007867ca` | `flush()` |
| `0x01b9`, offset `0x007867d2` | kosongkan buffer sebelum menerima respons |
| `0x01bd..0x01d6` | hingga 80 poll, interval 50 ms, menunggu `available() >=256` |
| `0x01d8`, offset `0x00786810` | `InputStream.read(buffer,0,256)` |

Bukti penuh dengan isi string/array payload dikecualikan: [gate-LoginHelper-b.dex.txt](evidence/gate-LoginHelper-b.dex.txt); collector offline: [gate-dex-instructions.java](evidence/gate-dex-instructions.java). `code_item_offset=7890000`, register count 46. Offset `dex@` bersifat relatif terhadap file DEX yang diekstrak dari APK, bukan offset ZIP atau alamat runtime. Collector memakai dexlib2 yang sudah terbundel dalam JADX headless MCP 0.7.1; tidak menjalankan kode APK.

Request APK berukuran 520 byte, little endian:

| Offset | Field |
|---|---|
| 0/u32 | command terpilih, V380=1167 |
| 4/u32 | 120 |
| 8/u8 | 31 |
| 9/u32 | 10 |
| 13/u32 | device ID |
| 17 | tanggal/waktu lokal `yyyy-MM-dd HH:mm:ss` |
| 49 | username bytes |
| 81 | nonce 16 karakter |
| 97 | hasil nested AES dengan PKCS5Padding |

Format tanggal berasal dari REA `ev_ba1a281f0251e17f557836454d3c04863142783910dbe6c551e9dd3ccb2cbf80`, [gate-getStrTimeWhenLANLogin.json](evidence/gate-getStrTimeWhenLANLogin.json). Ukuran field input dan encoding harus divalidasi client independen sebelum serialisasi; APK sendiri menggunakan `arraycopy` tanpa clamp field yang memadai. Pola `available`/satu `read` milik APK bukan rekomendasi transport yang aman: gunakan pembacaan tepat dengan deadline/cancellation.

Respons 256 byte pada jalur ini membawa command@0, result@4, resultValue@8, protocolVersion@12, handle@13, session@17, channel count@63, capability PTZ@67, serta atribut lainnya. Jalur sukses memeriksa command 1168, result 1001, dan remainder `resultValue %100 ==0` sesuai semantik integer Java. Nilai handle/session runtime tidak pernah dibaca. `lanLogin` masih memiliki fallback ke overload `a(String,String,String,int,int)` saat command respons `-100`; fallback tersebut **belum menjadi bagian profil terverifikasi** dan tidak boleh dicoba otomatis oleh client baru.

## 2. Pemilihan transmitter dan stream start

Instruksi JNI membuktikan hubungan field Java dengan struktur native, bukan sekadar kemiripan nama. [gate-JNI-field-names.json](evidence/gate-JNI-field-names.json) merekam hanya nama field dari operand `GetFieldID`; tidak ada nilai objek Java yang dibaca.

| Field Java `HSMediaPlayer` | Offset `tagPlayParam` |
|---|---|
| `lLoginHandle` | `0x20` |
| `nDeviceId` | `0x14` |
| `nDevType` | `0x18` |
| `nChannel` | `0x29c` |
| `nP2pIdx` | `0x2a0` |
| `nStreamType` | `0x2a4` |
| `isAudioEnable` | `0x2ad` |
| `mProtocolVersion` | `0x2b8` |
| `protocolType` | `0x2d0` |

`HSLivePlayer::startPlay` menyalin parameter ke `this+8`, lalu memilih V3 bila `protocolType==2`; untuk jalur lain yang tidak ditolak, memilih V2 jika `mProtocolVersion>=2`, selain itu V1. `protocolType==1` mengembalikan gagal pada fungsi ini. Bukti instruksi `0x85670..0x856e4`. Menyebut `mProtocolVersion` sebagai firmware version tanpa bukti model/firmware adalah tidak tepat.

Pada `HSLiveDataV2Transmitter::getDataFromDevice`, `socket(AF_INET,SOCK_STREAM,0)` dan `connect` membuktikan transport TCP untuk jalur LAN ini. Walaupun fungsi pengiriman bernama libc `sendto`, tujuan `NULL` memakai socket TCP yang telah terkoneksi.

Request V2 command 301 yang dipulihkan berukuran 256 byte, mula-mula nol:

| Offset | Isi |
|---|---|
| 0/u32 | 301 |
| 4/u32 | `nDeviceId` |
| 8/u32 | `nChannel` |
| 12/u16 | 21 jika `mProtocolVersion==32`, selain itu 20 |
| 14/u32 | 32 bit bawah `lLoginHandle` |
| 22/u32 | 4097 jika audio aktif, 4096 jika tidak |
| 26/u32 | `nStreamType` |
| 30/u16 | `0x0101` |
| 32/u8 | 1 |

Instruksi pembentukan berada di `0x44394..0x44440`, pengiriman `0x44490`. Respons awal diparse sebagai command@0, result@4, communicationVersion/u16@8, width/u32@10, height/u32@14, field/u32@18, dan atribut audio byte@22..24. Arti field18 belum ditetapkan melalui caller pada investigasi ini; C# menamakannya `maxPackSize`. APK memiliki cabang result `-11` yang mengirim command302 dengan material autentikasi lain. Cabang ini belum dispecifikasikan sebagai adapter aktif.

**Command303 sudah dibuktikan:** APK V2 mengirim 256 byte nol dengan LE32(303) pada offset0 dan LE32(`0x3001` =12289) pada offset4. Tidak ada handle atau status401 yang dicopy pada request ini. Bukti store/send `0x44f70..0x44fd4` serta pembacaan tepat8 byte prefix protokol di `0x30618`, [gate-stream-prefixes.json](evidence/gate-stream-prefixes.json). V1 memakai command8193 dengan argumen12289 pada jalur analog; kedua generasi tidak boleh dicampur.

## 3. Pemilihan cipher media V2

`HSLiveDataV2Transmitter::initParam` menyalin `lLoginHandle` ke `this+0x2f8`. Bila `mProtocolVersion>=31`, key buffer 16 byte pada `this+0x308` dibentuk dari **32 bit bawah handle login ditambah 12 byte konstanta protokol**. `updateAesKey` serta `updateLoginHandle` mengulang pembentukan yang sama. Nilai konstanta tidak dicantumkan atau dibaca dari data region; immediate yang menjadi material kunci telah disunting dari JSON instruksi.

`parseFrameDataV20` memisahkan header internal16 byte: u32@0, signed16@4, signed16@6, u64@8; compressed payload mulai offset16. Header/tipe dan unit timestamp belum dianggap sebagai kontrak kamera pengguna. Caller menerima communicationVersion sebagai argumen kelima, dan decoder memakai field@4 untuk dispatch.

Pemilihan primitive sudah dapat dipulihkan lebih jauh daripada laporan awal:

| Kondisi | Perlakuan |
|---|---|
| `mProtocolVersion<31` | lewati decrypt media pada jalur ini |
| tipe21/22/24/25/26 | jalur audio, flag1 |
| jalur media default/video | flag0 |
| communicationVersion21 + flag0 | `media_data_decrypt_pre_2k`: decrypt blok lengkap hingga maksimal2048 byte |
| communicationVersion21 + flag1 | `media_data_decrypt_pre_2k`: decrypt seluruh blok lengkap16 byte |
| communicationVersion lain + flag0 | `media_data_decrypt`: decrypt64 byte setiap stride80; sisa<64 tidak diproses |
| communicationVersion lain + flag1 | `media_data_decrypt`: decrypt seluruh blok lengkap16 byte |

Primitive AES128 ECB decrypt itu sendiri dibuktikan pada investigasi terdahulu di [native-media-decrypt-disassembly.json](evidence/native-media-decrypt-disassembly.json). Bukti pemilihan caller yang baru: `0x49738..0x49764`, `0x49984..0x499b8`, serta [gate-frame-type-switch.json](evidence/gate-frame-type-switch.json). Mode aktif pada kamera pengguna tetap belum diketahui karena belum ada respons/stream kamera.

## 4. Continuous PTZ V1/V2

`HSMediaPlayer.setPTZAction` memanggil native `setPTZParam(this,channel,false,0,false,0,left,right,up,down,parameter)`. Nama arah merupakan interpretasi yang dikorelasikan dengan konstanta C#; signature Java terdekompilasi memakai nama boolean obfuscated.

`HSLiveDataBaseTransmitter::setPTZParam` menyimpan channel pada `this+0xa4`, empat boolean arah pada `+0xa8..+0xab`, parameter pada `+0xac`, dan pending flag pada `+0xa0`. Action thread membaca pending flag dan menulis paket **16 byte command170**, langsung pada socket stream terkoneksi. V2 pengiriman berada di `0x43450..0x4349c`; V1 di `0x3e8ac..0x3e8f4`.

| Offset | Isi little endian |
|---|---|
| 0/u32 | 170 (`0xaa`) |
| 4/u16 | 1000 |
| 6/u16 | 1000 |
| 8/u16 | 1001=arah pertama/left, 1002=arah kedua/right, selain itu1000 |
| 10/u16 | 1003=arah ketiga/up, 1004=arah keempat/down, selain itu1000 |
| 12/u16 | channel pada V2; selalu0 pada V1 |
| 14/u16 | 16 bit bawah parameter terakhir API |

Bila kedua arah dalam satu sumbu sama-sama true, thread memilih1000 pada sumbu tersebut. Semua arah false menghasilkan paket stop. Interpretasi offset14 sebagai kecepatan/jangka waktu belum dipastikan; C# menetapkannya1. Nilai arah dan channel persis kamera pengguna harus diuji sebelum enable PTZ. Action thread tidak membuktikan adanya timeout stop yang dijamin oleh firmware maupun ACK yang menjamin motor berhenti.

## 5. Perbandingan dan desain profil

Referensi lokal tetap commit Android `afe9b13f1e119efa15d41fa604bae6a38f1d6fe7` dan C# `14cb20fe70cf4c5639bbf15d3cfe453680c516ed`. Tidak ada source reference yang dipindahkan ke runtime app.

| Area | APK teramati | Implikasi terhadap reference |
|---|---|---|
| Auth | nested AES PKCS5Padding, nonce@81, ciphertext@97, u32(10)@9 | C# memakai u32(1)@9 dan skema password lain; Android memakai request256 byte. Jangan auto-probe varian. |
| Request301 | versi20/21 berdasarkan `mProtocolVersion`, tambahan@30/32 | Core C# serupa, tetapi tidak cukup untuk menganggap seluruh paket identik. |
| Request303 | offset4 konstan0x3001 | Cocok C#; Android justru menyalin result401[4], sehingga tidak sesuai jalur native APK yang dibuktikan. |
| Video commVersion21 | pre2k flag0, maksimal2048 byte | Receive loop C# memanggil pre2k mode1 untuk video; komentar helper menyatakan belum diuji. Jangan ikuti tanpa koreksi profil. |
| Continuous PTZ | command170,16 byte | Konstanta C# empat arah/stop cocok channel0, parameter1; bukan command318. |

Kandidat profil riset dapat dinamai `v380-apk6490-v2` dengan fakta wire di atas, tetapi statusnya **unverified / disabled**. Identitas profil bukan nama produk generik. Saat gate lulus, satu profil menetapkan command login, transformasi password, padding/cipher, layout301/303, versi header/frame, kualitas/channel, dan PTZ secara tetap. Input atau discovery yang tidak memenuhi profil harus menghasilkan unsupported protocol; jangan mencoba beberapa format login dengan password yang sama.

UI dan session boleh mempunyai kontrak nyata sekarang: connect hanya mengaktifkan profil yang gate-nya lulus, kemudian capability/stream/PTZ mengikuti profil itu. Jangan menampilkan `Ready`/`Streaming` dari dummy data, memakai decoder untuk menebak cipher, atau memasukkan library proprietary APK sebagai jalan pintas.

## 6. Yang masih menghalangi kelulusan tahap0

| Syarat | Status |
|---|---|
| Model/firmware, device protocol/platform type kamera pengguna | belum tersedia |
| Satu auth request/response berhasil dengan input pengguna | belum diuji; analisis statis tidak membuktikan interoperabilitas |
| Profil awal/binary login versus fallback versus LAN key exchange | belum dipilih oleh fakta kamera |
| Kode gagal auth serta kebutuhan date/nonce/field reserved pada firmware kamera | belum diuji |
| Respons401 yang lengkap, dimensi, communicationVersion dan codec | belum tersedia |
| Reassembly/framing dan cipher menghasilkan Annex-B valid pada kamera | belum diuji |
| SD/HD dan frame pertama<=5 detik | belum diuji |
| Empat arah PTZ, channel, parameter terakhir dan stop | belum diuji |
| Streaming30 menit, reconnect, lifecycle Android dan internet diblokir | belum diuji |

Tidak ada nilai rahasia yang perlu dimasukkan ke repository untuk menutup gap tersebut. Bukti live berikutnya sebaiknya dilakukan secara lokal pada kamera pengguna dengan log ringkas berupa panjang, command/status, capability dan versi; payload login, password, handle/session, material cipher, serta paket auth lengkap tidak dicetak atau dijadikan fixture. Sampel parser disintetiskan dari spesifikasi. Temuan statis baru ini memungkinkan implementasi serializer yang tepat setelah profil target disahkan, tetapi tidak menggantikan gerbang live yang diwajibkan rencana.

Artefak instruksi native lengkap yang tersanitasi: [gate-native.json](evidence/gate-native.json), collector [gate-collect-native.py](evidence/gate-collect-native.py). Pemeriksaan dibatasi ke19 fungsi ekspor (termasuk marshalling JNI, selector player, V1/V2 LAN receive/control, dan cipher init), delapan byte prefix stream pada masing-masing dua lokasi, metadata nama field JNI terpilih, serta76 byte tabel dispatch tipe frame. Tidak ada native session REA yang dibuka sehingga tidak ada session native yang harus ditutup.
