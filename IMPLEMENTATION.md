# Status implementasi V380 LAN

## Format SD aktif dengan kontrak ACK vendor — 17 Oktober 2026

Fitur format SD kini dapat dipakai pada build debug; koneksi build release tetap ditolak oleh gate aplikasi, jadi ini bukan klaim dukungan release. REA V380 6.4.90 membuktikan action `DAID 131584` pada `197384/197385` dengan params `{"daid":131584}`; respons berkorelasi `result.code=1000` adalah jalur sukses vendor (SDK `256` → return `1001`). Enum terminal properti `131330` tetap belum terbukti, sehingga aplikasi tidak menafsirkan nilai mentahnya dan tidak mengklaim format fisik selesai.

Setelah ACK, aplikasi menampilkan **diterima**, memantau `SD_STATUS` tiap2detik hingga120detik, dan menampilkan **selesai** hanya bila transisi4 (sedang format)→1 (siap) teramati. Timeout, penolakan jaringan, atau proses mati menghasilkan status belum diketahui dengan marker dipertahankan; tidak ada pengiriman ulang. Penolakan berkorelasi menghapus marker. `131330` tidak lagi menjadi prasyarat, sehingga kamera yang tidak mengembalikan properti itu tetap dapat diformat; `SD_STATUS∈{1,2,3,8}` tetap diwajibkan. Konfirmasi destruktif, marker sebelum write, identitas sesi/perangkat, dan penguncian maintenance dipertahankan.

[Validasi sintetis/offline](validation/sd-format-activation/results.json): hasil build, lint, dan tes JVM beserta batasnya. Tidak ada koneksi kamera, format nyata, atau acceptance firmware. Checkpoint format16Oktober di bawah adalah catatan historis.

## Talkback codec102 berfungsi (laporan pemilik) — 16 Oktober 2026

Pemilik melaporkan suara mikrofon HP **terdengar jelas dari speaker kamera** pada uji ucapan terbatas (≤2 detik, probe debug). Ini bukti fisik pertama bahwa jalur IoT/V3 LAN pada kamera pemilik berfungsi: negosiasi start `197122`, payload PCM21/IMA256B, framing format0/type4, dan CBC. Konfigurasi yang diverifikasi memakai front-end mikrofon vendor (audio focus, `MODE_IN_COMMUNICATION`, AEC platform), sehingga front-end tersebut kini **default** (`TALKBACK_MIC_FRONTEND_V2`, dapat dinonaktifkan).

Gate codec102 `FORMAT_UNVERIFIED` dihapus; **Mulai bicara** normal aktif bila capability, metadata, izin mikrofon, dan foreground terpenuhi. Percobaan pendahulu (AAC lalu IMA tanpa front-end) gagal acceptance; paritas front-end dan koreksi gate probe di `runTalkback` menjadi jalur yang berhasil. Probe terbatas (`TALKBACK_PROBE_ENABLED`, default nonaktif) tetap tersedia sebagai alat diagnostik.

Validasi source ini: suite offline resmi lulus; tes talkback JVM lulus, termasuk codec102 menulis setelah ACK dan jalur probe hening/ucapan. Ini laporan pemilik terbatas, bukan pengukuran berulang; belum ada klaim latensi, ketahanan jangka panjang, atau firmware lain. [Bukti dan batas](validation/talkback-fix/results.json).

## Front-end mikrofon, probe terbatas, dan batas codec102 — 16 Oktober 2026

[Paritas front-end mikrofon](app/src/main/java/dev/cctv/v380/media/TalkbackAudioSession.kt) menambahkan audio focus, `setMode(MODE_IN_COMMUNICATION)`, dan `AcousticEchoCanceler` platform, dengan pemulihan mode saat berhenti; sumber `VOICE_COMMUNICATION` bila AEC tersedia, selain itu `MIC`. Diaktifkan hanya oleh flag build `-PtalkbackMicFrontendV2=true` (default nonaktif) melalui seam `talkbackAudioFactory`; tanpa flag, capture identik dengan perilaku sebelumnya (`NO_OP`). Bukti vendor: [startSpeak](analysis/evidence/talkback-HSMediaPlayer-startSpeak.sanitized.json) dan [MicrophoneRecorder.init](analysis/evidence/talkback-MicrophoneRecorder-init.sanitized.json). EchoCanceller proprietary vendor tidak direplikasi.

[Verifikasi statis](analysis/evidence/talkback-codec-select.sanitized.json) mengonfirmasi pemilih encoder uplink memakai field aattr `+0x400` (`cmp w8, #0x64`): codec100 menyalin PCM, non100 menuju encoder2001. **Gate codec102 `FORMAT_UNVERIFIED` tetap tidak diubah.**

Probe acceptance terbatas (debug) tersedia untuk menguji codec102 secara aman: paling banyak15 frame hening (sekitar0,5 detik), deadline pembatalan independen, berhenti sendiri, tanpa mikrofon dan tanpa capture; hanya aktif dengan flag build `-PtalkbackProbeEnabled=true` dan tunduk pada kebijakan bukti transport di [tools/talkback-oracle/README.md](tools/talkback-oracle/README.md). Tombol dan catatannya berbahasa Indonesia/Inggris dan tidak menggantikan gate normal.

Validasi source ini: suite offline resmi lulus (assemble debug/instrumentation + lint + tes terpilih, locale Indonesia). Tes talkback JVM lulus; pada build default kasus bounded probe di-skip (fail-closed), dan dengan `-PtalkbackProbeEnabled=true` `TalkbackSessionTest` menjalankan17 kasus (1 skip) dengan jalur probe hening benar-benar dieksekusi. [TalkbackAudioFrontendTest](app/src/androidTest/java/dev/cctv/v380/TalkbackAudioFrontendTest.kt) lulus pada perangkat fisik M2101K6G/Android13 (mode audio dipulihkan). Tidak ada mikrofon, kamera, cloud, atau audio nyata yang diakses; acceptance speaker tetap belum dijalankan. [Bukti dan batas](validation/talkback-fix/results.json).

## Penyederhanaan tab Pengaturan — 16 Oktober 2026

Tab Pengaturan kini memiliki tiga kartu: Gambar & lampu, Rekaman & kartu SD, dan Koneksi & perangkat. Jadwal lampu, alarm/deteksi gerakan serta preset/patroli dipindah ke satu panel paling bawah dengan ikon Build dan label Belum berfungsi sesuai laporan pengguna; detailnya tetap dapat dibuka. Ini penandaan UI, bukan perubahan dukungan protokol. Subjudul dan divider menggantikan kartu bersarang; aksi hotspot hanya tampil saat grup koneksi dibuka. Draft, status/error/pemulihan ketika tertutup, penghapusan password saat ditutup, tema/bahasa serta gerbang format/patroli tetap dipertahankan. Tab Live tidak diubah.

Build debug/instrumentation dan lint berhasil (0 error);15 tes UI sintetis lulus pada emulator API35. Pemeriksaan visual mencakup69 capture matriks Indonesia/Inggris, terang/gelap, teks1,3× dan landscape, ditambah5 capture status/diagnostik. Hasil serta hash source dicatat di [validation/settings-simplified/results.json](validation/settings-simplified/results.json). Bukti sembilan kartu sebelumnya di validation/settings-redesign bersifat historis. Tidak ada acceptance kamera fisik yang diklaim.

## Checkpoint decitan talkback dan pembanding vendor — 16 Oktober 2026

Revisi IMA juga gagal menurut pengguna, termasuk dari ruangan lain; aplikasi resmi dilaporkan jelas. **Bicara codec102 kini dibatasi dengan `FORMAT_UNVERIFIED` sebelum recorder/start write**, dengan pesan Indonesia/Inggris dan enum diagnostik. Metadata tetap dipertahankan untuk diagnosis; codec negosiasi dipisahkan dari enum payload internal. Tidak ada pembukaan codec atau perubahan cipher baru berdasarkan tebakan.

[Oracle terpisah](tools/talkback-oracle/README.md) tanpa permission berhasil menjalankan fungsi encoder/framing vendor pada Android API35 melalui ARM64 native bridge. Tujuh blok berurutan dan14 paket utuh cocok byte demi byte; pengulangan proses baru identik. Factory native mempertahankan index global saat restart; client tetap memakai state per attempt, dengan regresi yang mencatat perbedaannya. Penelusuran dua caller Java juga sama-sama menemukan input21. Jalur transport aplikasi resmi pada kamera pengguna belum diamati, sehingga hasil ini bukan perbaikan speaker yang sudah selesai.

Validasi source ini:126 tes JVM lulus (28 talkback); build debug/instrumentation dan lint berhasil;8 tes Android sintetis lulus, termasuk UI pembatasan dua bahasa dan state/lifecycle kontrol. Screenshot sintetis font besar Indonesia gelap dan Inggris terang diperiksa. Tidak ada kamera/cloud/mikrofon nyata yang diakses; uji hening/ucapan belum diaktifkan dan codec102 tetap dibatasi. APK client terpasang di emulator saja, bukan HP pengguna yang terputus. [Kontrak](analysis/talkback-wire.md), [hasil lengkap dan hash](validation/talkback-oracle/results.json). Hasil historis di bawah bukan acceptance revisi terbaru.

## Kontrol player rekaman — 16 Oktober 2026

Player rekaman memakai satu baris ikon: sebelumnya, putar/jeda, hentikan, berikutnya, unduh. Navigasi mengikuti urutan daftar Terbaru/Terlama pada tanggal/filter aktif dan langsung meminta pemutaran segmen tujuan dari awal, termasuk setelah jeda atau segmen selesai. Tetangga yang tidak tersedia nonaktif; tidak ada wrap, pergantian tanggal atau pemutaran segmen berikutnya secara otomatis. Alur playback/stop/seek/ekspor dan gerbang autentikasi/busy/hotspot/ekspor tetap memakai implementasi sebelumnya. Tombol memakai target sentuh48dp, ikon24dp, padding panel adaptif untuk layar320dp, serta deskripsi aksesibilitas Indonesia/Inggris.

[Validasi source perubahan ini](validation/recording-controls/results.json): build debug/instrumentation dan lint berhasil;16 tes JVM terpilih lulus, termasuk navigasi berdasarkan urutan/ID, batas daftar, daftar kosong/satu item dan filter. Tes workflow diperbarui untuk pemilihan tetangga dari awal, dan fixture visual memeriksa urutan/ukuran/jarak kontrol termasuk layar320dp. Tes Android tersebut baru dikompilasi, belum dijalankan karena tidak ada perangkat Android terhubung; tidak ada screenshot baru atau acceptance kamera fisik yang diklaim. Screenshot kontrol berlabel sebelumnya adalah bukti historis.

## Checkpoint format SD — 16 Oktober 2026 (historis)

[Penelusuran handler hasil V380](analysis/sd-maintenance-wire.md#penelusuran-penyelesaian-format--16-oktober-2026) menemukan bahwa jalur UI vendor yang diperiksa menampilkan sukses dari return `1001`, yang pada platform baru berasal dari hasil action SDK `256`. Handler tidak membuktikan terminal format; assignment status normal pada salah satu layar adalah perubahan model lokal. Properti `131330` tetap belum memiliki enum terminal/korelasi yang terbukti. Rencana mensyaratkan bukti lengkap sebelum aktivasi, sehingga `FORMAT_STATUS_VERIFIED=false`, writer/UI dan marker tetap seperti sebelumnya; fitur belum diperbaiki sampai tahap dapat dipakai. Tidak ada API/state sukses atau migrasi spekulatif yang ditambahkan. [Manifest bukti](analysis/evidence/sd-format-completion-summary.json) dan [validasi offline revisi ini](validation/sd-format-completion/results.json) menggantikan hasil historis hanya untuk lingkup yang dicatat; tidak ada pengujian kamera nyata atau UI runtime baru.

## Setup router offline, 15 Oktober 2026

Alur AP→router yang sudah ada kini memvalidasi encoding sebelum pending/write, mengikat writer ke profil/device/revisi autentikasi dan jaringan AP, serta menyimpan marker atomik tepat sebelum satu pengiriman. Penolakan eksplisit dipisahkan dari hasil belum diketahui; respons IoT Wi-Fi yang tidak terenkripsi atau tidak berkorelasi tidak dapat menyatakan penolakan/sukses. Pemulihan proses, retry dan background tidak mengulang konfigurasi. Pemilihan router memakai Pengaturan Android. SSID terbaca yang berbeda ditolak; SSID tidak tersedia memerlukan konfirmasi pada Network saat itu, dilanjutkan discovery ID sama dan login ulang. Konfirmasi pengguna bukan bukti nama router. Password LAN opsional mengikuti pilihan ingat password pengguna.

Setup menampilkan empat tahap, bantuan router tanpa internet, pemisahan tiga credential, pesan pairing cloud/akses ditolak/hasil ambigu dan pemulihan yang jelas. Semua copy setup menggunakan resource Indonesia/Inggris dalam desain yang sudah disetujui. [Riset REA/DEX dan kontrak](analysis/ap-provisioning.md#offline-router-setup-investigation-and-implementation-15-october-2026) belum membuktikan bootstrap binding baru tanpa cloud; tidak ada token/credential tebakan. [Hasil validasi dan source manifest](validation/offline-provisioning/results.json) memisahkan software sintetis dari runtime Android dan acceptance kamera yang belum dijalankan.


## Koneksi otomatis Live, Rekaman dan Pengaturan — 15 Oktober 2026

Masuk ke ketiga tab otomatis memakai password lokal kamera tersimpan. Meninggalkan Live/Rekaman tetap menutup sesi; koneksi baru menunggu transisi selesai dan memeriksa pembatalan, profil serta tab aktif. Pengaturan kini mengikuti alur ini, termasuk ketika detail dibuka kembali pada tab tersebut. Setelah login dari Pengaturan, jalur `connect()` yang sudah ada memuat pengaturan tanpa pemicu pembacaan tambahan atau retry otomatis. Profil tanpa password tersimpan langsung meminta kredensial. `CameraAccess` menampilkan aksi Sambungkan saat Idle; indikator koneksi hanya mengikuti Connecting/Authenticating/Reconnecting. Copy panel memakai resource Indonesia/Inggris yang sudah tersedia.

[Validasi perbaikan dan hash source](validation/settings-connection/results.json): build debug/instrumentation dan lint lulus (0 error, 40 warning, 1 hint); 10 tes JVM terpilih lulus dengan locale `id-ID`. Run locale default sempat gagal pada tes tanggal lama yang mengharapkan “Oktober”. Enam tes UI sintetis ditambahkan untuk koneksi antar-tab, Settings yang dibuka kembali, password manual, pembatalan, pergantian profil, serta status koneksi/error. Run Android API33 hanya meluluskan tes membuka kembali Settings; tes berikutnya gagal saat perangkat kembali ke AOD/keyguard dan tidak menyediakan hierarchy Compose. Runner dihentikan saat tes ketiga; tiga tes sisanya belum berjalan. Fixture akhir menjaga layar Activity menyala dan sudah dikompilasi/lint, tetapi belum diuji ulang. Tidak ada koneksi kamera fisik, pengujian login/readback kamera, atau klaim kompatibilitas firmware dari hasil sintetis ini.

Validasi sebelum perbaikan Settings (historis): build debug/instrumentation, lint dan 106 tes JVM lulus dengan locale `id-ID`; hasil tersebut bukan validasi source perbaikan ini.

## Koreksi payload mikrofon setelah laporan decitan — 15 Oktober 2026

Percobaan AAC sebelumnya gagal pada speaker pengguna: decitan keras dan ucapan tidak terdengar. Encoder AAC mikrofon dihapus. Implementasi kini mengikuti jalur PCM21 vendor: codec100 tetap PCM; metadata102 mono16kHz memakai blok IMA ADPCM256B dari505 sampel PCM16. Negosiasi dan framing mengikuti alur native, tanpa mencoba codec alternatif otomatis. Validator menolak payload ADTS pada jalur mikrofon ini. Izin, foreground, ACK-before-capture, stop dan penghapusan buffer dipertahankan.

123 tes JVM lulus, termasuk sesi codec102 yang memeriksa bytes IMA setelah ACK; build debug/instrumentation dan lint0 error/35 warning/1 hint berhasil. Decoder host libsndfile memvalidasi nada sintetis secara independen (korelasi0.9997469). Setelah HP tersambung kembali, APK sudah dipasang dan5 tes protokol sintetis Android API33 lulus. Mikrofon/speaker revisi ini belum diuji dan tidak ada pengiriman audio kamera otomatis. [Kontrak yang dikoreksi](analysis/talkback-wire.md), [hasil terkini](validation/talkback-noise/results.json). Bagian AAC di bawah adalah riwayat implementasi yang telah diganti dan tidak membuktikan kompatibilitas speaker.

## Dukungan AAC talkback — 15 Oktober 2026

Debug kini mendukung codec102 AAC-LC/ADTS mono16kHz yang diumumkan kamera pengguna. `MediaCodec` bawaan Android mengodekan PCM16 menjadi access unit AAC; client menambahkan ADTS dan framing/enkripsi V3 yang sudah ada. PCM1008/16kHz tetap tersedia; ADPCM dan format AAC lain tetap terkunci. Izin/foreground, ACK-before-capture, stop/cancel, generasi sesi, buffer terbatas dan penghapusan suara tetap berlaku. Tidak ada dependency atau penyimpanan audio baru.

120 tes JVM lulus (23 talkback), build debug/instrumentation berhasil, lint0 error/35 warning/1 hint. Delapan tes Android API33 sintetis lulus, termasuk encode/decode AAC dan kegagalan transport. Observasi metadata kamera terarah menunjukkan `metadataBlock:null`; mikrofon nyata, ACK start/suara speaker dan kestabilan video belum diuji pada perubahan ini. APK terbaru terpasang tanpa menghapus profil. [Kontrak](analysis/talkback-wire.md), [hasil/source/APK hashes](validation/talkback-aac/results.json). Bagian metadata/envelope di bawah adalah tahap sebelumnya.

## Koreksi envelope talkback — 15 Oktober 2026

Observasi LAN kamera yang diminta pengguna menemukan notifikasi CBC dengan SID header berbeda dari SID login, serta format codec102 mono16/16000Hz. Pemeriksaan native receiver tidak membandingkan SID tersebut. Client kini mengikat metadata ke socket/generasi/object sesi dan decode CBC dengan key sesi, tanpa mensyaratkan kesamaan SID header. Jenis paket dan mode enkripsi yang ditolak memiliki alasan terpisah; log hanya menambah boolean/angka format yang diizinkan. Codec102 tetap terkunci karena pengirim hanya mendukung PCM100; mikrofon dan speaker belum diuji. [Kontrak yang diperbarui](analysis/talkback-wire.md), [bukti sebelum/sesudah dan batas](validation/talkback-envelope/results.json). Validasi offline:118 tes JVM lulus (21 talkback), build debug/instrumentation berhasil, lint0 error/35 warning/1 hint. Tes UI baru dikompilasi; peluncuran Activity observasi sebelumnya tertahan dan dihentikan, sehingga tidak ada UI runtime pass pada perubahan ini.

## Perbaikan metadata talkback — 15 Oktober 2026

Notifikasi informasi Live parsial kini mempertahankan format audio valid dalam stream yang sama; audio-only diproses tanpa mensyaratkan metadata video. Format invalid, envelope tidak cocok dan pergantian format menghentikan talkback; teardown/generasi sesi membuang metadata dengan perlindungan terhadap reader lama. UI Indonesia/Inggris dan diagnostik membedakan menunggu informasi, atribut belum dikirim, invalid dan tidak cocok dengan sesi/proteksi. Permintaan `up.audio:0` tetap dipakai karena bukti yang tersedia belum menetapkan kebutuhan `audio:1`. Kamera yang tidak mengirim format tetap terkunci. [Kontrak dan batas](analysis/talkback-wire.md), [hasil revisi ini](validation/talkback-metadata/results.json). Tidak ada acceptance mikrofon/speaker kamera pada perubahan ini. Validasi source perubahan ini:116 tes JVM lulus (20 talkback), build debug/instrumentation berhasil, lint0 error/35 warning/1 hint. UI baru dikompilasi tetapi tidak dijalankan karena perangkat Dozing.

## Talkback PCM IoT — 15 Oktober 2026

[Rencana](analysis/talkback-implementation-plan.md) dan [kontrak REA/DEX/ELF](analysis/talkback-wire.md) mendasari panel Bicara ke kamera pada Live. Debug mendukung pengiriman mikrofon HP saja untuk metadata autentikasi PCM100 mono16 pada8/16kHz; ADPCM/AAC, Legacy dan metadata unknown tetap terkunci. Izin mikrofon memerlukan ketukan Mulai baru setelah diberikan. Capture dimulai setelah respons start berkorelasi; pause/rotasi/pergantian tab/kualitas/profil/reconnect/close selalu menghentikannya tanpa auto-resume. Buffer PCM aplikasi dibatasi≤200ms; write yang dibatalkan pada socket live membatalkan kesehatan transport dan memicu pemulihan video. Native stop mikrofon hanya lokal, sehingga tidak dibuat paket stop rekaan. Tidak ada suara yang disimpan atau diekspor. [Validasi dan batas](validation/talkback/results.json) membedakan tes sintetis dari acceptance kamera yang tetap belum dijalankan.

Source akhir lulus106 tes JVM (17 talkback), build debug/instrumentation dan lint0 error/39 warning. Empat vector protokol juga lulus pada Android API33. UI native ditunda sesuai pilihan pengguna; fixture tetap dikompilasi, tanpa screenshot/UI runtime yang dinyatakan lulus. Mikrofon nyata dan speaker kamera belum diuji. Hasil bagian lain di bawah adalah record historis sesuai revisi masing-masing.

Versi **0.4.3-experimental** memiliki adapter wire nyata untuk Legacy V2 dan common IoT LAN: autentikasi, negosiasi video, decoding MediaCodec, PTZ, serta konfigurasi Wi-Fi melalui AP yang telah terautentikasi. Adapter aktif pada **APK debug**. Belum ada kamera yang dinyatakan kompatibel; rilis pengguna tetap dibatasi sampai acceptance kamera fisik lulus.

Target pertama: firmware `Hw_EV2X_NA1_XHR_V1.0_WF_20250804`, software `V2.1.6.0(HSC_A2X17XHR_20250804)`, berdasarkan screenshot pengguna. Nama firmware tidak digunakan untuk memilih generasi protokol.

## Hapus rekaman dan format SD — 15 Oktober 2026

[Investigasi dan perubahan](analysis/sd-maintenance-wire.md) mencatat scan5.429.555 instruksi DEX dan simbol ELF tambahan. Kontrak delete TF per segmen serta enum terminal format131330 tetap belum terbukti; hapus per-rekaman tetap nonaktif. Format SD aktif sejak17Oktober2026 dengan kontrak ACK vendor; writer membawa profil/device/operation ID, memeriksa ulang sesi dan kartu sebelum write, serta mewajibkan marker terverifikasi. Recovery membaca tiga properti SD dengan korelasi ketat; cleanup wajib ID operasi dan pemeriksaan file terhapus. Tutup catatan meminta konfirmasi dan tetap menyatakan hasil belum diketahui, termasuk ketika settings belum dimuat.

[Validasi source terbaru](validation/sd-maintenance/results.json):73 tes JVM terpilih lulus, build debug/instrumentation berhasil, lint0 error. UI/storage baru belum dijalankan: emulator API37 SIGSEGV saat boot dengan KVM maupun tanpa akselerasi. Tidak ada screenshot baru terverifikasi, tes koneksi atau perintah kamera destruktif. Hasil di bagian lama berikut adalah historis.

## Manajemen kamera0.4.2 — implementasi sebagian

[Kontrak REA/DEX, perilaku, dan batas](analysis/camera-management-wire.md). Tidak ada dependensi baru atau pengujian koneksi kamera. Debug IoT menambah grup pengaturan alarm utama/gerakan/suara, tambah/hapus/edit jadwal alarm per hari dengan batas konservatif, sensitivitas/jadwal lampu, informasi versi, preset simpan/panggil/hapus, konfigurasi rute patroli tidak aktif, command kalibrasi, serta restart send-once dengan pending AtomicFile dan verifikasi identitas setelah reconnect. Mutasi tidak mengganti write yang sedang berjalan; snapshot memeriksa revisi auth/device ID dan SET memerlukan readback.

**Belum selesai:** start/stop serta status motor patroli, status terminal kalibrasi, sensitivitas gerakan, varian editor area non-flat/ambigu, enum terminal format SD, dukungan Legacy/multi-view variants dan acceptance firmware. Format SD sudah aktif dengan kontrak ACK vendor; enum terminal `131330` tetap belum dapat dipercaya. Kalibrasi hanya melaporkan ACK/hasil ambigu dan meminta pemeriksaan gerak oleh pengguna. Restart yang tersambung kembali tidak dinyatakan terverifikasi tanpa bukti reboot.

[Validasi terbaru](validation/area-stage3/results.json) mencatat build/lint/tes offline pada source akhir. UI/storage instrumentation baru dikompilasi; runtime tetap belum berjalan; boot tidak diulang pada lingkungan yang sama dengan API35/API26 KVM yang sebelumnya SIGSEGV. Tidak ada screenshot baru, pengujian socket, atau kamera yang diklaim lulus. Penanda format versi1 dibaca sebagai unknown tanpa replay; versi2 memakai operation ID untuk cleanup. Pending restart terpisah dari AP/provisioning dan tidak menyimpan credential.

Editor area tahap3 tersedia hanya untuk grid flat/single-view yang terkonfirmasi, dengan reference session, PixelCopy setelah frame render, dan preflight/readback. [Kontrak, batas crop/orientasi/motor, dan hasil](analysis/area-stage3-wire.md).

## Rekaman dan pengaturan0.3.0

Detail kamera sekarang memiliki tab Live, Rekaman dan Pengaturan, tanpa dependensi baru. Fitur IoT memakai transport/auth/crypto yang sama; Legacy mengembalikan unsupported untuk fitur baru. [Spesifikasi dan bukti REA/DEX/ELF](analysis/recordings-settings-wire.md).

| Fitur | Hasil implementasi |
|---|---|
| Rekaman SD | Daftar per hari/semua atau peristiwa, slice berkorelasi, playback AVC/HEVC berpacing timestamp, jeda dan seek melalui sesi baru. |
| Salin ke Android | Satu segmen video-only, persetujuan audio tidak disertakan, SAF sebelum transfer, MediaMuxer tanpa antrean drop live, pemeriksaan MediaExtractor sebelum publikasi dan cleanup parsial. |
| Pengaturan | Orientasi lewat action kamera; lampu otomatis/hidup/mati, indikator, mode/kualitas/audio/loop rekaman sesuai nilai dan ability yang dibaca. Write satu kali, ACK per properti dan readback. |
| SD | Status dan persentase ruang tersisa. Format aktif sejak17Oktober2026 dengan kontrak ACK vendor: ACK berkorelasi1000 = diterima, transisi4→1 = selesai; enum terminal `131330` tidak ditafsirkan. Konfirmasi, pending send-once, dan status recovery tersedia. |
| Lifecycle/diagnostik | Satu media aktif, cancel/join, cleanup background, draft/leave confirmation; diagnostik schema3 menambah tahap rekaman/playback/download/settings/format. |

**Validasi0.3.0:** APK debug dan APK instrumentation terbangun, lint0 error, 40 tes JVM offline lulus. Tes Android baru untuk AtomicFile pending, MP4 sintetis dan UI native dikompilasi, tetapi belum dijalankan: emulator37.2.12 mengalami SIGSEGV saat boot pada API37 maupun API30 dengan beberapa renderer dan tanpa akselerasi. Tidak ada screenshot baru atau hasil native ekspor yang diklaim lulus. [Hasil/command/log](validation/recordings-settings/results.json). Tes koneksi, loopback session dan kamera tidak dijalankan sesuai instruksi pengguna. Paragraf validasi0.2.1 dan runtime historis di bawah bukan pengujian fitur baru.

MQ/pver≥5, interoperabilitas firmware, video/ekspor nyata, ketahanan setting setelah reconnect, serta hasil format fisik masih belum verified. Sensitivitas dan jadwal lampu tidak diberi kontrol tanpa label/kontrak yang terbukti. Rencana diimplementasikan dengan gerbang per fitur tersebut; format aktif dengan kontrak ACK vendor, tetapi acceptance fisik belum selesai.

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
