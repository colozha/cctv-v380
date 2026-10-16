# Talkback IoT/V3 — kontrak statis dan batas implementasi

Tanggal: 15 Oktober 2026. APK V380 6.4.90 SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`.
Riset statis awal tidak menjalankan APK/library vendor atau menghubungi kamera/cloud. Observasi metadata kamera oleh client independen yang kemudian diminta pengguna dicatat terpisah di bagian tindak lanjut; tidak ada acceptance speaker fisik.

**Status terbaru:** laporan pengguna membatalkan dugaan kompatibilitas AAC uplink: kamera mengeluarkan decitan keras dan ucapan tidak terdengar. Jalur mikrofon kini mengikuti PCM21 vendor: PCM100 diteruskan; untuk metadata102/mono16kHz digunakan blok IMA ADPCM256B. Encoder/passthrough AAC mikrofon dihapus. Perbaikan ini baru diverifikasi offline; speaker kamera belum diverifikasi. [Hasil terkini](../validation/talkback-noise/results.json). Bagian AAC sebelumnya adalah riwayat percobaan yang gagal pada speaker pengguna.

## Bukti

Excerpt REA `talkback-*.sanitized.json` mempertahankan Evidence ID asal, identitas input, versi engine dan limitations; bukan bundle kanonis pengganti. Kolektor `evidence/talkback-collect-rea.py` menjalankan analisis Java read-only. Disassembly ARM64 `evidence/talkback-native.sanitized.json` berasal dari system Capstone5 C API dan ELF metadata; bukan dekompilasi native REA. Input `libhsMediaLibrary.so` SHA-256 `1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc`.

## Kontrak PCM yang dibuktikan

- Java `MicrophoneRecorder.init`: mono PCM16; `jniCallOnRecvMediaInfo` menerima 8000/16000 Hz. Loop recorder mengagregasi buffer1010B lalu memanggil JNI dengan counter, argumen21 dan timestamp milidetik. Argumen21 adalah jenis input internal, **bukan** enum codec wire100.
- JNI `0x97e2c` → manager `0x81138` → player `0x85e80` → base `0x3d000`. Base mengecek flag capture `+0xd0` lalu memasukkan item audio bertipe2 dan format input21 ke ring queue.
- Capability Java `isSupportSpeak`: `stream[3] & 2` (ABL_AUDIO_PLAY), terpisah dari capture dan video two-way. DEX ordinal dan mask dilacak sebagai bukti pelengkap.
- V3 `startSpeak 0x543dc` menghapus flag format sudah terkirim `+0x41c`, lalu base start `0x3cf94` mengaktifkan flag lokal `+0xd0`. Base stop `0x3cfa8` hanya menghapus flag tersebut dan mengembalikan1: **tidak ada paket stop mikrofon pada fungsi ini**. Independent client menghentikan capture/antrean lokal, bukan mengarang action stop atau mengirim konfigurasi nol dari jalur video-call.
- V3 receiver `0x50660` menyimpan socket live yang berhasil dipakai ke `+0x3e0`; pengirim audio memakai descriptor yang sama di `0x4e810` dan `0x4ebb0`. Tidak ada socket talkback kedua pada cabang ini.
- `parseLiveMediaInfo 0x9436c..0x943f0` memulihkan `params.aattr:{codec,samplerate,bits,chns}`. Receiver `0x50df4..0x50e18` menyalin ke V3 `+0x400..0x40c`, yang dipakai pengirim audio.
- Sebelum frame audio pertama, `0x4e670..0x4e828` mengirim method197122 melalui common JSON terenkripsiCBC: `params:{down:{aattr:{codec,samplerate,bits,chns}}}`. Tidak ada `callid` pada cabang audio-only ini. Respons197123/result1000 dikenali receiver `0x51bc4..0x51bdc`; client memperketat korelasi id/method/channel dan menunggu ACK sebelum capture. ACK hanya penerimaan protokol, bukan bukti speaker berbunyi.
- Input audio21 dengan codec100 masuk cabang `0x4e90c..0x4e948`: salin PCM tanpa encoder. Codec lain masuk encoder2001; kontrak encoder tersebut tidak dipulihkan di sini dan tetap terkunci.
- `0x4ea88..0x4eb2c` membentuk frame format0/type4, sequence dari queue, ukuran PCM, timestamp input, lalu mengenkripsi dan membungkus command MEDIA939722784, version1/security2/SID sesi/magic0.
- `iotc_ms_frame_cipher_init 0x935d0..0x93620` pada encrypt audio menetapkan prefix0, chunk `floor(payload/16)*16`, gap0. Header32B dan chunk dienkripsi berurutan dengan CBC NoPadding; tail PCM kurang dari16B disalin plaintext oleh `0x93a30..0x93a7c`. State CBC direset per paket; key/IV mengikuti sesi V3 yang sudah didokumentasikan.

Header inner32B mengikuti offset eksplisit fungsi encrypt: format0@0, type4@1, sequenceLE32@2, payloadSizeLE32@6, timestampLE64ms@10, prefix0@18, chunkLE32@22, gap0@26, view0@30, reserved0@31. Ukuran PCM1010B → chunk1008B → tail2B. View0 dan byte reserved0 adalah pembatasan client satu-view dan hasil buffer output nol; tidak mengaktifkan view lain.

Contoh JSON sintetis (envelope id/ts/session dan enkripsi mengikuti IoTCommonProtocol):

```json
{"down":{"aattr":{"codec":100,"samplerate":8000,"bits":16,"chns":1}}}
```

Fixture PCM sintetis:1010 byte nol, sequence1, timestamp1000ms, key/IV32 byte sintetis. Tidak menyimpan rekaman mikrofon nyata.

## Gerbang dan keputusan implementasi

Gerbang wire **lulus hanya untuk subset PCM100 mono16, 8/16kHz, view0, IoT LAN**. Capability true saja tidak cukup; metadata autentikasi yang cocok harus menyatakan format tersebut. ADPCM101, AAC dan codec lain, metadata hilang, Legacy, serta jalur cloud tetap tidak aktif. Tidak menebak bit permission; penolakan1109 tetap ditangani sebagai penolakan kamera.

Stop normal menghapus izin kirim lokal, menghentikan/release recorder dan membuang buffer. Write yang sedang berlangsung atau start yang sudah dikirim namun dibatalkan membuat socket bersama tidak pasti: tutup socket dan pulihkan sesi video tanpa otomatis mengaktifkan mikrofon. Capture gagal setelah negosiasi namun sebelum pengiriman tidak membutuhkan paket stop rekaan. Batas client: start5s, write500ms, cleanup1s, PCM tertunda≤200ms termasuk buffer producer/in-flight.

## Batas verifikasi

Instruksi statis bukan bukti suara terdengar, latency firmware, audio routing Android/OEM, atau kompatibilitas kamera pemilik. Pengujian offline memakai PCM palsu dan socket sintetis; hasil aktual berada di `../validation/talkback/results.json`. Dukungan ADPCM dan kontrak callid/video-call bukan bagian implementasi ini.

Validasi akhir:106 tes JVM lulus, termasuk17 tes talkback; build debug/instrumentation dan lint0 error. Empat vector protokol lulus pada Android API33. Tes UI native ditunda atas pilihan pengguna setelah perangkat tertahan Dozing dan input ADB ditolak; tidak ada screenshot/UI runtime, microphone routing atau acceptance speaker yang diklaim lulus.

## Perbaikan metadata Live — 15 Oktober 2026

Laporan pengguna: panel menampilkan “Format suara kamera belum diketahui. Sambungkan ulang untuk memeriksa”. Ini mengidentifikasi gerbang metadata kosong, bukan membuktikan codec tertentu atau speaker tidak didukung.

Reader kini membedakan menunggu informasi, `aattr` tidak disertakan, atribut invalid dan envelope yang tidak memenuhi pemeriksaan COMMAND_NOTIFY/CBC/SID. Metadata audio diproses terpisah dari video: notifikasi audio-only tidak memerlukan `vattr`; notifikasi video-only mempertahankan format audio yang sudah diterima pada stream yang sama. Atribut yang disertakan tetapi invalid atau envelope yang ditolak menghapus otorisasi format dan menghentikan talkback. Format berubah juga menghentikan capture tanpa auto-resume. Pergantian generasi/transport/sesi tetap membatalkan metadata; publikasi reader dan reset generasi dipagari lock yang sama. Diagnostik hanya menambah enum alasan, tanpa payload atau SID.

Permintaan Live tetap `up.audio:0`. Pemeriksaan ulang spesifikasi IoT dan excerpt native yang tersedia menunjukkan parsing `params.aattr` dan penggunaannya oleh pengirim, tetapi tidak membuktikan bahwa firmware hanya mengirim atribut saat `up.audio:1`. Tidak ada perubahan negosiasi berdasarkan dugaan, probing codec, atau fallback PCM. Jika kamera benar-benar tidak mengirim `aattr`, fitur tetap terkunci dengan alasan khusus. Penentuan kebutuhan negosiasi firmware memerlukan bukti tambahan; perbaikan ini tidak menjamin tombol kamera pengguna langsung aktif.

Regresi sintetis menguji reader sebenarnya untuk pembaruan parsial, audio-only, format invalid/unsupported, envelope salah, penghentian capture dan reset/stale-reader. Hasil revisi ini dicatat terpisah di `../validation/talkback-metadata/results.json`; hasil lama di `../validation/talkback/results.json` tetap historis. Tidak ada koneksi kamera, capture mikrofon atau verifikasi speaker fisik.

## Koreksi SID notifikasi dan observasi kamera — 15 Oktober 2026

Bagian ini menggantikan syarat kesamaan SID header notifikasi pada uraian sebelumnya. Observasi LAN yang diminta pengguna menemukan NOTIFY939722786/security2, metadata audio hadir, tetapi SID header tidak sama dengan SID hasil login. Nilai SID asli tidak disimpan. Format yang diumumkan adalah codec102,16000Hz,16-bit,1 kanal. Ini bukti sesi yang diamati, bukan klaim kompatibilitas seluruh firmware atau keberhasilan speaker.

Disassembly tambahan receiver dari library/APK yang sama berada di `../validation/talkback-envelope/native-receiver.json`: pembacaan header pada0x50eb8..0x50ec8 membaca command/version/security/length; dispatch pada0x50944..0x50974 membaca command/security/length dan beralih ke NOTIFY;0x50c44..0x50c5c mendekripsi menggunakan mode header dan material sesi. Jalur0x50cf4..0x50e18 mengenali197124 dan menyalin format audio tanpa membandingkan byte SID header dengan SID login. Perbandingan generasi thread pada jalur body-read berbeda dari byte SID header. Bukti ini bersifat statis; kode vendor tidak dieksekusi.

Client menghapus hanya persyaratan kesamaan SID untuk metadata197124 pada socket live milik sesi saat ini. Tetap memerlukan COMMAND_NOTIFY, CBC2 yang berhasil didekode memakai key sesi, kepemilikan object sesi/socket dan generasi yang cocok. SID permintaan keluar dan korelasi ACK start tidak berubah. Mode ECB/plaintext tetap ditolak untuk otorisasi format talkback. Diagnostik merekam jenis paket, mode, boolean kecocokan SID, keberadaan atribut dan angka format; tidak menyimpan SID mentah, JSON mentah, key atau audio. Alasan jenis paket dan mode enkripsi kini terpisah.

Codec102 tetap tidak didukung oleh pengirim PCM100; tidak ada encoder atau perubahan `up.audio` dalam perubahan ini. Karena itu perbaikan penolakan SID tidak membuat kamera ini siap bicara. Kontrak codec102/encoder tetap pekerjaan berikutnya, tanpa menyimpulkan nama codec hanya dari angka atau catatan pengaturan vendor terdahulu.

Observasi lanjutan memakai reader produksi tanpa Surface/decoder, menyimpan ringkasan metadata saja, tidak menampilkan atau menyimpan video dan tidak memulai mikrofon/PTZ. Hasil sebelum/sesudah, source/APK hashes, validasi sintetis dan batas ada di `../validation/talkback-envelope/results.json`. Hasil sebelum perubahan tidak membuktikan video stabil (report `streaming:false`); observasi sesudah sengaja hanya menguji metadata, bukan rendering. Pengujian speaker belum dijalankan.

## AAC-ADTS codec102 — 15 Oktober 2026

Konstanta DEX yang sudah tersimpan pada `evidence/talkback-capability.dex.txt` menetapkan `MV_MEDIA_CODEC_AUDIO_ADTS_AAC=102`; pemetaan ini tidak disimpulkan dari screenshot. Jalur V3 untuk input audio yang **sudah dikodekan**, jenis internal22, melompat dari0x4e558..0x4e55c ke0x4e918, menyalin payload tanpa encoder, lalu membungkusnya sebagai format0/type4 pada0x4ea88. Negosiasi audio-only tetap menyalin metadata sesi ke `down.aattr` pada0x4e6d8..0x4e764, tanpa callid. [Proyeksi instruksi](../validation/talkback-aac/native-encoded-audio.json).

Inspeksi `libmideaCodec.so` juga membuktikan factory2001 adalah **ADPCM**, sedangkan2002 adalah AAC; jangan memakai cabang PCM21/non100 sebagai encoder AAC. [Factory dan jump table](../validation/talkback-aac/native-factory.json). `HSAACEncoder.initEncoder` memuat parameter rate/channel/bits dan konfigurasi AAC; output format disetel1 (ADTS). [Instruksi encoder](../validation/talkback-aac/native-encoder.json). Tidak ada library vendor yang dimuat atau dijalankan oleh aplikasi atau pengujian.

Client memakai `MediaCodec` Android: AAC-LC,16000Hz,mono,bitrate target32000bps, input PCM16 little-endian1024 sampel/2048 byte. Raw AAC access unit diberi header ADTS MPEG-4 LC7 byte, tanpa CRC, satu raw-data block; panjang total dibatasi8191 byte. AudioSpecificConfig output wajib LC/16kHz/mono; format lain dihentikan. Satu access unit dikirim per paket dengan framing/enkripsi audio yang sama seperti PCM: CBC header32B dan seluruh blok16B payload, tail kurang16B tetap mengikuti wire vendor. Counter bertambah per access unit; timestamp dimulai pada input pertama lalu mengikuti1024 sampel per64ms. Codec-config buffer tidak dikirim sebagai audio.

Satu encoder dimiliki tiap attempt, disiapkan tanpa membuka capture. Mikrofon tetap hanya dimulai sesudah ACK start yang berkorelasi dan pemeriksaan izin/foreground. Saat stop/cancel/keluar, recorder berhenti, coroutine dibatalkan, encoder dilepas dan buffered speech dibuang tanpa flush ke kamera. Kegagalan write tetap diteruskan sebagai kegagalan transport, bukan diubah menjadi kegagalan encoder. Buffer PCM aplikasi AAC paling banyak3×2048=6144B (192ms pada16kHz); backlog input encoder dibatasi3 frame. Buffer internal AudioRecord/MediaCodec berada di luar hitungan buffer PCM aplikasi; tidak ada klaim latency fisik≤200ms. AAC8kHz, stereo, HE-AAC dan ADPCM tetap terkunci.

[Dokumentasi MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) dan [MediaFormat.KEY_IS_ADTS](https://developer.android.com/reference/android/media/MediaFormat#KEY_IS_ADTS) menjadi referensi API Android: KEY_IS_ADTS adalah pengaturan decoder, sehingga ADTS untuk output encoder ditambahkan client sendiri. Tidak ada dependensi tambahan.

Validasi source ini:120 tes JVM lulus (23 talkback), build debug/instrumentation berhasil, lint0 error/35 warning/1 hint. Delapan tes sintetis Android API33 lulus, termasuk encoder→ADTS→paket terenkripsi→decoder dan propagasi kegagalan write. Observasi kamera terarah juga lulus: capabilitytrue, metadata codec102/16000Hz/16-bit/mono diterima dan `metadataBlock:null`. Observasi memakai reader tanpa Surface/decoder dan tidak mengirim audio, sehingga bukan acceptance video/speaker. Capture mikrofon nyata, ACK start pada kamera dan suara speaker belum diuji. [Hasil lengkap](../validation/talkback-aac/results.json).

## Koreksi decitan: ikuti jalur mikrofon PCM21 — 15 Oktober 2026

Pengguna melaporkan bahwa setelah Mulai bicara, speaker mengeluarkan decitan keras dan tidak menyampaikan ucapan mikrofon. Ini adalah bukti kegagalan jalur AAC sebelumnya pada unit yang dicoba, bukan acceptance speaker. Posisi HP relatif terhadap kamera dan kontribusi feedback akustik belum dikonfirmasi; pengguna tidak diminta mengulang decitan.

Kesalahan implementasi sebelumnya: metadata codec102 dan keberadaan cabang encoded-audio22 dipakai untuk memilih AAC uplink. Padahal `MicrophoneRecorder` Java mengirim PCM1010B dengan jenis input21. Native V3 membedakan21 dan22; untuk input21, codec100 disalin, sedangkan nilai non100 menuju encoder2001. Factory yang sudah dipulihkan menetapkan2001=HSADPCMEncoder. ACK dan tes AAC encode/decode lokal tidak membuktikan speaker menerima cabang22.

[Disassembly tambahan](../validation/talkback-noise/native-adpcm.json) menetapkan `HSADPCMEncoder.encodeData`0x25d20..0x25d78 memanggil `GM_ima_adpcm_encode` dengan ukuran256 dan mengembalikan blok256B. Fungsi0x249d8..0x24b00 menulis predictor awalLE16, step-index awal dan reserved0, kemudian mengodekan504 sampel berikutnya dari total505 sampel/1010B, nibble rendah terlebih dahulu. Step-index dibawa antarblok; predictor awal ditulis ulang dari sampel pertama tiap blok. [Tabel publik](../validation/talkback-noise/adpcm-tables.json) berisi89 step dan8 penyesuaian index. Client menggunakan integer penuh dan clamp untuk transisi PCM ekstrem, tanpa meniru overflow short/index di luar batas.

Client kini memakai satu encoder IMA konkret per attempt, tanpa MediaCodec AAC atau library tambahan. PCM tetap dibaca1010B pada rate sesi; sample/counter/timestamp mengikuti alur capture yang sama seperti vendor. Untuk subset kamera yang diamati (metadata102/16000Hz/mono16), `down.aattr` tetap mengikuti native, tetapi payload mikrofon21 adalah blok IMA256B. Field metadata102 tetap nilai negosiasi; jangan menyebut payload mikrofon ini AAC atau menggeneralisasi ke firmware/format lain. Framing format0/type4 dan CBC tetap; blok256B terenkripsi penuh setelah header32B. Validator paket menolak ADTS, panjang bukan256, index di luar0..88 dan reserved bukan0 pada jalur ini.

Tidak ada capture, koneksi kamera atau pemutaran speaker selama koreksi ini.123 tes JVM lulus, termasuk golden vector silence/constant/ramp, saturasi, codec102→ACK→blok IMA pada reader/sender sesi sebenarnya, penolakan ADTS, dan lifecycle yang ada. Build debug/instrumentation dan lint0 error/35 warning/1 hint berhasil. Decoder host independen libsndfile1.2.2 berhasil membaca nada440Hz sintetis10100 sampel pada16kHz/mono: korelasi0.9997469 dan normalized RMSE0.0225082. Itu membuktikan blok dapat didekode secara independen; bukan suara kamera. Setelah HP tersambung kembali, APK debug dan instrumentation dipasang tanpa menghapus profil. Lima tes protokol sintetis lulus pada Android API33; mikrofon nyata dan acceptance speaker revisi ini belum diuji. Tidak ada paket audio dikirim ke kamera selama validasi ini. [Batas dan bukti lengkap](../validation/talkback-noise/results.json).
