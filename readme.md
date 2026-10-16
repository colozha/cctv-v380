# V380 LAN

Client Android independen untuk kamera V380 pada Wi-Fi lokal. Versi **0.4.3-experimental** mengimplementasikan autentikasi Legacy/IoT LAN, live video AVC/HEVC dengan MediaCodec, PTZ, konfigurasi Wi-Fi melalui AP terautentikasi, serta rekaman/pengaturan IoT eksperimental. **Pengguna sudah melaporkan login dan video berhasil, tetapi sesi versi0.2.0 terputus setelah beberapa detik; kompatibilitas stabil belum terverifikasi.** Pairing awal IoT yang membutuhkan binding cloud belum didukung. Rincian: [status implementasi dan kompatibilitas](IMPLEMENTATION.md).

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

APK lokal: [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk). Adapter eksperimental aktif hanya pada debug. Setelah pengguna melaporkan decitan keras pada jalur AAC, payload mikrofon dikoreksi mengikuti jalur vendor PCM21/IMA ADPCM; tidak ada pemutaran speaker otomatis.123 tes JVM lulus, build debug/instrumentation berhasil, lint0 error/35 warning, serta decoder host independen lulus untuk nada sintetis. APK koreksi sudah dipasang setelah perangkat tersambung kembali;5 tes protokol sintetis Android API33 lulus. Speaker/mikrofon revisi ini belum diuji dan tidak ada pengiriman suara ke kamera otomatis. [Hasil terbaru dan batas](validation/talkback-noise/results.json). Hasil [AAC sebelumnya](validation/talkback-aac/results.json), [envelope](validation/talkback-envelope/results.json), [metadata](validation/talkback-metadata/results.json) dan [talkback awal](validation/talkback/results.json) merupakan record historis sesuai source masing-masing. Tes AAC lokal sebelumnya tidak membuktikan kecocokan speaker.

## Kamera sudah terhubung Wi-Fi

1. Sambungkan HP ke Wi-Fi yang sama dengan kamera, lalu pilih **Cari di Wi-Fi**. Izinkan akses jaringan lokal bila diminta Android.
2. Pilih kamera hasil discovery, beri nama, lalu simpan. Input manual tersedia untuk IP/port/device ID; pilihan protokol manual hanya untuk keluarga yang sudah diketahui.
3. Buka kamera, masukkan password **lokal kamera**, lalu **Sambungkan**. Username hanya digunakan oleh profil Legacy. Password akun V380, password hotspot, dan password Wi-Fi rumah adalah input berbeda; aplikasi tidak mengasumsikan `admin` atau password kosong.
4. SD/HD dan PTZ aktif setelah stream/capability valid. Tahan arah untuk bergerak, lepaskan untuk stop. Saat aplikasi masuk background, stream ditutup.

Opsi **ingat password** default nonaktif. Bila diaktifkan, akses disimpan terenkripsi dengan Keystore dan dikecualikan dari backup. Menghapus profil menghapus ciphertext-nya. Akses vendor tidak dipindahkan otomatis. Bila tidak mengetahui password lokal dan kamera tidak menawarkan pairing offline, jangan mengisi password akun sebagai penggantinya.

## Bicara melalui mikrofon HP

Pada APK debug, buka tab **Live**, tunggu video berjalan, lalu gunakan **Bicara ke kamera → Mulai bicara**. Jika Android meminta izin mikrofon, berikan izin lalu ketuk **Mulai bicara** kembali. **Berhenti bicara** mematikan mikrofon; meninggalkan aplikasi/Live, rotasi, perubahan kualitas, kehilangan koneksi dan reconnect juga menghentikannya. Mikrofon tidak menyala kembali otomatis. Suara hanya dikirim, tidak disimpan.

Fitur ini memerlukan capability speaker dan metadata sesi IoT lokal yang sesuai subset: PCM100 mono16-bit pada8/16kHz atau metadata102 mono16kHz dengan jalur mikrofon vendor PCM21. Jalur AAC mikrofon sebelumnya sudah dihapus setelah laporan decitan; status Mikrofon aktif tetap hanya menyatakan capture/pengiriman lokal. Hentikan bila terdengar suara keras/tidak wajar. Uji awal dengan HP jauh dari kamera untuk mengurangi kemungkinan feedback akustik. [Kontrak yang dikoreksi](analysis/talkback-wire.md), [hasil dan batas](validation/talkback-noise/results.json). Suara speaker revisi ini belum terverifikasi.

Panel membedakan format yang masih ditunggu, belum dikirim kamera, invalid, atau tidak cocok dengan sesi/proteksi. Pembaruan informasi video saja tidak lagi menghapus format audio valid dalam sesi yang sama. Jika kamera belum mengirim format, tombol tetap nonaktif; menyambungkan ulang belum terbukti menyelesaikannya. [Perbaikan dan hasil validasi](validation/talkback-metadata/results.json).

## Rekaman dan pengaturan

Buka detail kamera → **Rekaman** atau **Pengaturan**. Aplikasi otomatis menyambungkan akses lokal jika password kamera tersimpan; jika belum tersimpan, masukkan password lokal kamera. Saat tidak tersambung, tombol **Sambungkan kamera** tetap tersedia. Fitur baru tersedia eksperimental untuk sesi IoT; dukungan Legacy belum diimplementasikan.

- **Rekaman:** kartu SD menampilkan persentase aktual. Pilih tanggal dengan panah atau date picker dan filter Semua/Peristiwa; daftar dimuat otomatis. Ikon refresh memuat ulang, menu urutan memilih Terbaru/Terlama. Kartu rekaman menampilkan waktu, tanggal dan durasi tanpa thumbnail. Putar membuka mode fokus player; tombol/gesture kembali memulihkan daftar dan posisi scroll.
- **Simpan rekaman:** gunakan menu kartu atau tombol **Simpan rekaman** di player dan pilih lokasi file Android. Aplikasi menyalin satu segmen penuh ke MP4 **tanpa audio**, tanpa checkbox tambahan. Perpindahan ke pemilih file mempertahankan akses kamera; bila sesi sudah hilang, lokasi simpan menunggu autentikasi ulang. Status tersimpan baru tampil setelah MP4 tervalidasi, file selesai ditulis dan dibaca ulang identik; bila pembatalan gagal menghapus file parsial, hapus file tujuan sebelum mencoba lagi. Audio, batch dan resume unduhan belum tersedia.
- **Kapasitas dan hapus:** tab Rekaman memperingatkan saat kartu SD kamera terpakai ≥90%. Aksi hapus berada di menu kartu dan tetap nonaktif dengan penjelasan karena kontrak penghapusan rekaman SD belum terbukti; kontrol hapus file generik yang ditemukan dipakai untuk suara pengingat, bukan bukti hapus rekaman SD.
- **Pengaturan:** muat nilai kamera, ubah orientasi/lampu/perekaman yang tersedia, lalu simpan perubahan. Aplikasi membaca ulang sebelum menyatakan tersimpan. Pilihan unknown tidak diberi default buatan.
- **Kartu SD:** status/ruang tersisa dapat dibaca. **Format SD card belum diaktifkan** karena bukti status akhir format belum cukup. Catatan format tertunda dapat diperiksa setelah login, atau ditutup dengan konfirmasi walaupun settings belum dimuat. Penutupan tidak membuktikan sukses atau membatalkan kamera. [Investigasi hapus/format dan batas aktivasi](analysis/sd-maintenance-wire.md).

Fitur belum diuji pada kamera pengguna. Tidak ada fallback cloud jika firmware memerlukan MQ/signaling server.

Perbaikan tab Rekaman dan batas validasi terbaru: [hasil pengujian](validation/recordings-fixes/results.json), [catatan protokol](analysis/recordings-settings-wire.md#perbaikan-tab-rekaman--14-oktober-2026).

## Manajemen kamera tambahan (debug0.4.2)

Di **Pengaturan**, buka grup Alarm dan deteksi gerakan, Jadwal lampu, Preset dan patroli/Perangkat, lalu muat nilai kamera. Alarm utama, detector gerakan dan suara kamera terpisah dari Rekam peristiwa. Interval alarm dapat ditambah/dihapus/diedit per hari dalam draft, lalu disimpan atau dibatalkan. Penambahan khusus gerakan mengikuti batas editor konservatif dan membutuhkan deteksi gerakan aktif. Minggu kosong memerlukan Alarm utama mati. Sensitivitas gerakan belum tersedia. Editor area tersedia untuk grid satu tampilan datar yang terkonfirmasi: **Ambil gambar referensi → pilih sel → Gunakan area → Simpan alarm**. Gambar hanya berada di memori; jika arah kamera berubah, ambil ulang. Fisheye, gambar gabungan, crop/rotasi dan metadata ambigu tetap terkunci. Sensitivitas lampu rendah/normal/tinggi dan jadwal kamera memerlukan readback/capability; jadwal mengikuti waktu kamera, bukan waktu Android. Tambah/hapus interval lampu memperbarui jumlah interval; kapasitas baru dibatasi baseline UI vendor.

Di **Live → Preset dan patroli**, muat status, pilih slot, lalu simpan/panggil/hapus preset. Pada kamera yang mendeklarasikan cruise, arah PTZ terkunci sampai konfigurasi patroli tidak aktif terbaca. Rute, urutan dan jeda patroli dapat diedit ketika tidak aktif; **start/stop patroli belum diaktifkan** karena status motor belum terbukti. Kalibrasi berada pada Pengaturan dan meminta konfirmasi. ACK kalibrasi belum berarti gerak selesai; periksa kamera sebelum menutup pemeriksaan.

**Perangkat** menampilkan versi yang dibaca dari kamera dan menyediakan restart satu kali. Jika koneksi kembali, aplikasi mencocokkan device ID dan autentikasi; tanpa indikator boot, restart tetap belum dapat dipastikan. Setelah timeout/proses mati, sambungkan lewat akses lokal biasa lalu **Periksa kamera**; tidak ada pengiriman ulang. **Tutup catatan restart** hanya menutup catatan lokal. **Format SD tetap terkunci** sampai enum terminalnya terbukti.

Fitur baru belum diuji pada kamera. [Kontrak dan batas implementasi](analysis/camera-management-wire.md), [hasil validasi](validation/area-stage3/results.json). Seluruh rencana belum selesai; kontrol yang masih terkunci adalah pekerjaan tersisa.

## Ubah hotspot kamera

Pada debug build, buka **Pengaturan → Hotspot kamera** setelah login kamera berhasil. Sambungkan Android langsung ke AP kamera, izinkan akses Wi-Fi/lokasi jika diminta, lalu **Periksa hotspot kamera**. Form hanya aktif jika identitas kamera, SSID jaringan, readback AP, dan capability SoftAP terverifikasi.

Masukkan nama baru (huruf/angka, maksimal 30 karakter) dan password AP 8–30 karakter (huruf, angka, atau `@#_^*%.+`); nama lama boleh dipertahankan. Password tetap wajib ketika hanya mengubah nama. **Simpan hotspot** mengirim satu kali lalu mencoba menyambungkan dan memverifikasi kamera yang sama. Password login kamera tetap terpisah. API26–28 menggunakan pengaturan Wi-Fi Android; API29+ memakai dialog sistem.

ACK belum berarti selesai. Jika hasil belum diketahui/restart, **Sambungkan dan periksa hotspot** hanya membaca/verifikasi, tanpa mengirim perubahan ulang. Password AP tidak disimpan permanen. Legacy/kemampuan yang belum diketahui tetap terkunci; jalur MQ/cloud tidak digunakan. Fitur dan readback password tetap memerlukan acceptance firmware, dan UI runtime baru belum diverifikasi karena emulator crash. [Kontrak, batas, dan validasi](analysis/ap-hotspot-settings-research.md).

## Setup melalui hotspot/AP kamera

1. Aktifkan mode AP kamera mengikuti petunjuk perangkat; pilih **Setup lewat AP kamera**.
2. Masukkan/pilih SSID hotspot dan password hotspot bila diperlukan. API29+ menggunakan dialog koneksi Android; API26–28 membuka pengaturan Wi-Fi sistem. Persetujuan dialog sistem tetap diperlukan. Daftar SSID dapat memerlukan izin lokasi dan layanan lokasi; input manual tetap tersedia.
3. Cari/pilih identitas kamera, lalu verifikasi akses lokal yang sah. Untuk kamera yang memang belum memakai password lokal, pilih opsi tersebut secara eksplisit. Kamera terikat pemilik tidak diambil alih atau di-reset.
4. Setelah autentikasi berhasil, masukkan SSID/password Wi-Fi router, lalu kirim sekali. Router boleh tanpa koneksi internet; gunakan jaringan yang mengizinkan komunikasi antarklien. Batas legacy password32 byte GBK; input yang melebihi format ditolak.
5. Buka Pengaturan Android, pilih Wi-Fi router tujuan, lalu kembali dan pilih **Periksa kamera di router**. Jika Android memperingatkan tidak ada internet, tetap gunakan Wi-Fi tersebut. SSID yang terbaca harus cocok; bila tidak dapat dibaca, konfirmasikan bahwa router tujuan sudah dipilih. Setup baru selesai setelah ID yang sama ditemukan dan autentikasi ulang berhasil; ACK bukan bukti kamera berhasil masuk Wi-Fi.

Jika AP terputus/ACK hilang/proses mati, lanjutkan verifikasi dahulu. Password Wi-Fi tidak dipulihkan atau dikirim ulang otomatis. Menu **Hapus progres setup lokal** menghapus progres aplikasi saja dan tidak membatalkan konfigurasi kamera. Penggunaan AP langsung tersedia setelah akses lokal terverifikasi.

**Pairing baru platform IoT:** jalur PIN dalam APK memakai token binding dari cloud. Tombol pairing menjelaskan batas ini (`CloudRequired`); alur offline penuh untuk kamera demikian belum dibuktikan. ONVIF bukan prasyarat dan tidak dipakai sebagai pengganti. Riset REA/DEX tambahan belum membuktikan bahwa token kosong dapat menggantikan binding cloud. [Kontrak dan hasil investigasi offline](analysis/ap-provisioning.md#offline-router-setup-investigation-and-implementation-15-october-2026).

## Diagnostik dan pengujian

Buka **Diagnostik LAN** untuk Wi-Fi terpilih (IPv4/gateway), tahap, status, ukuran dan waktu pemeriksaan. Ekspor JSON tidak memuat password, payload login, SID/handle, kunci atau footage. Gunakan hasil ini untuk mencatat kegagalan, bukan sebagai bukti kompatibilitas sebelum uji kamera nyata.

Pengujian otomatis memakai parser/cipher fixture sintetis, server loopback dan frame abu-abu; tidak menggunakan data privat aplikasi vendor. Radio Wi-Fi/AP, stream30menit, motor/stop PTZ, offline LAN dan firmware target memerlukan pengujian Android/kamera fisik. Detail batas ada di [IMPLEMENTATION.md](IMPLEMENTATION.md). Setelah pengujian koneksi diaktifkan kembali secara terpisah, `ANDROID_SERIAL=<serial> tools/run-android-validation.sh` menyimpan report per runtime; perintah tersebut tidak dijalankan pada kelanjutan tanpa pengujian koneksi ini.

## Analisis V380 6.4.90

[Laporan utama](analysis/REPORT.md), [Legacy wire](analysis/legacy-wire-spec.md), [IoT LAN](analysis/iot-lan-protocol.md), [AP provisioning](analysis/ap-provisioning.md), [rekaman/pengaturan](analysis/recordings-settings-wire.md), [discovery/PTZ](analysis/discovery-ptz.md), [P2P](analysis/p2p.md), [native decoder](analysis/native.md), [perbandingan repository](analysis/comparison.md), [indeks bukti/hash](analysis/evidence-index.md), [gerbang dukungan](analysis/protocol-gate.md).

Analisis statis menggunakan REA/JADX/DEX/ELF. APK vendor tidak dijalankan atau dimodifikasi, library proprietary tidak menjadi dependensi, dan token akun/kredensial tersimpan tidak diekstrak. Kedua repository pembanding menjadi referensi perilaku, bukan sumber kode runtime yang disalin.

## Tab Rekaman 0.4.3

Desain kartu dan mode fokus player mengikuti referensi pengguna, dengan tema terang/gelap, tanggal/filter otomatis dan tanpa thumbnail. Diagnostik schema4 mempertahankan penyebab asli replay, tahap transfer, penghitung frame/byte dan time ID; tidak mencatat payload, key, SID atau credential. Timeout, EOF, penolakan, paket dan timestamp tidak valid dibedakan dari kegagalan MP4/tujuan. Tidak ada perubahan mode/cipher/deadline spekulatif. [Validasi 0.4.3](validation/recordings-redesign/results.json) membedakan fixture wire, native Android sintetis, screenshot dan acceptance kamera nyata.
