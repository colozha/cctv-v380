# Implementasi LAN legacy V2

Implementasi ini adalah adapter riset berdasarkan APK V380 6.4.90, bukan bukti kamera pengguna sudah kompatibel. Seluruh pengujian otomatis memakai input dan paket sintetis. Tidak ada koneksi kamera/cloud, eksekusi APK/library vendor, perubahan APK, atau pengambilan kredensial pemilik. Profil harus dipilih oleh metadata discovery/handshake yang sesuai, bukan nama SSID/model yang ditebak. Tidak ada probing format login, fallback command302, atau kredensial bawaan.

Spesifikasi dasar dan Evidence ID autentikasi tetap ada pada [protocol-gate.md](protocol-gate.md). Konstanta algoritme wire yang diperlukan implementasi dipulihkan statis dan hanya disimpan dalam source runtime; nilainya tidak disalin ke laporan ini. Provenance tersanitasi ada pada [initializer login](evidence/legacy-login-constant-provenance.sanitized.json) dan [suffix media](evidence/legacy-media-constant-provenance.sanitized.json).

## Autentikasi

`LegacyLanProtocol.authRequest` menulis 520 byte nol dengan command1167, nilai120@4, byte31@8, LE32(10)@9, deviceID uint32@13, waktu lokal `yyyy-MM-dd HH:mm:ss`@17, username UTF-8@49, nonce alfanumerik16 byte@81, dan ciphertext@97. Username wajib eksplisit1–32 byte dan tidak boleh mengandung NUL; deviceID1–4294967295. Password wajib diberikan eksplisit, tidak dicatat, dan dibatasi4096 UTF-16 units agar input tetap terbatas.

Password sampai28 UTF-16 units dikonversi ke UTF-8. Password lebih panjang diproses menjadi lowercase hexadecimal MD5 dari UTF-8, karakter8–23 diambil sebagai teks16 karakter, lalu Base64 tanpa line wrap. Perilaku penggantian surrogate tidak valid sama dengan `String.getBytes(UTF_8)` Java. Kedua lapis AES memakai ECB/PKCS5Padding: kunci protokol untuk lapis pertama, nonce untuk lapis kedua. Buffer plaintext dan ciphertext sementara dihapus setelah serialisasi; pemanggil tetap harus menghapus `CharArray` password dan paket autentikasi setelah pengiriman.

`parseAuthResponse` mensyaratkan tepat256 byte, command1168, result1001, dan signed Java remainder `resultValue % 100 == 0`. Responsecommand lain, termasuk fallback-100, menghasilkan unsupported. Nilai handle@13 dan session@17 dipertahankan sebagai unsigned32 di `Long`; objek login sengaja tidak memiliki generated `toString`. Parser membaca protocolVersion@12, channelCount@63, audio@64 dan PTZ@67. Dukungan dibatasi protocolVersion2–32 dan1–64 channel; angka di luar batas ditolak, tidak diinterpretasikan sebagai generasi baru.

## Stream301/401/303

Request301 berukuran256 byte: deviceID@4, channel@8, u16 communication request@12 (21 bila protocolVersion32, selain itu20), handle low32@14, flag audio4096/4097@22, streamType0/1@26, u16(0x0101)@30, byte1@32. Request303 berukuran256 byte dengan command303@0 dan LE32(0x3001)@4; tidak menyalin result401 menjadi field303.

Mapping kualitas sudah dikonfirmasi langsung dari DEX, bukan hanya label reference: `HSMediaPlayer.STREAM_TYPE_HD=1`, `STREAM_TYPE_SD=0`, dan `isHeightStream()` menguji `VideoPlayParams.nStreamType == 1`. Initializer tidak mengubah kedua nilai itu. [Instruksi kualitas](evidence/legacy-quality.dex.txt) bersama pemetaan JNI `nStreamType` membuktikan HD→1 dan SD→0 pada request301.

Response401 memiliki field yang diketahui hingga byte24: command@0, result@4, u16 communicationVersion@8, width@10, height@14, field tak bernama@18, dan tiga byte atribut audio@22–24. Parser menolak result negatif termasuk-11; tidak mencoba command302. Native tidak mensyaratkan satu kode sukses positif tertentu, sehingga hasil nonnegatif diterima hanya untuk melanjutkan negosiasi; penerimaan frame/decoder tetap dibutuhkan sebelum status streaming. Hanya communicationVersion20/21 didukung. Dimensi wajib16–8192 dengan area maksimal33554432 pixel.

**Batas response401 belum terbukti tetap412 byte.** Native memanggil `recvfrom` maksimum412 dan reference Android menyebut response dapat lebih pendek. Pembacaan TCP sekali tidak dapat diperlakukan sebagai batas message. Integrasi membaca tepat25 byte field yang diketahui, mengirim303, lalu memberi sisa data kepada `LegacyV2FrameParser(allowInitialResponsePadding=true)`. Opsi eksperimental ini mengizinkan maksimal387 byte sebelum header7F pertama yang lolos validasi panjang/count/index; setelah itu parser ketat tanpa pencarian ulang di payload. Uji sintetis mencakup response25/412 byte dan header terpotong. Batas387 berasal dari412−25, bukan observasi live; reserved data yang kebetulan menyerupai header valid tetap merupakan ketidakpastian interoperabilitas yang harus diverifikasi pada kamera. Profil tidak boleh dilabeli `cameraVerified` dari uji ini.

## Framing, tipe dan cipher

Bukti baru [legacy-framing-types.sanitized.json](evidence/legacy-framing-types.sanitized.json) mempertahankan instruksi terpilih dari library tanpa eksekusi. Header fragment luar12 byte membawa total/u16@3, index/u16@5 dan payloadLength/u16@7. Native menolak panjang lebih dari500 dan menyalin payload ke `index*500`, berbeda dari reference C# yang menerima hingga20000 dan langsung concatenate. Karena itu serializer/parser profil ini mensyaratkan500 byte untuk fragment nonfinal,1–500 byte untuk fragment final, index berurutan dan total konsisten. Frame dibatasi8MiB, input feed1MiB. Fragment hilang, duplikat nonawal, metadata tak konsisten atau panjang berlebih menghapus assembly lalu gagal. Fragment baru index0 membuang frame sebelumnya yang belum lengkap. Parser mendukung split/coalescing TCP tanpa mengandalkan batas `read`.

Magic7F dan byte tipe luar berasal dari reference historis; native receive tidak memeriksa magic dan dispatch decoder memakai **tipe header internal**, bukan byte tipe luar. Header internal16 byte adalah frameID/u32@0, frameType/u16@4, frameRate/u16@6 dan timestamp/u64@8. Unit timestamp belum diketahui; adapter mengekspos raw ticks dan display dapat memakai waktu kedatangan lokal. Channel berasal dari channel yang dinegosiasikan, bukan field luar yang belum teridentifikasi.

`getVideoDataType` pada `libhsUtils.so` membuktikan tipe internal0/1=AVC dan40/41=HEVC. Parser V2 menormalkan50/51→0/1 dan52/53→40/41. `isDataKeyFrame` membuktikan tipe keyframe0,2,9,36,40,50,52; tipe21,22,24,25,26 memilih audio. Format video lain dan metadata91–96 tidak ditebak dari bytes payload; codec dibiarkan unsupported. Audio dapat dilewati oleh sesi video.

ProtocolVersion<31 melewati decrypt. Untuk versi31/32, kunci media adalah handle login low32 little endian dan suffix protokol12 byte. CommunicationVersion21 memilih decrypt maksimal2048 byte blok16 penuh untuk video; versi20 memilih64 byte setiap stride80 dan mempertahankan sisa yang pendek. Audio pada kedua versi didecrypt untuk seluruh blok16 penuh. Primitive yang sudah ada, `SparseAesCipher`, dipakai kembali. Cipher dipilih dari metadata handshake dan tipe internal; tidak ada mencoba cipher sampai Annex-B terlihat. Key buffer dihapus saat `LegacyMediaCipher.close()`.

## PTZ

Continuous PTZ memakai16 byte command170, u16(1000)@4/@6, horizontal1001/1002 atau stop1000@8, vertical1003/1004 atau stop1000@10, channel/u16@12 dan parameter/u16@14. Dua arah berlawanan pada satu sumbu menghasilkan stop pada sumbu itu; semua false menghasilkan stop. Parameter default1 sesuai reference, tetapi makna kecepatan/durasinya belum dibuktikan. Kapabilitas PTZ harus berasal dari response login; lifecycle dan watchdog sesi wajib mengirim stop. Tidak ada ACK atau jaminan timeout motor dari analisis ini.

## Pengujian dan gap

`LegacyProtocolTest` berisi10 pengujian JVM dengan input sintetis: batas UTF-16 termasuk emoji/surrogate; pembukaan independen kedua lapis AES/PKCS5; validasi256 byte/signed remainder/unsigned handles; request301/303 dan seluruh arah/stop; hasil401;80 variasi pembagian TCP; remainder40125/412; fragment hilang/duplikat/oversized; dispatch codec/audio; dan enkripsi blok independen untuk semua mode media serta close key. Kompilasi Kotlin terisolasi dan JUnit lulus10/10; tidak menjalankan build paralel keseluruhan aplikasi.

Belum terjawab: panjang response401 kamera sebenarnya, magic/fragment framing live, protocol/platform metadata kamera, login berhasil dengan input pengguna, unit timestamp, Annex-B sesudah decrypt, ukuran/video codec aktual, parameter PTZ serta stop fisik. Implementasi dan fixture sintetis tidak menutup gerbang interoperabilitas tersebut.
