# Talkback IoT/V3 — kontrak statis dan batas implementasi

Tanggal: 15 Oktober 2026. APK V380 6.4.90 SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`.
Tidak ada APK/library vendor yang dijalankan, koneksi kamera/cloud, pembacaan credential runtime, atau acceptance kamera fisik.

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
