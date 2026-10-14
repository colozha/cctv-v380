# V380 LAN

Client Android independen untuk kamera V380 pada Wi-Fi lokal. Versi **0.3.0-experimental** mengimplementasikan autentikasi Legacy/IoT LAN, live video AVC/HEVC dengan MediaCodec, PTZ, konfigurasi Wi-Fi melalui AP terautentikasi, serta rekaman/pengaturan IoT eksperimental. **Pengguna sudah melaporkan login dan video berhasil, tetapi sesi versi0.2.0 terputus setelah beberapa detik; kompatibilitas stabil belum terverifikasi.** Pairing awal IoT yang membutuhkan binding cloud belum didukung. Rincian: [status implementasi dan kompatibilitas](IMPLEMENTATION.md).

Perbaikan0.2.1 mencakup parser frame kecil/sparse cipher, feedback NOTIFY197120, recovery error PTZ/reader, dan diagnostik schema2 yang membedakan error stream dari auth. [Bukti dan batas perbaikan disconnect](analysis/stream-disconnect.md). Pengujian koneksi tidak dijalankan ulang.

## Build dan instalasi

Memerlukan JDK17+ (pengujian memakai Temurin21), Android SDK37.0 dan Build Tools36.0.0. Project memakai AGP9.1.1, Gradle9.3.1, Kotlin bawaan2.2.10/Compose Compiler2.2.10, minAPI26/targetAPI37; versi dipertahankan sesuai rencana.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# Pilihan tes JVM murni: validation/offline-build-command.txt
python3 tools/collect-validation.py --without-connection-tests
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

APK lokal: [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk). Adapter eksperimental aktif hanya pada build debug; tidak ada rilis kamera verified. Hasil pengujian dan hash: [validation/results.json](validation/results.json), [hasil fitur0.3.0](validation/recordings-settings/results.json). Build akhir dan40 tes JVM murni lulus, lint0 error. Tes UI/ekspor Android baru belum berjalan karena emulator crash saat boot. Pengujian koneksi lanjutan ditunda sesuai instruksi pengguna; hasil emulator API26/30/37 sebelumnya ditandai historis karena source telah berubah. Uji integrasi IoT sintetis terakhir gagal saat teardown ActivityScenario dan belum diulang. API35 belum menjalankan suite runtime.

## Kamera sudah terhubung Wi-Fi

1. Sambungkan HP ke Wi-Fi yang sama dengan kamera, lalu pilih **Cari di Wi-Fi**. Izinkan akses jaringan lokal bila diminta Android.
2. Pilih kamera hasil discovery, beri nama, lalu simpan. Input manual tersedia untuk IP/port/device ID; pilihan protokol manual hanya untuk keluarga yang sudah diketahui.
3. Buka kamera, masukkan password **lokal kamera**, lalu **Sambungkan**. Username hanya digunakan oleh profil Legacy. Password akun V380, password hotspot, dan password Wi-Fi rumah adalah input berbeda; aplikasi tidak mengasumsikan `admin` atau password kosong.
4. SD/HD dan PTZ aktif setelah stream/capability valid. Tahan arah untuk bergerak, lepaskan untuk stop. Saat aplikasi masuk background, stream ditutup.

Opsi **ingat password** default nonaktif. Bila diaktifkan, akses disimpan terenkripsi dengan Keystore dan dikecualikan dari backup. Menghapus profil menghapus ciphertext-nya. Akses vendor tidak dipindahkan otomatis. Bila tidak mengetahui password lokal dan kamera tidak menawarkan pairing offline, jangan mengisi password akun sebagai penggantinya.

## Rekaman dan pengaturan

Buka detail kamera → **Rekaman** atau **Pengaturan**, lalu sambungkan akses lokal. Fitur baru tersedia eksperimental untuk sesi IoT; dukungan Legacy belum diimplementasikan.

- **Rekaman:** pilih tanggal dan Semua/Peristiwa, lalu Muat rekaman. Waktu memakai zona Android; cocokkan dengan kamera. Pilih segmen untuk Putar/Jeda/seek.
- **Simpan rekaman:** centang **Simpan video tanpa audio**, pilih lokasi dokumen, lalu sambungkan kembali jika document picker menutup sesi foreground. Aplikasi menyalin satu segmen penuh ke MP4. Audio, batch dan resume belum tersedia. Status tersimpan baru tampil setelah file selesai dan disalin; bila pembatalan gagal menghapus file parsial, hapus file tujuan yang disebutkan sebelum mencoba lagi.
- **Pengaturan:** muat nilai kamera, ubah orientasi/lampu/perekaman yang tersedia, lalu simpan perubahan. Aplikasi membaca ulang sebelum menyatakan tersimpan. Pilihan unknown tidak diberi default buatan.
- **Kartu SD:** status/ruang tersisa dapat dibaca. **Format SD card belum diaktifkan** karena bukti status akhir format belum cukup; format bukan reset kamera. Detail gerbang: [kontrak](analysis/recordings-settings-wire.md).

Fitur belum diuji pada kamera pengguna. Tidak ada fallback cloud jika firmware memerlukan MQ/signaling server.

## Ubah hotspot kamera

Pada debug build, buka **Pengaturan → Hotspot kamera** setelah login kamera berhasil. Sambungkan Android langsung ke AP kamera, izinkan akses Wi-Fi/lokasi jika diminta, lalu **Periksa hotspot kamera**. Form hanya aktif jika identitas kamera, SSID jaringan, readback AP, dan capability SoftAP terverifikasi.

Masukkan nama baru (huruf/angka, maksimal 30 karakter) dan password AP 8–30 karakter (huruf, angka, atau `@#_^*%.+`); nama lama boleh dipertahankan. Password tetap wajib ketika hanya mengubah nama. **Simpan hotspot** mengirim satu kali lalu mencoba menyambungkan dan memverifikasi kamera yang sama. Password login kamera tetap terpisah. API26–28 menggunakan pengaturan Wi-Fi Android; API29+ memakai dialog sistem.

ACK belum berarti selesai. Jika hasil belum diketahui/restart, **Sambungkan dan periksa hotspot** hanya membaca/verifikasi, tanpa mengirim perubahan ulang. Password AP tidak disimpan permanen. Legacy/kemampuan yang belum diketahui tetap terkunci; jalur MQ/cloud tidak digunakan. Fitur dan readback password tetap memerlukan acceptance firmware, dan UI runtime baru belum diverifikasi karena emulator crash. [Kontrak, batas, dan validasi](analysis/ap-hotspot-settings-research.md).

## Setup melalui hotspot/AP kamera

1. Aktifkan mode AP kamera mengikuti petunjuk perangkat; pilih **Setup lewat AP kamera**.
2. Masukkan/pilih SSID hotspot dan password hotspot bila diperlukan. API29+ menggunakan dialog koneksi Android; API26–28 membuka pengaturan Wi-Fi sistem. Persetujuan dialog sistem tetap diperlukan. Daftar SSID dapat memerlukan izin lokasi dan layanan lokasi; input manual tetap tersedia.
3. Cari/pilih identitas kamera, lalu verifikasi akses lokal yang sah. Untuk kamera yang memang belum memakai password lokal, pilih opsi tersebut secara eksplisit. Kamera terikat pemilik tidak diambil alih atau di-reset.
4. Setelah autentikasi berhasil, masukkan SSID/password Wi-Fi rumah, lalu kirim sekali. Batas legacy password32 byte GBK; input yang melebihi format ditolak.
5. Kembali ke Wi-Fi rumah dan pilih **Periksa kamera di Wi-Fi rumah**. Setup baru selesai setelah ID yang sama ditemukan dan autentikasi ulang berhasil; ACK bukan bukti kamera berhasil masuk Wi-Fi.

Jika AP terputus/ACK hilang/proses mati, lanjutkan verifikasi dahulu. Password Wi-Fi tidak dipulihkan atau dikirim ulang otomatis. Menu **Setup ulang lewat hotspot** menghapus progres lokal saja. Penggunaan AP langsung tersedia setelah akses lokal terverifikasi.

**Pairing baru platform IoT:** jalur PIN dalam APK memakai token binding dari cloud. Tombol pairing menjelaskan batas ini (`CloudRequired`); alur offline penuh untuk kamera demikian belum dibuktikan. ONVIF bukan prasyarat dan tidak dipakai sebagai pengganti.

## Diagnostik dan pengujian

Buka **Diagnostik LAN** untuk Wi-Fi terpilih (IPv4/gateway), tahap, status, ukuran dan waktu pemeriksaan. Ekspor JSON tidak memuat password, payload login, SID/handle, kunci atau footage. Gunakan hasil ini untuk mencatat kegagalan, bukan sebagai bukti kompatibilitas sebelum uji kamera nyata.

Pengujian otomatis memakai parser/cipher fixture sintetis, server loopback dan frame abu-abu; tidak menggunakan data privat aplikasi vendor. Radio Wi-Fi/AP, stream30menit, motor/stop PTZ, offline LAN dan firmware target memerlukan pengujian Android/kamera fisik. Detail batas ada di [IMPLEMENTATION.md](IMPLEMENTATION.md). Setelah pengujian koneksi diaktifkan kembali secara terpisah, `ANDROID_SERIAL=<serial> tools/run-android-validation.sh` menyimpan report per runtime; perintah tersebut tidak dijalankan pada kelanjutan tanpa pengujian koneksi ini.

## Analisis V380 6.4.90

[Laporan utama](analysis/REPORT.md), [Legacy wire](analysis/legacy-wire-spec.md), [IoT LAN](analysis/iot-lan-protocol.md), [AP provisioning](analysis/ap-provisioning.md), [rekaman/pengaturan](analysis/recordings-settings-wire.md), [discovery/PTZ](analysis/discovery-ptz.md), [P2P](analysis/p2p.md), [native decoder](analysis/native.md), [perbandingan repository](analysis/comparison.md), [indeks bukti/hash](analysis/evidence-index.md), [gerbang dukungan](analysis/protocol-gate.md).

Analisis statis menggunakan REA/JADX/DEX/ELF. APK vendor tidak dijalankan atau dimodifikasi, library proprietary tidak menjadi dependensi, dan token akun/kredensial tersimpan tidak diekstrak. Kedua repository pembanding menjadi referensi perilaku, bukan sumber kode runtime yang disalin.
