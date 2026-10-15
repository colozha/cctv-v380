# Status implementasi V380 LAN

Versi **0.4.3-experimental** memiliki adapter wire nyata untuk Legacy V2 dan common IoT LAN: autentikasi, negosiasi video, decoding MediaCodec, PTZ, serta konfigurasi Wi-Fi melalui AP yang telah terautentikasi. Adapter aktif pada **APK debug**. Belum ada kamera yang dinyatakan kompatibel; rilis pengguna tetap dibatasi sampai acceptance kamera fisik lulus.

Target pertama: firmware `Hw_EV2X_NA1_XHR_V1.0_WF_20250804`, software `V2.1.6.0(HSC_A2X17XHR_20250804)`, berdasarkan screenshot pengguna. Nama firmware tidak digunakan untuk memilih generasi protokol.

## Hapus rekaman dan format SD — 15 Oktober 2026

[Investigasi dan perubahan](analysis/sd-maintenance-wire.md) mencatat scan5.429.555 instruksi DEX dan simbol ELF tambahan. Kontrak delete TF per segmen serta enum terminal format131330 tetap belum terbukti; kedua fitur tetap nonaktif. Writer format yang masih gated kini membawa profil/device/operation ID, memeriksa ulang sesi dan kartu sebelum write, serta mewajibkan marker terverifikasi. Recovery membaca tiga properti SD dengan korelasi ketat; cleanup wajib ID operasi dan pemeriksaan file terhapus. Tutup catatan meminta konfirmasi dan tetap menyatakan hasil belum diketahui, termasuk ketika settings belum dimuat.

[Validasi source terbaru](validation/sd-maintenance/results.json):73 tes JVM terpilih lulus, build debug/instrumentation berhasil, lint0 error. UI/storage baru belum dijalankan: emulator API37 SIGSEGV saat boot dengan KVM maupun tanpa akselerasi. Tidak ada screenshot baru terverifikasi, tes koneksi atau perintah kamera destruktif. Hasil di bagian lama berikut adalah historis.

## Manajemen kamera0.4.2 — implementasi sebagian

[Kontrak REA/DEX, perilaku, dan batas](analysis/camera-management-wire.md). Tidak ada dependensi baru atau pengujian koneksi kamera. Debug IoT menambah grup pengaturan alarm utama/gerakan/suara, tambah/hapus/edit jadwal alarm per hari dengan batas konservatif, sensitivitas/jadwal lampu, informasi versi, preset simpan/panggil/hapus, konfigurasi rute patroli tidak aktif, command kalibrasi, serta restart send-once dengan pending AtomicFile dan verifikasi identitas setelah reconnect. Mutasi tidak mengganti write yang sedang berjalan; snapshot memeriksa revisi auth/device ID dan SET memerlukan readback.

**Belum selesai:** start/stop serta status motor patroli, status terminal kalibrasi, sensitivitas gerakan, varian editor area non-flat/ambigu, format SD terminal, dukungan Legacy/multi-view variants dan acceptance firmware. Writer format tetap dikunci; belum ada enum terminal yang dapat dipercaya. Kalibrasi hanya melaporkan ACK/hasil ambigu dan meminta pemeriksaan gerak oleh pengguna. Restart yang tersambung kembali tidak dinyatakan terverifikasi tanpa bukti reboot.

[Validasi terbaru](validation/area-stage3/results.json) mencatat build/lint/tes offline pada source akhir. UI/storage instrumentation baru dikompilasi; runtime tetap belum berjalan; boot tidak diulang pada lingkungan yang sama dengan API35/API26 KVM yang sebelumnya SIGSEGV. Tidak ada screenshot baru, pengujian socket, atau kamera yang diklaim lulus. Penanda format versi1 dibaca sebagai unknown tanpa replay; versi2 memakai operation ID untuk cleanup. Pending restart terpisah dari AP/provisioning dan tidak menyimpan credential.

Editor area tahap3 tersedia hanya untuk grid flat/single-view yang terkonfirmasi, dengan reference session, PixelCopy setelah frame render, dan preflight/readback. [Kontrak, batas crop/orientasi/motor, dan hasil](analysis/area-stage3-wire.md).

## Rekaman dan pengaturan0.3.0

Detail kamera sekarang memiliki tab Live, Rekaman dan Pengaturan, tanpa dependensi baru. Fitur IoT memakai transport/auth/crypto yang sama; Legacy mengembalikan unsupported untuk fitur baru. [Spesifikasi dan bukti REA/DEX/ELF](analysis/recordings-settings-wire.md).

| Fitur | Hasil implementasi |
|---|---|
| Rekaman SD | Daftar per hari/semua atau peristiwa, slice berkorelasi, playback AVC/HEVC berpacing timestamp, jeda dan seek melalui sesi baru. |
| Salin ke Android | Satu segmen video-only, persetujuan audio tidak disertakan, SAF sebelum transfer, MediaMuxer tanpa antrean drop live, pemeriksaan MediaExtractor sebelum publikasi dan cleanup parsial. |
| Pengaturan | Orientasi lewat action kamera; lampu otomatis/hidup/mati, indikator, mode/kualitas/audio/loop rekaman sesuai nilai dan ability yang dibaca. Write satu kali, ACK per properti dan readback. |
| SD | Status dan persentase ruang tersisa. Format **belum diaktifkan**: kontrak enum terminal belum terbukti. Konfirmasi/pending send-once/status recovery tersedia dalam kode, entry point dikunci. Tidak ada klaim format selesai dari ACK. |
| Lifecycle/diagnostik | Satu media aktif, cancel/join, cleanup background, draft/leave confirmation; diagnostik schema3 menambah tahap rekaman/playback/download/settings/format. |

**Validasi0.3.0:** APK debug dan APK instrumentation terbangun, lint0 error, 40 tes JVM offline lulus. Tes Android baru untuk AtomicFile pending, MP4 sintetis dan UI native dikompilasi, tetapi belum dijalankan: emulator37.2.12 mengalami SIGSEGV saat boot pada API37 maupun API30 dengan beberapa renderer dan tanpa akselerasi. Tidak ada screenshot baru atau hasil native ekspor yang diklaim lulus. [Hasil/command/log](validation/recordings-settings/results.json). Tes koneksi, loopback session dan kamera tidak dijalankan sesuai instruksi pengguna. Paragraf validasi0.2.1 dan runtime historis di bawah bukan pengujian fitur baru.

MQ/pver≥5, interoperabilitas firmware, video/ekspor nyata, ketahanan setting setelah reconnect, serta format fisik masih belum verified. Sensitivitas dan jadwal lampu tidak diberi kontrol tanpa label/kontrak yang terbukti. Rencana diimplementasikan dengan gerbang per fitur tersebut; format dan acceptance fisik belum selesai.

## Perbaikan disconnect0.2.1

Log yang dikirim pengguna membuktikan interoperabilitas parsial: login/capability dan frame render berhasil, tetapi sesi berakhir setelah beberapa detik. Penyebab tepat tidak terekam dalam schema1. Source/native membuktikan parser terlalu ketat pada chunk/gap sparse cipher untuk frame kecil dan adanya feedback NOTIFY197120 yang belum dibalas. Keduanya diperbaiki; error PTZ/reader kini memakai recovery per generation, watchdog stop tidak melempar exception tak tertangani, dan diagnostik schema2 menambahkan appVersion/reason/detail/command. [Analisis dan regresi](analysis/stream-disconnect.md).

Tidak ada pengujian koneksi lanjutan. Bukti pengguna versi sebelumnya tidak dianggap acceptance penuh atau konfirmasi bahwa stream0.2.1 telah stabil.

## Implementasi

| Lapisan | Perilaku |
|---|---|
|Discovery|UDP10008/10009, 3detik/500ms; parser text100/200/300 dan respons biner1280 berdasarkan field APK; identitas teks dan metadata platform.|
|Jaringan|Socket diikat per `Network`; koneksi tanpa internet diperbolehkan. AP memakai dialog Android/WifiNetworkSpecifier pada API29+, pengaturan sistem pada26–28. Permintaan jaringan dilepas saat selesai/batal.|
|Transport|TCP incremental/exact read, deadline total transaksi, ukuran terbatas, serialisasi write, cancellation menutup socket.|
|Legacy|Auth1167/1168, transformasi password dan nested AES PKCS5; stream301/401/303, fragment12B/inner16B, sparse cipher, AVC/HEVC; PTZ170. Tidak mencoba format login alternatif otomatis.|
|IoT LAN|X25519 melalui BouncyCastle lightweight, common header16B/JSON terenkripsi, verifikasi digest/response ID/SID, live197122/197123, info197124, sparse CBC/ECB media32B, ability dan PTZ finite step.|
|Sesi/video|Ready hanya setelah autentikasi; Streaming setelah callback render nyata. SD/HD mengganti generation/parser/decoder; frame≤8MiB, antrean3, overflow menunggu keyframe; satu stream foreground; reconnect1/2/4detik, tidak mengulang auth yang ditolak atau gerakan PTZ.|
|PTZ|Empat arah sesuai capability/sumbu; stop saat release/cancel/background/disconnect/ganti kualitas, lease1detik diperbarui200ms. Keberhasilan write bukan bukti motor berhenti.|
|AP setup|Discovery → pilih identitas → autentikasi → kirim Wi-Fi sekali → kembali ke LAN → cari ID yang sama → autentikasi ulang → simpan profil. ACK hilang/AP putus masuk verifikasi. Restart proses memulihkan metadata verifikasi, tanpa mengirim konfigurasi ulang.|
|Penyimpanan|Migrasi atomik v1→v2 mempertahankan UUID/AAD. Password opsional AES-GCM Keystore/IV baru/noBackup; password Wi-Fi tidak disimpan. Identitas/progres nonrahasia dapat dipulihkan.|
|UI/diagnostik|Compose Material3 Indonesia, tema sistem, daftar/manual/AP wizard/player/diagnostik. Ekspor allowlist tahap, kode, ukuran, waktu; tanpa payload auth, key/session/handle atau footage.|

Semua kode protokol adalah implementasi independen. Library proprietary vendor tidak dipaketkan. APK sumber tidak dimodifikasi; token/kredensial tersimpan aplikasi vendor tidak diambil.

## Batas AP pairing dan kompatibilitas

**Setup AP yang memerlukan binding baru platform IoT belum tersedia secara offline.** Jalur PIN yang ditemukan pada APK mengambil `bind_token` dari layanan akun terotorisasi, mengirim informasi binding, lalu memeriksa status melalui server. Tidak ditemukan pengganti lokal yang sah untuk rangkaian tersebut. Tombol pairing baru menjelaskan `CloudRequired`; aplikasi tidak membuat token, mereset kepemilikan, atau memakai default password. Ini bukti jalur vendor tertentu, bukan bukti bahwa setiap kamera platform2 selalu membutuhkan cloud.

Konfigurasi Wi-Fi yang sudah diimplementasikan memerlukan akses lokal valid. Akses yang tersimpan pada V380 vendor tidak otomatis tersedia pada client ini. Bila kamera pengguna tidak menyediakan password LAN/pairing offline, alur target tetap belum selesai. Adapter direct IoT untuk firmware yang memilih MQ pada pver≥5 juga perlu dibuktikan; tidak ada fallback cloud/ONVIF.

| Profil | Status |
|---|---|
|`legacy-v2`|Wire aktif pada debug, dibuktikan statis APK dan fixture sintetis; belum diuji pada kamera.|
|`iot-lan`|Wire aktif pada debug, X25519/common JSON/V3 media/control; belum diuji pada kamera.|
|`auto`|Menentukan keluarga dari metadata discovery; menolak metadata tidak dikenal, tanpa probing password alternatif.|
|Firmware pengguna|Belum teridentifikasi melalui handshake. Login/video versi sebelumnya berhasil menurut laporan dan diagnostik pengguna; stream masih terputus. Motor PTZ/setup/stabilitas belum verified.|
|Fresh IoT AP PIN binding|Jalur APK yang dipulihkan bergantung cloud; belum didukung client LAN.|

## Bukti dan pengujian

**Validasi akhir14 Oktober2026:** sesuai instruksi terbaru, pengujian koneksi lanjutan tidak dijalankan. Build APK debug, kompilasi APK pengujian, lint (0 error,10 warning,1 hint), dan34 tes JVM murni parser/cipher/serializer lulus. Hasil26 tes Android pada masing-masing API26,30,37 berasal dari source sebelumnya dan ditandai `historical_pass_only`; tidak dianggap pengujian APK akhir. API35 belum menjalankan suite runtime. Percobaan integrasi IoT sintetis terakhir gagal pada teardown ActivityScenario (`PAUSED`, bukan `DESTROYED`); source fixture diperbaiki tetapi tidak diulang. Belum ada klaim uji sintetis IoT end-to-end lulus atau kompatibilitas stabil kamera; laporan pengguna kini menunjukkan login dan render parsial pada versi sebelumnya.

Perbaikan terakhir mencakup deadline total handshake IoT dan cleanup PTZ `NonCancellable` agar cancel/join tidak melewati stop. Review source dilakukan; perilaku stop saat sentuhan dibatalkan masih memerlukan pengujian runtime/fisik. [Command offline](validation/offline-build-command.txt) dan [log build](validation/offline-build.log) mencatat pemeriksaan yang benar-benar dijalankan.

Perbaikan disconnect0.2.1 dibangun dan diuji offline; tidak ada pengulangan pengujian kamera/loopback.

Spesifikasi: [Legacy](analysis/legacy-wire-spec.md), [IoT LAN](analysis/iot-lan-protocol.md), [AP provisioning](analysis/ap-provisioning.md). Lihat [indeks bukti](analysis/evidence-index.md), [hasil mesin](validation/results.json), [review UI](validation/ui-review.md), dan [panduan penggunaan](readme.md).

Pengujian memakai fixture sintetis, loopback, AVC abu-abu dari encoder Android, dan Surface nyata. Tes mencakup parser/framing/cipher/key exchange, auth ditolak, cancellation, deadline slow-trickle, penyimpanan/migrasi, process restart provisioning, UI/rotasi/erasure password, serta sesi auth→render→PTZ stop dengan peer sintetis. Hasil per runtime dicatat dalam `validation/runtime-api*/`; sumber dan APK final dapat diperiksa melalui hash pada `validation/results.json`.

**Acceptance fisik belum lulus:** AP→Wi-Fi→login→video tiga kali, frame pertama≤5detik, stream30menit/memori/ANR, kualitas/HEVC, motor PTZ dan stop, kehilangan Wi-Fi/kamera restart, izin dicabut, serta LAN dengan internet diblokir. Emulator tidak menguji radio Wi-Fi/AP fisik atau kompatibilitas firmware. Komputer belum menemukan kamera melalui probe LAN; Android fisik belum tersambung ADB.

## Langkah validasi berikutnya

Pasang APK debug pada Android fisik di LAN kamera. Mulai dengan pencarian dan diagnostik; pilih kamera yang identitasnya sesuai. Masukkan akses lokal langsung di perangkat. Bila autentikasi valid, uji video dan stop PTZ sebelum menguji perubahan Wi-Fi. Simpan hanya diagnostik tersanitasi dan hasil acceptance, tanpa password atau video. Status verified hanya diberikan per firmware setelah seluruh alur yang diminta benar-benar bekerja.

## Pengaturan AP hotspot debug

`ApHotspotProtocol`, guard autentikasi session, form Hotspot kamera, pending marker tanpa credential, serta reconnect/discovery/auth/readback tersedia. Write memakai mode1 dan satu pengiriman; provisioning router tetap mode2. Writer memerlukan capability SoftAP eksplisit, readback AP dan jaringan/identitas yang cocok. Legacy dan unknown tetap terkunci, tanpa cloud/MQ fallback. Status ACK dipisahkan dari verifikasi, dan status pending yang dipulihkan tidak pernah dipakai sebagai antrean write. Update host/port menjaga credential dan metadata profil yang sudah ada.

[Spesifikasi serta batas routing/readback](analysis/ap-hotspot-settings-research.md#hasil-implementasi-debug), [validasi offline](validation/ap-hotspot/results.json). Tidak ada kamera yang dihubungi. Emulator API30 crash SIGSEGV saat boot; native UI/storage tests terkompilasi tetapi belum berjalan, dan kompatibilitas/persistensi firmware belum diklaim.
