# Implementasi Talkback Mikrofon HP → Kamera

Berkas tujuan: `analysis/talkback-implementation-plan.md`  
Tanggal: 15 Oktober 2026  
Status: implementasi subset PCM100 tersedia; validasi UI native ditunda atas pilihan pengguna, acceptance kamera belum dijalankan. Lihat `talkback-wire.md` dan `../validation/talkback/results.json` untuk hasil aktual.

## 1. Tujuan dan keputusan

Tambahkan pengiriman suara mikrofon HP ke speaker kamera pada layar Live, dengan tombol **Mulai bicara / Berhenti bicara**.

- Versi pertama hanya mendukung jalur **IoT/V3 lokal** yang kontraknya berhasil dibuktikan.
- Audio kamera → HP, percakapan dua arah, Legacy V1/V2, cloud/P2P, perekaman suara, dan background service berada di luar cakupan.
- Fitur tetap eksperimental debug. Dukungan firmware tidak disimpulkan dari keberhasilan video, capability, atau ACK.
- Gunakan sesi, transport, coroutine, kriptografi, komponen Material, dan framework pengujian yang tersedia. Jangan menambahkan library proprietary atau lapisan arsitektur baru.

## 2. Gerbang bukti sebelum implementasi wire

Riset REA membuktikan `startSpeak()`, `stopSpeak()`, input `AudioRecord` mono PCM 16-bit, penyesuaian frekuensi 8/16 kHz, serta pemanggilan JNI `sendSpeakAudioData()`. Capability bicara IoT diperiksa melalui `(stream[3] & 0x02) == 0x02`.

Kontrak setelah JNI harus dibuktikan sebelum pengiriman diaktifkan:

1. Arsipkan ulang hasil REA terkait start/stop, recorder, metadata frekuensi, dan capability sebagai excerpt tersanitasi dengan Evidence ID asal.
2. Telusuri JNI, pengiriman audio dasar, dan `HSLiveDataV3Transmitter.speakAudioSendThreadFunc()` secara statis.
3. Pulihkan command start/stop, codec keluar, format frame, ukuran payload, sequence, timestamp, enkripsi, respons/error, serta pemilihan socket dan generasi protokol.
4. Pastikan apakah talkback membutuhkan negosiasi tambahan, izin perangkat tertentu, atau jalur cloud. Jangan menebak arti bit `perm`.
5. Dokumentasikan kontrak dan contoh paket sintetis dalam `analysis/talkback-wire.md`, lalu perbarui indeks bukti.

Nilai argumen JNI `21` tidak dianggap sebagai codec wire. Format media masuk juga tidak digunakan sebagai format audio keluar tanpa bukti.

**Syarat melanjutkan:** jalur IoT lokal memiliki kontrak start, audio, stop, codec, transport, dan penanganan kegagalan yang cukup untuk serializer serta fixture independen. Bila belum lengkap, fitur tetap terkunci dan dokumentasikan kekurangan bukti; jangan mengirim paket perkiraan.

Tidak menjalankan APK vendor atau menghubungi kamera pada tahap ini.

**Hasil gerbang:** kontrak native membuktikan jalur PCM100 mono16 pada8/16kHz melalui socket live; codec lain tetap terkunci. Native start/stop mikrofon mengubah flag lokal dan tidak mengirim paket stop. Implementasi mengikuti penghentian lokal tersebut, tanpa mengarang command; write/start yang dibatalkan saat hasil transport tidak pasti menutup socket bersama. Rincian alamat instruksi dan bukti ada pada spesifikasi wire.

## 3. Perubahan implementasi

### Capability dan kepemilikan sesi

- Perluas `CameraCapabilities` dengan `talkback: Boolean?`: `null` berarti belum diketahui. Dukungan wire/profil diperiksa terpisah.
- Tambahkan state talkback tersendiri: `Idle`, `Starting`, `Sending`, `Stopping`, dan `Error`. Video tetap memakai `SessionState` yang ada.
- Tambahkan operasi sesi `startTalkback(profileId)` dan `stopTalkback()`, beserta state yang dapat diamati ViewModel.
- Start hanya diizinkan saat Live aktif, Activity resumed, video `Streaming`, izin mikrofon tersedia, capability benar, kontrak profil didukung, dan maintenance tidak memblokir operasi.
- Ikat pekerjaan ke identitas profil, sesi autentikasi, dan generation. Double tap dan callback lama tidak boleh membuat pengirim kedua.

### Input mikrofon dan pengiriman

- Gunakan satu komponen konkret `TalkbackRecorder` berbasis `AudioRecord`, tanpa service atau session owner tambahan.
- Tambahkan izin `RECORD_AUDIO`. Minta izin ketika pengguna menekan Mulai; setelah izin diberikan, pengguna menekan Mulai kembali. Callback izin tidak langsung mengaktifkan mikrofon.
- Gunakan PCM mono 16-bit dengan sample rate yang dibuktikan oleh negosiasi kamera. Metadata yang belum cukup membuat start ditolak.
- Inisialisasi recorder boleh dilakukan sebelum negosiasi; perekaman dimulai setelah tahap start protokol berhasil.
- Tangani partial read, nilai error, buffer tidak valid, dan kegagalan inisialisasi. Jangan mengirim bagian buffer yang tidak terisi.
- Implementasikan hanya codec yang dibuktikan untuk jalur talkback sasaran. Tidak menambahkan transcoding umum atau mendukung seluruh inventaris codec.
- Gunakan buffer terbatas maksimal 200 ms PCM. Jika pengirim tertinggal sampai buffer penuh, hentikan talkback dengan error; jangan mengumpulkan suara lama.
- Semua buffer hanya berada di memori dan dibersihkan setelah penggunaan.

### Transport dan penghentian

- Ikuti topologi socket yang dipulihkan dari native. Gunakan penulis paket atomik `TcpChannel.write()`; jangan membuat pembaca kedua pada socket live.
- Bila respons melalui socket live, gunakan dispatcher pembaca yang sudah ada dan korelasi transaksi yang ketat.
- Jangan memegang mutex operasi sepanjang perekaman.
- Gunakan batas client: negosiasi start 5 detik, write audio 500 ms, dan cleanup stop 1 detik. Ini batas resource client, bukan semantik firmware.
- Stop segera mencabut izin pengiriman lokal, menghentikan capture, membuang antrean, kemudian melakukan stop protokol secara terbatas.
- Jangan membatalkan write pada socket bersama sambil menganggap video tetap sehat: cancellation `TcpChannel` menutup socket. Jika write macet atau hasil start/stop tidak pasti, tutup transport terkait dan gunakan penanganan kegagalan sesi yang ada.
- Stop harus idempotent dan dipanggil saat pergantian tab/profil/kualitas, kehilangan Surface, disconnect, error autentikasi/stream, reconnect, Activity pause, rotasi, serta disposal.
- Penghentian mikrofon berjalan sebelum pengecualian lifecycle untuk picker atau handoff AP. Reconnect dan recreation tidak pernah mengaktifkan mikrofon otomatis.

### UI dan diagnostik

- Tambahkan panel **Bicara ke kamera** setelah kualitas video dan sebelum PTZ, mengikuti visual Live yang ada.
- Tampilkan tombol mulai/berhenti, status mikrofon, error, dan alasan fitur terkunci.
- Tombol berhenti tetap dapat digunakan saat operasi lain sibuk; selama `Starting`, tombol membatalkan start.
- Label **Mikrofon aktif** menyatakan capture/pengiriman lokal, bukan bukti suara terdengar di kamera.
- Semua copy dan semantics baru tersedia dalam resource Indonesia dan Inggris; target sentuh minimal 48 dp, tema terang/gelap, font scaling, dan TalkBack.
- Tambahkan tahap/detail diagnostik terstruktur untuk start, capture, send, dan stop. Gunakan field allowlist yang ada; tanpa audio, payload, key, SID, atau pesan bebas dari perangkat.
- Bedakan penolakan izin Android, penolakan kamera, codec tidak didukung, kegagalan capture, dan kegagalan jaringan.

## 4. Pengujian dan kriteria penerimaan

| Area | Pemeriksaan wajib |
|---|---|
| Capability/wire | Capability hilang/false/true; profil unsupported; paket sesuai bukti; panjang invalid; respons salah korelasi; penolakan dan timeout. |
| Audio | PCM sintetis, partial read, error recorder, sample rate tidak didukung, antrean penuh, serta buffer tidak terkirim setelah stop. |
| Concurrency | Double tap, stop selama start/write, callback sesi lama, dan start baru saat cleanup berlangsung. |
| Lifecycle | Background, rotasi, pergantian tab/kualitas/profil, Surface hilang, disconnect, reconnect, dan disposal selalu mematikan mikrofon tanpa auto-resume. |
| Transport | Fake socket membuktikan paket tidak bercampur dengan PTZ/feedback; write macet menutup transport dan tidak meninggalkan status video sehat palsu. |
| UI | Izin ditolak/diberikan, disabled reason, seluruh state tombol, Indonesia/Inggris, tema, landscape, font scale 1,3, serta semantics TalkBack. |
| Regresi | Live, PTZ, playback, export, provisioning, dan diagnostik tetap lulus pemeriksaan terkait. |

Jalankan:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Instrumentation dijalankan pada emulator/perangkat yang tersedia menggunakan fixture sintetis dan pengirim palsu; pengujian otomatis tidak merekam suara pengguna atau menghubungi kamera.

Acceptance kamera fisik tetap deferred sampai pengujian koneksi dibuka secara eksplisit. Acceptance nantinya harus membuktikan suara benar-benar terdengar, berhenti setelah tombol Stop/background, dan tidak hidup kembali setelah reconnect. Build, fixture, dan ACK tidak menggantikan bukti tersebut.

## 5. Dokumentasi dan penyelesaian

- Simpan rencana ini pada berkas tujuan.
- Perbarui dokumentasi produk, desain, implementasi, dan README sesuai perilaku akhir serta keterbatasan yang benar-benar tersisa.
- Simpan hasil pemeriksaan di `validation/talkback/`, dengan revision sumber, perintah, hasil aktual, dan batas pengujian.
- Implementasi selesai secara offline ketika kontrak terarsip, pengujian sintetis lulus, seluruh jalur cleanup terverifikasi, dan tidak ada mikrofon aktif tanpa aksi pengguna.
- Status kompatibilitas kamera tetap **belum terverifikasi** sampai acceptance perangkat nyata berhasil.
