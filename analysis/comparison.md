# Perbandingan source referensi V380

Analisis statis pada 13 Oktober 2026. Source diambil dari repository publik dan dibaca tanpa menjalankan client, discovery LAN, koneksi kamera, atau endpoint cloud. Seluruh fakta di bawah adalah **observasi source**, bukan bukti keberhasilan terhadap kamera pengguna atau kesetaraan dengan APK V380 6.4.90. Kredensial contoh, password, konstanta kunci, suffix penandatanganan, dan ticket sesi tidak disalin ke laporan.

## Reproduksibilitas

| Repository | Commit yang dianalisis | Checkout lokal |
|---|---|---|
| [umar14/v380-android](https://github.com/umar14/v380-android/tree/afe9b13f1e119efa15d41fa604bae6a38f1d6fe7) | `afe9b13f1e119efa15d41fa604bae6a38f1d6fe7` | `analysis/references/v380-android` |
| [PyanSofyan/V380Decoder](https://github.com/PyanSofyan/V380Decoder/tree/14cb20fe70cf4c5639bbf15d3cfe453680c516ed) | `14cb20fe70cf4c5639bbf15d3cfe453680c516ed` | `analysis/references/V380Decoder` |

Keduanya berupa clone bersih; tidak ada perubahan source referensi. Semua nomor baris merujuk commit di atas. Nama `J/` di bawah berarti `v380-android/app/src/main/java/com/example/v380cam/`; `C/` berarti `V380Decoder/src/`.

## Hasil yang paling berpengaruh

Android Java cocok sebagai contoh pemisahan transport dari `MediaCodec`, tetapi bukan implementasi lengkap: tidak ada discovery aktual, P2P, PTZ, dekripsi media, atau pemutaran audio. C# lebih lengkap untuk LAN, media terenkripsi, relay cloud, dan kontrol, tetapi tetap memerlukan validasi per firmware. README C# membatasi pengujian penulis pada dua kamera device version 31 dengan H.264 (`README.md:13–15`); fungsi komunikasi version 21 diberi komentar belum diuji (`C/V380Client.cs:546`).

**Peringatan terhadap pembacaan README:** README Android menampilkan `CameraDiscovery.java` dalam diagram (`README.md:158`), tetapi file tersebut tidak ada dalam git tree commit ini. Panduan discovery mengarahkan pembaca ke alat C++ lain (`README.md:133–141`). Jangan menyatakan repository Android sudah mengimplementasikan discovery.

## Matriks fitur dan bukti

| Fokus | Android Java | C# V380Decoder |
|---|---|---|
| Discovery LAN | Tidak ada implementasi pada git tree | `DeviceDiscovery.Discover/Parse`, UDP10009/10008; `C/DeviceDiscovery.cs:24–97` |
| Auth | `CameraProtocol.run`; request256B; `J/CameraProtocol.java:57–101` | `GetAuthTicket`; request520B; `C/V380Client.cs:87–202` |
| Stream login/start | `run`; 301→401→303; `J/CameraProtocol.java:103–144` | `StreamLogin/StartStream`; `C/V380Client.cs:205–311` |
| Framing | 12B frag +16B video header; video00/01, audio16; `J/CameraProtocol.java:153–200` | 12B frag +16B header; video00/01/28/29, audio1A; `C/V380Client.cs:344–501` |
| Dekripsi media | Tidak ditemukan | `GenerateMediaKey/DecryptVideoFrame/DecryptAudioFrame/DecryptMediaPre2k`; `C/V380Client.cs:522–575` |
| Relay cloud | Tidak ditemukan | `DispatchRelayServer.GetServerIPAsync`, source Cloud; `C/DispatchRelayServer.cs:16–51`, `Program.cs:64–93` |
| NAT traversal/P2P langsung | Tidak ditemukan | Tidak ada implementasi hole punching/rendezvous UDP kamera yang terlihat; source cloud adalah relay TCP |
| Decoder Android/native | `H264Decoder` memakai Android `MediaCodec`; tidak ada JNI milik vendor | RTSP repackaging; snapshot memakai H264Sharp atau proses FFmpeg; `C/SnapshotManager.cs:49–59,186–238` |
| PTZ/kontrol | Tidak ditemukan | `PtzRight/Left/Up/Down/Stop`, `SendControl`; `C/V380Client.cs:661–677`; paket di `C/V380Commands.cs:10–21` |

## Discovery LAN

`DeviceDiscovery.Discover` bind UDP `0.0.0.0:10009`, aktifkan broadcast, kirim ASCII `NVDEVSEARCH^100` ke `255.255.255.255:10008`, maksimal lima iterasi dengan jendela penerimaan250ms. Parser membelah `^`, mensyaratkan minimal13 bagian dan penanda `NVDEVRESULT`, lalu mengambil MAC indeks2, IP3, subnet4, gateway5, device ID12; deduplikasi berdasarkan MAC. Bukti: [`DeviceDiscovery.cs:24–97`](https://github.com/PyanSofyan/V380Decoder/blob/14cb20fe70cf4c5639bbf15d3cfe453680c516ed/src/DeviceDiscovery.cs#L24-L97).

Parameter `sourceIp` diteruskan dari alamat pengirim namun tidak dipakai parser (`:49,76–97`), sehingga IP yang dipercaya berasal dari payload. Ini adalah perilaku source, bukan rekomendasi. Client baru sebaiknya memvalidasi alamat pengirim/payload dan mendukung pemilihan interface Wi-Fi; belum terbukti semua firmware memakai susunan ini.

## Koneksi dan autentikasi

Alur bersama: TCP auth → request1167 → response1168 → ambil ticket → tutup auth socket → TCP stream baru → request301 memakai ticket → response401 → request303 → loop fragmen. C# default port8800 terlihat pada `Program.cs:23`; Android menerima port dari UI.

Request dan response menggunakan little-endian pada integer. **Varian berbeda, jangan digabung menjadi satu struct universal.**

| Field | Java LAN | C# LAN |
|---|---:|---:|
| Panjang request1167 | 256 | 520 |
| command u32 | offset0 =1167 | offset0 =1167 |
| field u32 belum terjelaskan | offset4 =1022 | offset4 =120 |
| field byte/version dugaan | offset8 =2 | offset8 =31 |
| field u32 | offset9 =1 | offset9 =1 |
| deviceId u32 | offset13 | offset13 |
| username | offset49, max32B | offset49, max32B |
| representasi password | offset81, max32B | offset81, max64B |

Bukti Java [`CameraProtocol.java:67–101`](https://github.com/umar14/v380-android/blob/afe9b13f1e119efa15d41fa604bae6a38f1d6fe7/app/src/main/java/com/example/v380cam/CameraProtocol.java#L67-L101); C# [`V380Client.cs:103–187`](https://github.com/PyanSofyan/V380Decoder/blob/14cb20fe70cf4c5639bbf15d3cfe453680c516ed/src/V380Client.cs#L103-L187).

Response256B: command offset0, status offset4, ticket offset13. C# mensyaratkan command1168 dan status1001; membaca deviceVersion byte12 dan sessionId u32 offset17. Status1011/1012/1018 diperlakukan sebagai kesalahan username/password/device ID. Java hanya menolak tiga status tersebut apabila command1168, tanpa mewajibkan status sukses atau command benar; ini kelemahan validasi, bukan perilaku protokol yang patut ditiru.

Algoritme password kedua source: AES-128-ECB tanpa padding cipher, dua tahap (konstanta protokol lalu kunci acak16B), hasil diawali kunci acak. Java memakai `SecureRandom`, encoding ISO-8859-1, zero-fill ke blok16B berikutnya (`J/CameraAuth.java:20–46`). C# memakai `Random`, ASCII, buffer48B zero-fill, hasil64B (`C/V380Client.cs:577–601`). Java selanjutnya memotong hasil menjadi32B, sehingga password lebih panjang memerlukan perhatian. Konstanta tidak dicantumkan. Ini **autentikasi lokal kamera**; bukan proses login akun aplikasi/cloud atau ekstraksi token akun.

## Negosiasi stream dan framing

Request301 LAN256B: deviceId offset4, field0 offset8, u16 nilai20 offset12, ticket offset14, audio selector4097 offset22, quality1 offset26. C# mengomentari audio4096=off/4097=on dan quality0=SD/1=HD; semantik tersebut masih observasi source. C# membaca response401: result u32 offset4, communicationVersion u16 offset8, width u32 offset10, height u32 offset14, maxPackSize u32 offset18, karakteristik audio byte22/23/24 (`C/V380Client.cs:223–299`).

Request303 berbeda: Java menulis u32 hasil response401 offset4 ke request offset4 (`J/CameraProtocol.java:128–141`); C# menulis u16 `0x3001` pada offset4 (`C/V380Client.cs:304–310`). Nilai/struktur benar untuk kamera pengguna belum ditentukan.

Header fragmen12B yang disepakati kedua implementasi:

| Offset | Ukuran | Makna observasi |
|---:|---:|---|
|0|1|kelas paket0x7F untuk media; Java mengabaikan0x6F sebagai keepalive|
|1|1|jenis fragmen/frame|
|2|1|belum diparse|
|3|2LE|jumlah fragmen|
|5|2LE|indeks fragmen dari0|
|7|2LE|panjang payload|
|9|3|belum diparse|

Fragmen video digabung, lalu header internal16B dilepas. C# menafsirkan u32 frameId offset0, u16 frameType4, u16 frameRate6, u64 timestamp8 (`C/V380Client.cs:383–391`). Java menyebutnya timestamp header dalam komentar tetapi tidak memparse field (`J/CameraProtocol.java:27–29,179–182`). Data berikutnya diharapkan Annex-B H.264/H.265.

Java mendukung tipe video00/01 dan audio16 (melepas4B audio header); C# tipe video00/01/28/29 dan audio1A (melepas16B). C# mengaitkan28/29 dengan H.265 pada RTSP path (`C/RtspServer.cs:71–82`). Ini bukti variasi firmware, bukan izin menyimpulkan semua tipe setara.

## Dekripsi media dan decoding

C# mengaktifkan dekripsi apabila deviceVersion>30 (`C/V380Client.cs:299,315`). Kunci AES16B dibangun dari ticket LE4B dan konstanta protokol12B (`:569–575`); nilainya sengaja tidak disalin. Video jalur biasa: AES-ECB decrypt64B pada setiap stride80B, hanya bila64B tersedia;16B berikutnya dilewati (`:522–530`). Audio: decrypt setiap blok16B lengkap, sisa akhir dibiarkan (`:532–544`). CommunicationVersion21 memilih `DecryptMediaPre2k`; pemanggilan video dan audio keduanya memakai mode1 (`:395–398,476–479`), sehingga cabang pembatas2048B mode0 dalam fungsi tidak tercapai dari call site tersebut (`:546–567`). Source secara eksplisit menandainya belum diuji.

Android mengirim hasil reassembly langsung ke `H264Decoder.decode` (`J/StreamActivity.java:68–76`), tanpa tahap dekripsi media. Decoder memisahkan start code Annex-B3B/4B, mengumpulkan SPS/PPS, lalu `MediaCodec.createDecoderByType("video/avc")`, konfigurasi CSD dan `Surface`, dequeue/queue input dan release output untuk render (`J/H264Decoder.java:44–108,122–150`). Tidak diperlukan native vendor untuk jalur ini. Namun ukuran awal dipatok1920×1080, timestamp dibuat dari `nanoTime`, dan input diberikan per NAL; ukuran/resolusi dinamis, access-unit boundaries, decoder discontinuity, H.265, sinkronisasi audio, serta perubahan SPS belum ditangani lengkap.

C# tidak mendecode semua stream untuk RTSP; ia mengemas ulang NAL ke RTP interleaved TCP (`C/RtspSession.cs:206–310,332–360`). Snapshot/MJPEG menggunakan H264Sharp atau executable FFmpeg, bukan library `.so` APK (`C/SnapshotManager.cs:49–59,186–238`; dependency `V380Decoder.csproj:13–14`). Dukungan H.265 RTP tidak membuktikan snapshot H.265: fallback snapshot tetap H264Sharp. Clock RTP video dibuat sintetis dengan tick7500 pada clock90kHz (~12fps), audio PCMA8kHz dengan chunk160 (`C/RtspSession.cs:26–28,210–212,315–328`); clock ini bukan timestamp kamera yang sudah terverifikasi.

## P2P dan relay

Source C# memiliki cloud relay, **bukan bukti implementasi P2P UDP langsung/NAT hole punching**. `Program.cs:70–93` meminta relay dari dispatch kemudian membuat `V380Client` dengan IP relay. Dispatch memakai HTTP JSON berisi deviceId/platform/timestamp/signature SHA-1, memilih IP server yang menerima TCP8800 (`C/DispatchRelayServer.cs:10–58`). Domain endpoint dan suffix signature tidak perlu dicantumkan untuk menerangkan alur; tidak ada request yang dikirim dalam analisis ini.

Cabang Cloud memakai layout1167 berbeda: hostname turunan deviceId offset17, port67, username71, password103 (`C/V380Client.cs:114–124`);301 memakai hostname8, port58, deviceId62, ticket66, session70, quality74 (`:234–245`). Ini harus terpisah dari struct LAN. Tidak ditemukan implementasi rendezvous UDP kamera, NAT classification, STUN, retransmit P2P, atau pemilihan rute direct-versus-relay. `OnvifDiscovery` adalah pengumuman server bridge yang dibuat tool, bukan discovery vendor kamera; jangan mencampurkannya dengan `DeviceDiscovery`.

## PTZ dan kontrol

C# mengirim array16B melalui socket stream yang telah terautentikasi (`C/V380Client.cs:661–677`). PTZ dimulai u32LE command170 (`0xAA`); word LE offset8 membedakan kiri1001/kanan1002, word offset10 membedakan atas1003/bawah1004, stop mengembalikan kedua word ke1000 (`C/V380Commands.cs:10–14`). Field lainnya hanya konstanta dalam source; semantik speed/channel/ack belum terjelaskan. Tidak ada parsing acknowledgement untuk memastikan motor bergerak/berhenti.

Kontrol lampu command196, mode gambar197, flip190 pada array16B (`C/V380Commands.cs:15–21`). REST route hanya meneruskan call dan menjawab sukses tanpa memeriksa bool hasil kirim (`C/WebServer.cs:154–167`). Adapter ONVIF mengubah velocity menjadi arah dan memasang timer stop (`C/OnvifHandler.cs:162–178`). Untuk Android, kirim stop pada lepas tombol, cancel gesture, lifecycle pause, timeout, dan sebelum disconnect; validasi dengan kamera milik pengguna diperlukan.

## Batasan source yang berdampak pada client baru

1. TCP tidak menjaga batas pesan. Java `recvAtLeast` hanya melakukan satu `read`, dapat menolak read parsial atau mengonsumsi data berikutnya (`J/CameraProtocol.java:212–218`). C# `ReceiveData` terus membaca sampai max dan dapat blocking setelah partial receive karena tidak memasang read timeout (`C/V380Client.cs:610–628`). Bangun parser incremental dengan panjang/variant yang tervalidasi.
2. C# hanya mengecek response401 minimal8B namun membaca hingga byte24 (`:254–285`); validasi panjang harus sesuai field yang dipakai.
3. Java mengecek indeks fragmen berurutan (`J/CameraProtocol.java:171–175`); C# menggabungkan tanpa mengecek urutan atau jumlah fragmen yang benar (`C/V380Client.cs:369–375`). Batas total ukuran frame, batas jumlah fragmen, cancellation, reset saat timeout/reconnect, serta pemulihan desinkronisasi perlu didefinisikan.
4. Header tidak dikenal dilewati tanpa selalu mengonsumsi payload pada kedua parser. Batas payLen C#20000 versus Java65535 merupakan keputusan implementasi, bukan batas protokol terbukti.
5. Log source C# mencetak ticket/kunci derivasi dan Android mencetak ticket. Client independen harus memakai logging tersanitasi; jangan membawa logging tersebut ke implementasi.
6. C# menganggap audioPCMA8kHz, Java menyebut ADPCM dan tidak memutarnya. Jenis audio kamera pengguna belum dipastikan.
7. Tidak ada bukti kedua source mencakup seluruh varian V380, V380 Pro, panorama, multi-lens, model H.265, atau versi6.4.90. Bandingkan field dan primitive crypto dengan native APK, lalu uji hanya kamera pengguna.

## Lisensi

Android memiliki [`LICENSE:1–21`](https://github.com/umar14/v380-android/blob/afe9b13f1e119efa15d41fa604bae6a38f1d6fe7/LICENSE#L1-L21) MIT dan README mengatribusikan protokol ke mzyy94/v380 (`README.md:9–20,171–174`). Ada ketidakkonsistenan narasi README personal/educational dengan file MIT; provenance kode turunan tetap perlu diperiksa sebelum redistribusi.

Pada commit C# yang diperiksa tidak ada `LICENSE`, `COPYING`, SPDX identifier, atau pemberian lisensi eksplisit yang ditemukan dalam tracked source/README. Akses publik sendiri tidak menetapkan izin penyalinan/distribusi. Gunakan observasi protokol untuk implementasi independen; jangan mengasumsikan source C# berlisensi MIT. Penilaian ini sebatas inventaris artefak, bukan nasihat hukum.

## Rekomendasi Android modular

Gunakan satu app dengan paket/modul kecil: `discovery` (UDP dan model device), `transport` (socket + deadline/cancellation), `protocol` (codec pesan LE dan reassembly), `session` (state machine auth→stream→reconnect), `media` (decrypt per profil firmware + access-unit parser + MediaCodec), `control` (PTZ/light/flip), `ui` (lifecycle dan surface). Satu interface transport cukup untuk LAN sekarang; relay/P2P menjadi adapter terpisah setelah terbukti pada APK, tanpa mencampurkan layout request.

Prioritas implementasi: LAN discovery → auth dengan kredensial yang pengguna masukkan → parsing301/401/303 menurut profil → reassembly terbatas → dekripsi sesuai versi → Annex-B/access-unit → MediaCodec H.264/HEVC sesuai hasil negosiasi → PTZ + stop. Gunakan queue terbatas, lifecycle cancellation, buffer reuse, timestamp terdefinisi, dan simpan rahasia melalui Android Keystore bila persistensi diperlukan. Uji parser dengan fixture sintetis/sanitasi, termasuk TCP read parsial, gabungan pesan, fragmen hilang, panjang invalid, reconnect, dan rotasi surface. P2P vendor tetap pekerjaan terpisah hingga native APK menjelaskan handshake/routing-nya.
