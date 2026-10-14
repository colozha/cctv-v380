# Rencana pengembangan manajemen kamera V380 melalui LAN

Tanggal: 14 Oktober 2026. Status: **implementasi sebagian pada debug0.4.2**. [Perilaku aktual, kontrak, dan pekerjaan tersisa](camera-management-wire.md).

Dokumen ini mencakup lima kelompok fitur: pengaturan alarm/deteksi gerakan; preset, patroli, dan kalibrasi PTZ; jadwal/sensitivitas lampu; restart/informasi versi; serta aktivasi format kartu SD dengan verifikasi hasil. Penyusunan dokumen hanya membaca source dan bukti analisis yang tersedia; tidak mengubah kode atau menjalankan operasi kamera.

Tahap2: tambah/hapus interval dan validasi draft tersedia; [bukti/status](alarm-stage2-wire.md). Sensitivitas gerakan masih terkunci, dan runtime UI/storage belum berjalan karena kedua emulator gagal boot.

Tahap3: editor area flat/single-view dan PixelCopy sudah terintegrasi; [bukti/status](area-stage3-wire.md). Runtime Android/visual dan acceptance kamera tetap belum terverifikasi.

## 1. Tujuan, batas, dan kondisi awal

Pemilik kamera dapat mengelola operasi perangkat melalui koneksi lokal yang sudah terautentikasi, dengan status yang membedakan nilai terbaca, perintah diterima, hasil terverifikasi, dan hasil belum diketahui. Semua fitur menggunakan sesi kamera yang sama dengan aplikasi sekarang.

Keputusan dasar:

- Target pertama adalah **IoT LAN pada build debug**, mengikuti pengaturan/rekaman yang sudah ada. Dukungan Legacy untuk lima fitur baru merupakan tahap terpisah; bukti command Legacy disimpan sebagai referensi, bukan dipakai sebagai fallback otomatis.
- Pertahankan Kotlin, Compose Material 3, Android API26+, satu modul aplikasi, transport/kripto yang tersedia, dan satu kamera aktif. Tidak menambah SDK vendor, dependensi, service background, atau framework pengaturan baru.
- Tidak mencakup akun/cloud V380, binding, pairing, perubahan password/akses, konfigurasi jaringan, OTA, factory reset, push notification, deteksi AI, atau auto tracking. Pengaturan alarm di sini berlaku pada kamera; bukan layanan notifikasi aplikasi.
- Preset/patroli/kalibrasi hanya untuk view/channel pertama yang saat ini didukung. PTZ arah biasa tetap memakai kemampuan per sumbu dan watchdog yang ada.
- Jadwal dijalankan oleh kamera, bukan timer Android. Pembacaan zona waktu kamera boleh ditambahkan untuk menjelaskan jadwal; editor waktu/zona waktu kamera tidak termasuk pekerjaan ini.
- Nilai command, enum, unit, schema, dan capability yang belum terbukti harus dipulihkan sebelum writer terkait diaktifkan. Gerbang yang masih terkunci berarti fitur tersebut belum selesai, bukan alasan mengklaim seluruh pengembangan tuntas.

Kondisi awal yang perlu dipertahankan:

| Area | Implementasi saat ini | Celah yang ditangani |
|---|---|---|
| Pengaturan | Baca properti, write, ACK per properti, dan readback; orientasi/lampu dasar/perekaman tersedia | Belum ada alarm, jadwal lampu, dan informasi perangkat; parser terutama menerima scalar |
| PTZ | Empat arah, stop, lease/watchdog, kemampuan horizontal/vertikal | Belum ada daftar preset, simpan/panggil/hapus, patroli, atau kalibrasi |
| Sensitivitas lampu | DPID `327939`, nilai 1–3 dikenali protokol | UI belum tersedia karena arti label/rentang belum lengkap |
| Format SD | Action, konfirmasi, pending AtomicFile, dan polling dasar tersedia | `FORMAT_STATUS_VERIFIED=false`; belum ada evaluator status terminal dan fase selesai |
| Koordinasi | `launchFeature` membatalkan pekerjaan sebelumnya; AP edit mempunyai penjagaan pengiriman sendiri | Pola replace-job tidak boleh dipakai langsung untuk aksi motor/restart/format |

Dasar implementasi: [status proyek](../IMPLEMENTATION.md), [kontrak pengaturan/SD](recordings-settings-wire.md), [analisis PTZ](discovery-ptz.md), dan [rencana rekaman/pengaturan sebelumnya](recordings-settings-implementation-plan.md). Dokumen ini memperluas fitur terkait; tidak mengulang pengembangan rekaman atau hotspot.

## 2. Pembuktian protokol sebelum implementasi

Bukti berasal dari APK V380 6.4.90, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Inventaris kelas/properti membuktikan keberadaan API, bukan otomatis payload lengkap, jalur offline, atau dukungan firmware pengguna.

| Kelompok | Bukti yang sudah tersedia | Yang wajib dilengkapi |
|---|---|---|
| Alarm/gerakan | `createAlarmConfigPidList`, `createAlarmConfigPidList1`, parser `AlarmConfigInfo`; nama properti main switch, motion switch, area, sensitivity, timer, suara alarm | Body getter/setter dan caller UI; ID numerik, format area/jadwal, enum sensitivitas, capability, izin, serta respons parsial |
| Preset | `PTZXController.setPTZXPoint`; caller mendukung action102=simpan dan103=panggil; JSON `ctrl_flag`, `pos_mark_id_cnt`, `pos_mark_id` | Konstanta DAID, baca daftar/status/kapasitas, rentang ID, hapus/timpa, hasil action, channel mask, serta semantik readback |
| Patroli/kalibrasi | `createPTZXCruiseConfigPidList`, `DPID_PT_CRUISE_INFO`, action104 menuju `DAID_PT_RECAL` | Schema rute/jadwal jika tersedia, start/stop, durasi berhenti di preset, status gerak/kalibrasi, dan apakah kalibrasi dapat diinterupsi |
| Lampu | Body `createWhiteLightConfigPidList` membentuk objek jadwal; DPID sensitivitas dan nilai1–3 diketahui | Format waktu literal yang disanitasi, satuan, jumlah interval, default action, capability, lintas tengah malam, hubungan mode otomatis/jadwal, dan arti sensitivitas |
| Restart/versi | Nama `DAID_BASIC_REBOOT`, `getVersionConfigInfoNewPlatform`, properti versi perangkat/hardware/module | Payload restart, ACK/error, jalur LAN, getter/parser versi; indikator reboot seperti uptime/boot ID bila memang tersedia |
| Format SD | Action `131584`; SD status `131328`, kapasitas `131329`, format state `131330`; request tanpa `in` | Enum running/sukses/gagal, hubungan dengan SD status, reset/staleness status, korelasi operasi, serta perilaku setelah reboot/kartu dilepas |

Sumber utama: [inventaris DeviceConfigSetting](evidence/ap-class-com_macrovideo_sdk_setting_DeviceConfigSetting.sanitized.json), [inventaris Protocol](evidence/class-Protocol.json), [body jadwal lampu](evidence/features-DeviceConfigSetting-createWhiteLightConfigPidList-0.sanitized.json), dan [body format SD](evidence/features-DeviceSDCardSetting-setSDCardFormat-0.sanitized.json). Evidence ID asal dipertahankan dalam berkas REA; excerpt tersanitasi bukan evidence kanonis pengganti.

Tahap pembuktian dilakukan read-only menggunakan REA/JADX, DEX untuk cabang dekompilasi meragukan, dan ELF/native hanya bila jalur Java tidak cukup. Tidak menjalankan APK vendor, mengambil token akun, atau mengirim perintah kamera pada tahap ini.

Hasil wajib tahap ini:

1. Tambahkan spesifikasi kontrak fitur dan excerpt tersanitasi ke `analysis`, lalu perbarui indeks bukti saat implementasi nanti. Setiap kontrak mencatat sumber, payload, respons, capability, izin, batas input, routing, dan definisi hasil.
2. Buktikan jalur direct LAN per fitur/profil. Jalur MQ/cloud yang terlihat pada sebagian `pver` tidak ditiru; fitur tanpa jalur lokal yang terbukti tetap terkunci dengan alasan yang sesuai.
3. Siapkan vector sintetis untuk request/response dan status. Tidak mengisi kekosongan bukti dengan angka dari nama konstanta atau source pembanding.
4. Jika status terminal format atau stop patroli belum dapat dipulihkan secara statis, catat kebutuhan observasi firmware untuk acceptance berikutnya. Jangan mengaktifkan aksi terkait sebelum bukti yang menentukan keselamatan/status tersedia.

## 3. Integrasi sesi, model, dan penyimpanan

### 3.1 Model dan antarmuka internal

Tidak ada API publik eksternal baru. Perluas model internal dengan snapshot kecil untuk alarm, jadwal lampu, preset/rute patroli, status motor, informasi versi, dan hasil format/restart. Gunakan tipe konkret untuk objek jadwal/area/daftar; jangan membuka parser `Map<Int, Any>` agar menerima sembarang JSON.

- Tambahkan status dukungan per kemampuan: belum diketahui, didukung, tidak didukung, atau memerlukan cloud yang sudah terbukti. Penolakan izin tetap error terpisah. Bit PTZ arah tidak otomatis mengizinkan preset/patroli/kalibrasi.
- Setiap snapshot/draft terkait profil dan sesi yang membacanya. Reconnect, pergantian kamera, dan auth expiry menginvalidasi snapshot sebelum write baru.
- Tambahkan operasi internal pada pemilik sesi yang ada: baca/simpan alarm; baca/simpan jadwal dan sensitivitas lampu; baca/simpan/panggil/hapus preset; baca/simpan/start/stop patroli; kalibrasi/baca status; baca versi; kirim restart; baca status format. Hanya operasi dengan kontrak lengkap yang diekspos ke UI.
- Gunakan `CameraSettingsProtocol` untuk properti scalar dan struktur pengaturan yang terbukti, serta `IoTControlProtocol` untuk action PTZ/restart. Ekstrak codec khusus hanya jika struktur alarm/jadwal membuat file sulit dibaca; tidak membuat registry/plugin protokol.
- GET/SET tetap menggunakan common IoT `197376/197377` dan `197378/197379`; action memakai `197384/197385`. Batas GET10 properti, enkripsi, request ID, method, SID, channel, deadline, dan batas ukuran tetap diperiksa.
- Parser daftar/jadwal membatasi jumlah elemen, kedalaman, panjang string, dan ukuran payload; batas firmware mengikuti capability/kontrak yang dipulihkan. Unknown enum tetap unknown dan tidak menjadi pilihan default.

### 3.2 Satu operasi mutasi aktif

Perluas penjagaan pekerjaan yang ada dengan satu status operasi aktif. Tandai busy secara sinkron sebelum coroutine dijalankan agar double tap tidak membuat dua submit. Pembacaan boleh dibatalkan/diganti; mutasi tidak memakai pola membatalkan write lama lalu mengirim write baru.

Sebelum mengirim, session memeriksa ulang autentikasi valid, identitas profil/device, kepemilikan sesi/generation, dukungan, pending operasi, dan cancellation. Pemeriksaan ini wajib meskipun caller melewati UI. Ambil generation setelah penghentian media jika penghentian tersebut menaikkan generation.

- Konflik restart/format/AP/provisioning/download tidak boleh saling membatalkan untuk kemudian menulis. Pengguna menyelesaikan atau membatalkan pekerjaan lokal terlebih dahulu; format yang sedang berjalan di kamera tetap memblokir mutasi baru pada kamera tersebut.
- Settings memanfaatkan state Ready tanpa Surface. Preset/patroli dijalankan dari Live ketika stream dan capability valid. Kalibrasi, restart, dan format menghentikan media/PTZ terlebih dahulu melalui pemilik sesi yang sama.
- Perintah motor manual, panggil preset, patroli, dan kalibrasi saling eksklusif. Peralihan dari patroli membutuhkan stop dan verifikasi status; jangan mengasumsikan step-stop biasa menghentikan cruise.
- Cancellation menutup channel pekerjaan, bukan mengirim ulang. Stop motor yang terbukti aman dapat dikirim best effort melalui jalur cleanup khusus, dengan deadline pendek dan tanpa bergantung pada mutex yang sedang menunggu transfer panjang.
- Hasil dari profil/sesi/generation lama tidak boleh mengganti state baru. Reconnect hanya membaca kemampuan/status; tidak memutar ulang write, gerakan, patroli, kalibrasi, restart, atau format.

### 3.3 Write pengaturan dan pemulihan

Kirim hanya field yang diubah, memakai snapshot kamera terbaru. Pertahankan bit/field konfigurasi lain yang tidak dimiliki editor. Bila protokol mensyaratkan satu objek penuh, gabungkan perubahan ke objek asli yang telah divalidasi; jangan membuang field tambahan.

ACK sukses per properti diikuti readback dan perbandingan semantik. Jika sebagian properti diterima dan sebagian ditolak, laporkan per field setelah rekonsiliasi; tidak rollback atau mengulang seluruh batch otomatis. Jika timeout setelah write mungkin dimulai, status menjadi **Hasil belum diketahui**, lalu baca ulang saja.

Format dan restart memakai pending marker nonrahasia sebelum write, dengan `operationId`, profil, device ID, waktu, dan bukti baseline yang diperlukan untuk verifikasi. Gunakan `AtomicFile` melalui `ProfileStore`; marker restart terpisah dari format, provisioning, dan AP edit. Tidak menyimpan password, SID, key, payload, atau footage.

Perluas pending format lama dengan versi dan operation ID; marker versi1 dibaca sebagai operasi lama yang hasilnya belum diketahui dan tidak pernah dikirim ulang. Cleanup harus cocok dengan operation ID agar callback lama tidak menghapus marker baru. Marker rusak/gagal dibaca tidak dihapus diam-diam dan memblokir aksi berisiko sampai status lokal ditangani.

Jaminan client adalah **maksimal satu pengiriman per submit**, bukan exactly-once di kamera. Crash antara penulisan marker dan socket write tetap dianggap mungkin telah terkirim; pemulihan hanya membaca status. Penyimpanan marker gagal sebelum write menghasilkan **Belum dikirim**.

## 4. Perilaku lima kelompok fitur

### 4.1 Pengaturan alarm dan deteksi gerakan

- Tambahkan grup **Alarm dan deteksi gerakan** pada tab Pengaturan: alarm utama, deteksi gerakan, sensitivitas, area deteksi, jadwal aktif, serta suara alarm jika didukung. Tiap kontrol aktif berdasarkan kontrak dan readback masing-masing; dukungan salah satu field tidak membuka semuanya.
- Bedakan switch deteksi/alarm dari **Rekam peristiwa**. Mengaktifkan alarm tidak otomatis mengubah mode rekaman, dan perubahan recording tidak mengubah detector.
- Editor area memakai representasi yang benar-benar dilaporkan firmware. Jika grid, pertahankan ukuran/grid mask asli; jika rectangle, pakai batas koordinat hasil kontrak. Jangan mengonversi antarformat dengan tebakan. Ambil gambar referensi saat diminta, lalu hentikan live sebelum kembali mengedit; bila referensi tidak dapat dipetakan dengan benar, editor area terkunci dan pengaturan lainnya tetap tersedia.
- Jadwal mengikuti waktu kamera dan kemampuan harian/mingguan yang terbukti. Tolak interval invalid/overlap dan jumlah melebihi kemampuan; lintas tengah malam hanya ditawarkan bila semantiknya terbukti. Jangan membelah interval atau mengubah hari secara diam-diam.
- Simpan perubahan sebagai satu submit, baca ulang, dan tampilkan error per kontrol. Switch utama off tidak menghapus konfigurasi area/sensitivitas/jadwal tersimpan.

Selesai ketika konfigurasi terbaca, dapat diubah dan diverifikasi tanpa mengubah recording; acceptance fisik juga membuktikan perubahan detector/alarm melalui gerakan nyata dan jadwal, tanpa menjadikan push cloud sebagai syarat.

### 4.2 Preset, patroli, dan kalibrasi PTZ

- Tambahkan bagian **Preset dan patroli** di bawah kontrol arah pada tab Live. Daftar slot berasal dari kamera, dengan aksi **Simpan posisi**, **Ke posisi**, dan **Hapus** sesuai kontrak. Jika nama preset tidak tersedia, gunakan label slot; tidak menambah database nama lokal.
- Simpan hanya ke slot yang valid. Menimpa slot yang sudah terisi meminta konfirmasi; hapus juga dikonfirmasi. Baca ulang daftar/status setelah perubahan. ACK panggil preset berarti **Perintah gerak diterima**; label **Posisi tercapai** memerlukan status perangkat yang membuktikannya.
- Patroli memakai engine/rute native kamera. Pengguna memilih preset, urutan, dan waktu berhenti hanya jika didukung firmware; sediakan **Mulai patroli**, **Hentikan patroli**, dan status berjalan. Tidak membuat loop gerak berbasis timer Android sebagai pengganti fitur native yang belum terbukti.
- Start patroli memerlukan kontrak stop dan status yang jelas. Stop yang gagal membuat status motor belum diketahui dan memblokir gerakan baru sampai status dibaca kembali. Perubahan rute dilakukan saat patroli berhenti.
- Saat meninggalkan Live, background, disconnect, kehilangan Surface, atau sebelum pekerjaan eksklusif lain, coba hentikan patroli yang dimulai aplikasi ini. Jangan mematikan patroli/jadwal kamera yang sudah aktif sebelum sesi tanpa tindakan pengguna. Tidak mengaktifkan kembali patroli otomatis setelah reconnect.
- **Kalibrasi PTZ** berada di Pengaturan, grup **Gerak kamera**, dengan konfirmasi bahwa lensa akan bergerak. Tampilkan diterima/berjalan/selesai hanya sesuai status perangkat. Jika firmware tidak menyediakan pembatalan kalibrasi, UI menjelaskan bahwa keluar layar hanya menghentikan pemantauan.
- Stop best effort tidak menjamin motor berhenti ketika jaringan/proses mati. Pada reconnect, baca status dan tawarkan stop; hasil stop yang belum terverifikasi tetap diberi label demikian.

Selesai ketika daftar/simpan/panggil/hapus preset, native patrol start/stop, dan kalibrasi bekerja pada profil yang didukung, tanpa bentrok dengan PTZ arah dan tanpa gerakan ulang dari pemulihan.

### 4.3 Jadwal dan sensitivitas lampu

- Perluas grup **Lampu** yang sudah ada dengan sensitivitas dan daftar interval jadwal. Pertahankan pilihan lampu otomatis/hidup/mati serta indikator yang tersedia sekarang.
- Nilai1–3 tidak langsung diberi label Rendah/Sedang/Tinggi sebelum arah sensitivitas terbukti. Pulihkan label/arti dari caller UI, resources, dan parser; jangan menukar sensitivitas dengan brightness.
- Editor jadwal menyediakan tambah, ubah, hapus interval dan aksi default jika kontrak mendukung. Pertahankan capability/total/default action dan field lain ketika mengirim objek jadwal penuh. Nonaktifkan edit objek yang tidak dapat diparse secara aman.
- Tampilkan bahwa jadwal berjalan mengikuti waktu kamera dan tetap berjalan saat aplikasi ditutup. Jika zona waktu kamera belum dapat dibaca, tampilkan **Zona waktu kamera belum diketahui**; jangan menyamakan dengan zona Android.
- Terapkan aturan waktu, jumlah interval, overlap, lintas tengah malam, dan hubungan mode lampu berdasarkan kontrak tahap pembuktian. Jika mode manual mengesampingkan jadwal, jelaskan efeknya sebelum simpan; jangan mengganti mode diam-diam.
- Readback membandingkan interval dan aksi secara semantik, bukan urutan key JSON. Sensitivitas dapat aktif terpisah bila kontraknya lengkap meskipun jadwal masih terkunci.

Selesai ketika sensitivitas dan jadwal dibaca/disimpan/terverifikasi, bertahan setelah reconnect, dan lampu berubah sesuai interval pada acceptance fisik.

### 4.4 Restart kamera dan informasi versi perangkat

- Tambahkan grup **Perangkat** pada Pengaturan: model, versi firmware/software, hardware, dan module yang benar-benar tersedia, dilengkapi **Muat ulang informasi**. Nilai tidak tersedia diberi keterangan; jangan memakai nama firmware target dari dokumentasi sebagai hasil baca.
- **Restart kamera** adalah aksi terpisah dengan konfirmasi bahwa tayangan/rekaman aktif akan terputus. Factory reset, OTA, dan perubahan akses tidak termasuk aksi ini.
- Sebelum restart: pastikan tidak ada draft, pending AP/format, transfer, atau gerakan aktif; hentikan media/PTZ/patroli aplikasi, baca baseline versi/status reboot bila tersedia, simpan marker, dan kirim satu kali dengan respons serta korelasi ketat.
- Setelah ACK atau putus/timeout yang mungkin terjadi sesudah write, hentikan reconnect stream biasa dan invalidasi sesi lama. Verifikasi dengan discovery device ID yang sama dan autentikasi ulang; host/port dapat diperbarui hanya setelah identitas cocok. Tidak mengirim restart kembali.
- Alur tampil: **Belum dikirim → Mengirim → Menunggu kamera → Kamera tersambung kembali**, dengan penolakan dan hasil belum diketahui sebagai hasil terpisah. Batas verifikasi foreground120 detik adalah kebijakan client, bukan durasi reboot firmware.
- **Restart terverifikasi** hanya jika indikator boot/uptime/status yang telah dibuktikan menunjukkan reboot baru. Koneksi kembali saja menghasilkan **Kamera tersambung kembali; restart belum dapat dipastikan** bila tidak ada bukti tersebut.
- Gunakan akses tersimpan atau salinan sementara password dari sesi yang sah untuk verifikasi foreground; hapus salinan pada selesai/batal/background/timeout. Bila tidak tersedia, minta password lewat alur koneksi yang sudah ada. Tidak menyimpan credential baru demi recovery.
- Restore proses hanya menawarkan **Periksa kamera**, menggunakan marker sebagai catatan hasil yang belum diketahui. Memulai live lagi mengikuti tab/aksi pengguna dan tidak mengulangi restart.

Selesai ketika informasi perangkat berasal dari kamera, restart dikirim maksimal sekali, koneksi kamera yang sama pulih, dan UI tidak menyamakan terhubung kembali dengan bukti reboot.

### 4.5 Format kartu SD

- Gunakan UI konfirmasi dan action yang sudah ada. Aktivasi memerlukan evaluator status terminal yang dibuktikan untuk profil firmware; tidak cukup mengganti `FORMAT_STATUS_VERIFIED` menjadi true. Ganti gerbang global dengan pemeriksaan dukungan kontrak profil dan status sesi.
- Sebelum action, baca status terbaru, pastikan kartu eligible, tidak sedang format, tidak ada draft/pending operasi/transfer, hentikan media/PTZ, dan tulis marker atomik. Kirim action `131584` tanpa `in`, dengan respons; tidak membawa `no_rsp:true` dari PTZ.
- Tambahkan fase **SENDING, CHECKING/RUNNING, COMPLETE, FAILED, REJECTED, UNKNOWN**. FAILED memerlukan status gagal terminal yang terbukti; timeout monitoring menjadi UNKNOWN.
- Pantau status memakai polling2 detik dengan batas awal120 detik foreground. Status terminal diperiksa setiap respons dan polling dihentikan segera setelah hasil terverifikasi; bukan menjalankan polling tetap lalu selalu mengembalikan UNKNOWN seperti sekarang.
- Sukses memerlukan status terminal yang terhubung dengan operasi saat ini: transisi berjalan→terminal, operation ID bila tersedia, atau mekanisme lain yang memang dibuktikan kontrak. Nilai terminal lama, ACK, SD normal, ruang bertambah, dan daftar rekaman kosong tidak cukup sendirian.
- Setelah sukses, baca ulang status/kapasitas dan invalidasi daftar/segmen rekaman. Jika status gagal, tampilkan sebab yang terbukti; jangan mengirim format ulang otomatis. Kartu dilepas atau kamera restart saat pemantauan menghasilkan unknown kecuali protokol memberi hasil definitif.
- Background/process death menghentikan pemantauan lokal, bukan format kamera. Saat kembali, **Periksa status kartu** hanya membaca/evaluasi. Jika proses mati membuat korelasi hasil hilang, tetap UNKNOWN meskipun kartu normal.
- Pending dihapus hanya untuk operasi yang hasil terminalnya sudah dicatat atau setelah pengguna memilih menutup catatan dengan penjelasan bahwa hasil belum terverifikasi. Menutup catatan tidak mengizinkan format baru bila kamera masih melaporkan sedang format atau status mutakhir belum tersedia.

Selesai ketika format tersedia pada kontrak yang memenuhi syarat, tidak terkirim ulang pada semua jalur pemulihan, dan status selesai/gagal mempunyai bukti yang dapat diuji.

## 5. Integrasi UI, error, dan diagnostik

Tetap gunakan tiga tab Live/Rekaman/Pengaturan. Pengaturan baru menjadi grup dalam tab yang ada; tidak membuat dashboard atau navigasi baru. Form panjang dapat memakai bagian yang dibuka sesuai kebutuhan dengan komponen Material yang tersedia.

- Loading/error untuk setiap grup terpisah: kegagalan alarm tidak menyembunyikan versi atau status SD. Muat tiap grup saat dibuka; tidak menambah seluruh properti ke satu request settings besar.
- Setiap grup memiliki draft dan aksi simpan sendiri. Navigasi keluar dengan draft meminta buang/tetap; pending format/restart menjelaskan bahwa keluar hanya menghentikan pemeriksaan. Batal tidak pernah diklaim membatalkan aksi yang sudah berjalan di kamera.
- Bedakan belum tersambung, capability unknown, tidak didukung, izin ditolak, auth kedaluwarsa, ditolak kamera, belum dikirim, dan mungkin terkirim. Timeout tidak diubah menjadi pesan password salah atau sukses.
- Gunakan target sentuh48dp, label TalkBack untuk tombol/icon/slider/area, font scaling, scroll/IME insets, portrait/landscape, dan tema sistem. Pilihan waktu memakai kontrol Material; tidak menambahkan library jadwal.
- Tambahkan tahap diagnostik ALARM, PTZ_PRESET, PTZ_CRUISE, PTZ_CALIBRATION, LIGHT_SCHEDULE, DEVICE_INFO, RESTART, dan RESTART_VERIFY; SD_FORMAT tetap dipakai. Catat kode, fase, durasi, dan alasan yang di-allowlist. Tidak mencatat area/jadwal lengkap, nama preset, payload versi mentah, footage, password, SID, atau key.
- Perbarui schema diagnostik bila format ekspornya berubah dan uji kompatibilitas pembaca. Dokumentasi status harus membedakan implementasi debug, hasil tes offline, runtime Android, dan acceptance firmware.

## 6. Urutan implementasi dan dependensi

| Tahap | Pekerjaan | Kriteria keluar |
|---|---|---|
| 0. Kontrak | Lengkapi bukti lima kelompok, routing LAN, capability, enum, dan definisi hasil | Spesifikasi serta vector sintetis tersedia untuk setiap writer yang akan diaktifkan |
| 1. Koordinasi | Perkuat single-flight mutasi, sesi/generation, model grup, pending operation ID/migrasi, dan diagnostik | Double tap, stale session, cancellation, serta restore tidak mengirim mutasi ulang |
| 2. Pembacaan | Informasi versi, snapshot alarm/lampu, daftar/status preset/patroli/kalibrasi, evaluator format | Unknown/unsupported/error dapat dibedakan; hasil grup independen |
| 3. Konfigurasi | Alarm/gerakan dan sensitivitas/jadwal lampu beserta editor, validasi, readback | Perubahan parsial/ditolak/ambigu ditampilkan benar tanpa replay |
| 4. Motor | Simpan/panggil/hapus preset, konfigurasi/start/stop patroli, kalibrasi dan cleanup | Stop/status terbukti, motor eksklusif, tidak bergerak ulang setelah reconnect |
| 5. Pemeliharaan | Restart/verifikasi dan aktivasi format untuk profil yang memenuhi syarat | At-most-once per submit, marker pulih, hasil terminal tidak berasal dari ACK saja |
| 6. Validasi | Regresi, UI/storage Android, dokumentasi hasil, lalu acceptance kamera ketika pengujian dibuka | Bukti per fitur/profil lengkap; fitur tanpa acceptance tetap eksperimental |

Tahap1 diperlukan oleh semua mutasi; tahap2 menyediakan snapshot untuk tahap3–5. Setelah fondasi lulus, fitur dengan kontrak lengkap dapat dilanjutkan tanpa menunggu kontrak kelompok lain. Format tetap tertahan bila enum/korelasi terminal belum terbukti. Tidak menetapkan estimasi kalender sebelum tahap0 menunjukkan sisa pembuktian.

Area perubahan saat implementasi: model/protokol pengaturan dan action; pemilik sesi/ViewModel; komponen Live/Pengaturan; pending AtomicFile; diagnostik serta tes yang sudah ada. Pertahankan satu pemilik sesi dan helper transport/kripto yang sama. Hindari refactor umum di luar kebutuhan lima kelompok ini.

## 7. Pengujian dan acceptance

Gunakan JUnit/instrumentation yang sudah dipasang. Tes baru harus membuktikan kontrak atau kegagalan bermakna, bukan menguji getter atau menduplikasi implementasi.

| Area | Kasus minimum dan hasil yang diharapkan |
|---|---|
| Kontrak/parser | Payload sintetis valid; JSON/type/enum invalid; ID/method/SID/channel salah; response duplicate/missing; daftar/jadwal terlalu besar; semua input invalid menghasilkan nol write |
| Alarm | Switch utama/gerakan independen dari recording; boundary sensitivitas; area di luar batas; jadwal invalid/overlap; field tak diedit tetap utuh; partial ACK direkonsiliasi per field |
| Lampu | Arti sensitivitas sesuai evidence; waktu dan batas interval; default action terjaga; timezone unknown tidak memakai asumsi Android; readback objek dinilai semantik |
| Preset | ID/slot salah, penuh, overwrite/hapus, response ditolak, daftar stale, view salah; ACK panggil tidak menghasilkan posisi-tercapai palsu |
| Patroli/kalibrasi | Start/stop/status, stop gagal, manual-versus-cruise, route edit saat jalan, keluar/background/lost Surface; kalibrasi non-cancellable tidak diberi tombol batal palsu |
| Restart | Nol write jika preflight/marker gagal; maksimal satu pada double tap/partial write/ACK hilang/process restore; suppress reconnect stream; ID berbeda tidak diterima; koneksi kembali tanpa boot evidence tidak disebut reboot sukses |
| Format | Running→sukses/gagal, terminal stale, cepat selesai tanpa korelasi, ACK saja, kartu dicabut, reboot, polling timeout, resume; tidak ada format ulang atau sukses dari SD normal/daftar kosong |
| Storage/race | Marker versi1, corrupt, disk penuh, crash sebelum/sesudah write, cleanup operation ID lama, ganti profil, auth expiry saat menunggu mutex; tidak menimpa pending AP/provisioning |
| Regresi | Auth, cipher/parser, quality/PTZ stop, rekaman/playback/ekspor, hotspot/provisioning, lifecycle, dan ekspor diagnostik tetap mengikuti perilaku yang ada |
| UI Android | Belum tersambung, loading/unknown/error/partial success, draft, dialog motor/restart/format, pending recovery, TalkBack, font scale1.3, light/dark, portrait/landscape |

Build/check implementasi nanti mengikuti [command offline](../validation/offline-build-command.txt): debug APK, instrumentation APK, lint, dan tes JVM terpilih tanpa koneksi. Perluas seleksi tes untuk parser/state/storage baru; jangan menjalankan seluruh suite koneksi lewat perintah umum tanpa memeriksa batas yang berlaku. Kompilasi instrumentation tidak dicatat sebagai runtime test lulus.

[Pengujian koneksi masih ditunda](../validation/connection-tests-deferred/status.json), termasuk sesi sintetis yang memakai socket. Penyusunan dokumen ini tidak membuka penundaan tersebut. Build/tes offline dan UI/storage tanpa koneksi dapat dilakukan pada tahap implementasi; acceptance berikut merupakan pekerjaan mendatang setelah pengujian koneksi diizinkan kembali:

- Catat firmware/platform/pver dan dukungan per fitur, lalu jalankan fitur dengan internet diblokir. Sukses live tidak dianggap bukti semua fitur bekerja offline.
- Uji alarm menggunakan gerakan di dalam/luar area, sensitivitas, jadwal, dan suara kamera sesuai kemampuan; periksa bahwa recording tidak berubah tanpa aksi pengguna.
- Uji preset berulang, patroli/stop dan kehilangan jaringan, serta kalibrasi pada kamera nyata. Nilai gerakan/stop dari perangkat, bukan jumlah packet write.
- Uji jadwal lampu melewati batas waktu singkat yang sengaja dipilih, mode manual/otomatis, readback setelah reconnect, dan persistensi setelah reboot.
- Uji restart pada kamera yang sama, perubahan host jika terjadi, durasi pulih, versi aktual, dan indikator boot bila tersedia.
- Uji format hanya pada kartu yang datanya memang boleh dihapus, dengan konfirmasi eksplisit saat acceptance; verifikasi status terminal, ACK hilang, reconnect, status/kapasitas terbaru, dan rekaman lama tidak ditampilkan lagi.

Catat command, hash source/APK, evidence hasil, serta keterbatasan di folder validasi baru saat implementasi. Jangan memakai hasil emulator lama atau acceptance fitur lain sebagai hasil lima fitur ini.

## 8. Definisi selesai dan peluncuran

Implementasi dianggap lengkap per fitur ketika kontrak lokalnya terdokumentasi, kontrol terkait tersedia pada profil yang didukung, tes offline/race/storage lulus, dan UI menyatakan hasil sesuai bukti. Kelima kelompok dianggap selesai hanya setelah seluruh kemampuan yang diminta pada profil sasaran terpenuhi; kontrol yang masih gated dicatat sebagai pekerjaan tersisa.

Kompatibilitas kamera tetap memerlukan acceptance fisik per firmware. Pertahankan debug-only sampai acceptance dan gerbang rilis proyek lulus. Jika sebagian fitur lulus lebih dahulu, publikasikan status parsial yang jelas; jangan membuka seluruh kemampuan hanya karena auth/live berhasil.

Saat implementasi selesai, perbarui README, PRODUCT, IMPLEMENTATION, dokumentasi desain bila alur berubah, indeks bukti, dan hasil validasi sesuai perilaku final. Rollback kemampuan cukup mengunci kontrol/writer profil terkait; pembacaan pending untuk pemulihan tetap tersedia, tanpa menghapus profil/kredensial atau mengulang aksi ke kamera.
