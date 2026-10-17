# Rencana implementasi rekaman, pengaturan kamera, dan format SD

Tanggal: 14 Oktober 2026. Status implementasi: tab, daftar/playback/ekspor IoT dan pengaturan diimplementasikan pada debug0.3.0; format SD aktif sejak17Oktober2026 dengan kontrak ACK vendor, sedangkan enum terminal `131330` tetap belum terbukti. Validasi kamera tetap ditunda. [Kontrak dan batas hasil implementasi](recordings-settings-wire.md), [aktivasi format](sd-maintenance-wire.md#aktivasi-paritas-vendor--17-oktober-2026).

Tujuan: pemilik kamera dapat mencari dan memutar rekaman SD, menyimpan rekaman ke perangkat Android, mengubah pengaturan yang didukung, serta memformat SD dengan status yang dapat dipercaya. Target tetap Kotlin/Compose Material 3, Android API26+, satu modul aplikasi, akses lokal, dan adapter eksperimental pada debug sampai acceptance per firmware lulus.

Dasar: [PRODUCT](../PRODUCT.md), [DESIGN](../DESIGN.md), [status implementasi](../IMPLEMENTATION.md), [IoT LAN](iot-lan-protocol.md), [legacy](legacy-wire-spec.md), dan hasil riset REA pada percakapan sebelumnya. Impeccable dipakai dalam mode **Operate**, mengikuti identitas Material yang sudah ada; Ponytail dipakai pada level **full**, dengan transport, kripto, decoder, dan pola penyimpanan yang sudah tersedia.

**Keputusan implementasi**

- Tambahkan fitur dalam detail kamera, dengan tiga tab lokal: **Live**, **Rekaman**, **Pengaturan**. Pertahankan daftar kamera, profil, provisioning, tema, dan navigasi Back yang ada. Tidak perlu library navigasi baru.
- Autentikasi tidak bergantung pada SurfaceView atau frame live pertama. Daftar rekaman dan pengaturan dapat bekerja ketika sesi sudah terautentikasi, sesuai capability dan izin perangkat.
- Satu sesi pemilik, satu pekerjaan media aktif: live, playback, atau download. Perpindahan mode menghentikan PTZ dan pekerjaan media sebelumnya. Kembali ke tab Live memulai live hanya jika tab itu masih aktif dan Surface valid.
- Tambahan capability membedakan **belum diketahui**, **didukung**, **tidak didukung**, dan **memerlukan cloud**. Penolakan izin kamera menjadi error tersendiri; jangan menyimpulkan bahwa setiap penolakan berarti perlu binding pemilik. Semantik bit `perm` yang belum pulih tidak ditebak.
- Setiap keluarga protokol tetap mempunyai serializer/parser yang sesuai. Perangkat dipilih berdasarkan metadata discovery/handshake, bukan nama firmware, SSID, atau keberhasilan live saja. Kemampuan dan konfigurasi diperbarui pada sesi baru.
- Perubahan pengaturan menggunakan nilai eksplisit dan dibaca ulang setelah penulisan. Timeout tidak memicu pengiriman ulang otomatis. Operasi format selalu dikirim sekali; pemulihan hanya membaca status.
- Satu rekaman/segmen penuh per download pada versi pertama. Antrean massal, resume download, pemotongan rentang bebas, timeline khusus, transcoding umum, background service, dan cloud/MQ client tidak termasuk tahap awal. Batas ini tidak mengurangi fitur dasar yang diminta.

**Kontrak protokol dan gerbang bukti**

Temuan tambahan REA sebelumnya belum diarsipkan ke repo; laporan dan excerpt saat itu berada di `/tmp`, yang kini tidak tersedia. Angka di bawah adalah baseline hasil riset percakapan, bukan pengganti spesifikasi wire lengkap. Tahap 0 mengarsipkan ulang bukti yang akan dipakai sebelum serializer fitur dibuat.

| Fitur | Baseline riset | Yang harus dipastikan sebelum diaktifkan |
|---|---|---|
| Daftar rekaman IoT | `RecordFileHelper`; request/response `197152/197153`; filter, mode, start/end dalam milidetik | Enum filter/mode, nama field akhir, timezone, batas interval, format elemen, pagination, routing per `pver` |
| Playback IoT | `StartPlayBackSegment`; control `197154/197155`, info `197156` | Payload start/stop/pause/seek, metadata media, pacing, EOF, dan respons/notifikasi yang menentukan status |
| Download rekaman | `HSFileDownloader.StartDownloadRecSegment` → JNI | Kontrak transfer sebenarnya, kelengkapan data, timestamp, codec, audio, akhir transfer, dan kemungkinan framing berbeda dari live |
| Generic file download | Konstanta `197184/197185`, info `197188` tersedia | Belum terbukti sebagai jalur download rekaman SD; jangan menggunakannya berdasarkan nama saja |
| Orientasi IoT | `DPID_VIDEO_FLIP_MODE=524544`; `setCamImageOrientation(int)` | Enum normal/inversion/mirror yang benar, readback, persistensi, dan kemampuan per view |
| Lampu IoT | Floodlight switch/status/brightness/sensitivity/schedule dan indicator switch | Enum/rentang, unit, schema jadwal, capability, serta perbedaan lampu putih, indikator, dan mode night vision |
| Rekaman SD IoT | Mode `131331`, kualitas `131332`, audio `131333`, loop `131334` | Getter/setter beserta hasilnya, kemampuan kualitas dan konsistensi konfigurasi |
| Mode rekaman | Bitmask continuous=1, alarm=2, retrench=8; kualitas SD=1, HD=2, FHD=3 | DEX membenarkan bitmask, tetapi retrench dan FHD tetap mengikuti kemampuan perangkat |
| Format SD IoT | `DAID_SD_FORMAT=131584`, action `197384/197385`; status format `131330` | Request tanpa `in` dan respons berkorelasi1000 terbukti; dipakai sejak17Oktober2026. Enum status proses/terminal `131330` tetap belum terbukti dan tidak ditafsirkan |
| Format legacy | Setting version ≥2: request 256B `341`, respons 32B `441`; ada keluarga lama `141/241` | Layout dan pemilihan versi; jangan mencampurkan respons, wrapper relay, atau autentikasi keluarga lama |
| Kontrol legacy tambahan | Source pembanding: light `196`, image mode `197`, flip `190` | Validasi terhadap APK/caller native dan profil target; source pembanding saja belum cukup |

Pada jalur `pver>=5` yang ditelusuri, pencarian rekaman/playback/download dapat memakai MQ exchange meskipun media menuju IP LAN. Dukungan offline harus dibuktikan per fitur dan profil. Jika tidak ada jalur lokal yang terbukti, tampilkan alasan keterbatasannya; tidak ada fallback cloud tersembunyi.

**Alur UX yang ditetapkan**

| Permukaan | Hierarki dan interaksi |
|---|---|
| Detail kamera | Nama kamera, status koneksi, tab lokal. Sambungkan/autentikasi dapat dijalankan dari Rekaman atau Pengaturan tanpa membuat Surface live. |
| Live | Pertahankan video hitam, status, kualitas, dan PTZ. Tambahkan akses menuju dua tab lain; jangan menumpuk semua pengaturan di bawah PTZ. |
| Rekaman | Pemilih tanggal Material, filter Semua/Peristiwa jika didukung, lalu daftar waktu mulai–selesai dan durasi. Tidak memakai thumbnail palsu atau timeline khusus. Tanggal mengikuti waktu kamera dengan zona waktu yang dijelaskan. |
| Playback | Surface video yang digunakan ulang, waktu rekaman, play/stop, pause/seek jika kontraknya terbukti, dan aksi **Simpan rekaman**. Slider Material memiliki label waktu yang dapat dibaca TalkBack. |
| Download | Satu pekerjaan: menyiapkan → mengunduh → menyimpan → selesai/gagal/dibatalkan. Persentase hanya jika total diketahui; jika tidak, tampilkan byte/durasi yang diterima tanpa persentase rekaan. |
| Pengaturan | Grup **Gambar**, **Lampu**, **Perekaman**, **Kartu SD**. Tampilkan nilai yang dibaca dari kamera; simpan hanya field yang diubah lewat aksi **Simpan pengaturan**. Format merupakan aksi terpisah. |
| Kartu SD | Status dan kapasitas jika tersedia, tombol muat ulang, lalu **Format SD card**. Konfirmasi menamai kamera dan menyebut bahwa semua rekaman pada kartu akan dihapus. |

State wajib: belum tersambung, memuat, kosong, siap, sedang menyimpan, error, tidak didukung, kemampuan belum diketahui, memerlukan cloud, dan izin kamera ditolak. Daftar yang gagal dibaca tidak ditampilkan sebagai “Tidak ada rekaman”. Format tidak diberi label selesai fisik; COMPLETE hanya berarti transisi4→1 teramati setelah ACK operasi ini.

Pertahankan warna dan typography Material yang ada, tema sistem, target sentuh minimal48dp, font scaling, window/IME insets, dan navigasi Back. Pengaturan memakai layout scroll tanpa area video kosong. Video playback mengikuti batas tinggi player saat ini agar kontrol tetap terjangkau dalam landscape. Perubahan sistem visual tidak diperlukan.

**Tahap 0 — lengkapi dan arsipkan kontrak yang menentukan fitur**

1. Ekspor ulang bukti REA/JADX dan DEX yang dipakai ke `analysis/evidence`, dengan excerpt tersanitasi dan Evidence ID asal; perbarui indeks bukti.
2. Pulihkan getter/setter konfigurasi, enum orientasi/lampu, status SD/format, dan kemampuan yang relevan. Pastikan status sukses wire tidak tertukar dengan return code wrapper Java.
3. Pulihkan daftar/playback/download native secukupnya untuk profil sasaran: payload, respons, timestamp, media info, EOF, dan routing. Tetapkan batas input/ukuran di client secara eksplisit.
4. Buat matriks dukungan per keluarga dan `pver`, dengan unknown tetap unknown. Profil yang belum lengkap boleh tertahan sementara keluarga yang lengkap dilanjutkan.

Hasil: spesifikasi ringkas dan contoh paket sintetis yang dapat menjadi acuan parser. Lulus ketika fitur yang hendak dikodekan mempunyai payload, respons, pemilihan profil, dan definisi sukses/gagal yang jelas. Tidak ada operasi ke kamera pada tahap riset statis ini.

**Tahap 1 — fondasi sesi, capability, dan navigasi**

1. Perluas model pasif untuk rekaman/segmen, snapshot pengaturan, status SD, dan availability fitur. Pisahkan kualitas rekaman SD dari `Quality` live; FHD recording tidak otomatis menambah pilihan live.
2. Pisahkan alur autentikasi dan start live di `CameraViewModel`. Pertahankan `Ready` sebagai sesi terautentikasi; tambahkan state playback yang diperlukan dalam pemilik sesi yang sama.
3. Perluas cleanup/generation yang sudah ada untuk perubahan tab, seek, surface destruction, cancel, dan background. Callback Surface/generation lama tidak boleh memulai live atau menimpa playback baru.
4. Pakai mutex untuk perubahan kepemilikan/state, bukan mengunci seluruh transfer panjang sampai tombol Batal tidak bisa bekerja. Setiap job memakai channel miliknya dan cancellation menutup channel tersebut.
5. Tambahkan tahap diagnostik daftar rekaman, playback, download, settings, dan format dengan allowlist. Simpan hanya enum/kode/ukuran/durasi dan metadata protokol yang aman; tanpa payload, key, SID, handle, lokasi file, tanggal rekaman, atau footage. Versioning schema diperbarui saat format ekspor berubah.

Hasil: tab dan state dapat digunakan tanpa stream live. Lulus ketika pergantian mode selalu meninggalkan satu pekerjaan media aktif dan stale callback diabaikan. Jalur provisioning tetap memakai autentikasi yang sama.

**Tahap 2 — status SD dan pengaturan kamera**

1. Implementasikan baca konfigurasi/status SD melalui kontrak profil yang telah lengkap, menggunakan `TcpChannel`, `IoTCommonProtocol`, dan `exchangeIoT` yang sudah tersedia.
2. Tambahkan penyimpanan orientasi, mode lampu yang didukung, mode/kualitas recording, audio recording, dan loop. Brightness, sensitivity, indikator, serta jadwal muncul hanya ketika kontrak dan capability-nya tersedia.
3. Adaptasi helper action agar input boleh tidak ada dan field `in` benar-benar dihilangkan pada action tanpa input. Tetap pertahankan perilaku PTZ yang sudah teruji secara offline.
4. Validasi field/rentang dan kirim hanya perubahan pengguna. Pada ACK, baca ulang nilai kamera; baru kemudian tandai tersimpan. Pada timeout, tampilkan hasil belum diketahui dan lakukan pembacaan rekonsiliasi, bukan mengulang write.
5. Bedakan izin jaringan Android, izin operasi kamera, autentikasi, dukungan fitur, dan kebutuhan cloud. Jangan memakai pesan “akses tersimpan gagal” untuk disk penuh saat ekspor.

Hasil: pengaturan bekerja melalui sesi autentikasi tanpa decoder. Lulus ketika readback sesuai perubahan dan kegagalan tidak mengubah nilai UI menjadi sukses palsu. Mirror yang tidak didukung tidak ditawarkan sebagai pilihan.

**Tahap 3 — daftar rekaman dan playback**

1. Implementasikan pencarian per tanggal/rentang terbatas dengan parser berukuran terbatas. Validasi ID segmen, durasi, rentang waktu, jumlah elemen, duplikasi, dan pagination sesuai kontrak; jangan memuat seluruh SD ke memori.
2. Tampilkan daftar menggunakan `LazyColumn` dan key stabil. Pemilihan tanggal/filter membatalkan request sebelumnya; hasil lama tidak mengganti tanggal baru.
3. Gunakan parser/decrypt/access-unit dan `VideoDecoder` yang ada hanya jika framing playback terbukti cocok. Tambahkan bagian parsing yang berbeda secara langsung, tanpa menduplikasi transport atau kripto.
4. Playback memakai timestamp rekaman dan pacing yang sesuai; jangan memakai waktu kedatangan paket yang sekarang dipakai live. Seek membatalkan generation lama, menyiapkan ulang decoder/CSD, dan menunggu keyframe yang valid.
5. Aktifkan pause/seek hanya setelah command dan perilaku server jelas. Status memutar baru muncul setelah frame benar-benar dirender; EOF terverifikasi menjadi selesai, bukan error auth.

Hasil: pilih tanggal → pilih rekaman → putar → hentikan/seek → kembali. Lulus ketika waktu/durasi benar, perpindahan rekaman tidak menampilkan frame lama, dan live tetap dapat dibuka kembali.

**Tahap 4 — salin rekaman ke Android**

1. Versi pertama mengekspor satu segmen penuh ke MP4. Gunakan `MediaMuxer` untuk encoded samples yang kompatibel; gunakan SPS/PPS atau VPS/SPS/PPS dan timestamp asli yang dinormalisasi, tanpa re-encode video.
2. Jalur download melewati penulis sample sendiri, bukan antrean `VideoDecoder.offer`, karena antrean itu dapat membuang frame saat penuh. Terapkan backpressure dan batas memori; kehilangan/ketidaklengkapan data menggagalkan ekspor, tidak menghasilkan sukses palsu.
3. Dukung AVC/HEVC yang berhasil dipulihkan dan diuji. Pertahankan audio bila formatnya kompatibel dan terbukti; audio yang belum didukung diberitahukan sebelum transfer, dengan pilihan eksplisit **Simpan video tanpa audio**. Tidak membuang audio diam-diam atau menambah FFmpeg.
4. Gunakan `ActivityResultContracts.CreateDocument`/Storage Access Framework sebagai satu alur tujuan file pada API26+. Pilih tujuan sebelum transfer dimulai. Jika picker membuat sesi ditutup oleh lifecycle, autentikasi ulang setelah kembali; minta akses lokal lagi bila password tidak disimpan.
5. Tulis MP4 sementara di cache, finalisasi, lalu salin ke URI pilihan. Ini menghindari asumsi bahwa setiap document provider dapat di-seek oleh muxer. Periksa ruang cache, tangani disk penuh/provider gagal, dan bersihkan file sementara pada selesai, batal, error, serta sisa proses sebelumnya.
6. Bila penyalinan tujuan terputus, hapus dokumen parsial jika provider mengizinkan; bila tidak, beri pesan bahwa file tujuan belum lengkap. Nyatakan selesai hanya setelah muxer ditutup dan penulisan tujuan selesai.
7. Download berjalan di foreground. Background/process death membatalkan pekerjaan lokal; tidak ada resume otomatis. Back ketika pekerjaan aktif menawarkan tetap di layar atau membatalkan, lalu mengikuti navigasi biasa.

Hasil: rekaman historis tersimpan sebagai file yang dapat dibuka. Lulus ketika file cocok dengan segmen terpilih, bisa dibaca kembali dengan `MediaExtractor`, timestamp/durasi konsisten, dan gagal/batal tidak menghasilkan label sukses. Konsekuensi jalur cache adalah kebutuhan ruang untuk file sementara; tambah jalur streaming langsung hanya bila ukuran nyata membuatnya diperlukan dan provider yang didukung terbukti cocok.

Rujukan platform: [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer) dan [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).

**Tahap 5 — format SD dengan kontrak ACK vendor**

1. Aktifkan untuk profil IoT yang memiliki action/pembacaan status terbukti. Aktivasi memakai ACK berkorelasi1000 sebagai penerimaan, bukan enum terminal. Tombol dinonaktifkan selama playback/download, penyimpanan setting, proses format lain, atau saat kartu tidak eligible.
2. Tampilkan konfirmasi **Format SD card** dengan nama kamera, konsekuensi penghapusan, tombol **Batal**, dan **Hapus rekaman dan format**. Tidak menggunakan label reset kamera atau menawarkan undo.
3. Hentikan pekerjaan media/PTZ, lalu simpan penanda nonrahasia berisi identitas profil dan operasi format pending sebelum mengirim sekali. Gunakan pola `AtomicFile` dari `ProfileStore`, dengan file pending terpisah dari provisioning; jangan menimpa progres setup AP.
4. Gunakan action IoT `131584`; jalur Legacy `341/441` tidak diimplementasikan pada client ini. Mintalah respons; jangan membawa `no_rsp:true` dari PTZ. Penolakan definitif berbeda dari koneksi terputus setelah pengiriman.
5. Pantau status dengan polling2detik hingga120detik. Tampilkan mengirim → diterima → memformat → selesai/ditolak/status belum diketahui. `SD_STATUS=4` berarti memformat; `4→1` setelah ACK menandai COMPLETE. Timeout tidak menyatakan gagal dan tidak mengirim ulang.
6. Selesai fisik tidak diklaim. COMPLETE hanya berarti transisi4→1 teramati setelah ACK operasi ini dan tidak terikat kriptografis terhadap operasi. Nilai mentah `131330`, ACK, status normal, atau daftar kosong tidak dipakai sebagai bukti terminal. Muat ulang status/kapasitas dan invalidasi daftar rekaman setelah hasil.
7. Setelah background/restart, baca penanda pending dan lakukan pemeriksaan status saja. Penanda tidak pernah dipakai sebagai antrean write. Tombol meninggalkan layar menghentikan pemantauan lokal, bukan menjanjikan pembatalan format di kamera.

Hasil: format dapat dijalankan dengan status yang jujur dan tidak diulang akibat timeout/restart. Lulus ketika pengiriman tepat sekali terjaga pada respons hilang, double tap, cancellation, dan process recreation. Pada pengujian fisik nanti gunakan kartu dengan data yang memang boleh dihapus.

**Tahap 6 — validasi dan syarat rilis**

| Pemeriksaan | Bukti minimum |
|---|---|
| Parser/serializer offline | Vector sintetis per kontrak yang dipakai: payload valid, read parsial, panjang/rentang invalid, response ID/method salah, kode ditolak, timestamp dan enum |
| Lifecycle/state | Satu media aktif, stale callback diabaikan, seek/Back/background menutup resource; format tidak terkirim ulang pada pemulihan |
| Ekspor | Sample sintetis terkontrol; file hasil dibaca kembali, jumlah sample/durasi sesuai, serta disk penuh/cancel/provider gagal ditangani |
| Regresi | Test parser/cipher/transport/provisioning yang relevan tetap lulus; PTZ dan pemilihan kualitas live tidak rusak |
| Visual Android | Satu putaran batched pada phone light/dark, font scale1.3, portrait/landscape, loading/empty/error, progress dan dialog format; perbaiki bersama lalu paling banyak satu putaran konfirmasi |
| Kamera fisik, saat pengujian koneksi dibuka | Profil/pver dan firmware tercatat; live stabil sesuai acceptance yang ada; daftar/playback dibandingkan dengan rekaman nyata; ekspor dapat diputar; setting bertahan setelah readback/reconnect; format dinilai dari status akhir |
| Offline LAN | Internet diblokir saat acceptance untuk masing-masing fitur; sukses live tidak dijadikan bukti playback/download offline |

Gunakan JUnit dan instrumentation yang sudah dipasang; tambah pemeriksaan yang membuktikan kontrak/risiko, bukan test per getter atau snapshot yang hanya meniru implementasi. Penyusunan rencana ini tidak menjalankan build, emulator, koneksi, atau format. Instruksi pengujian koneksi yang masih [deferred](../validation/connection-tests-deferred/status.json) tetap berlaku; baris acceptance koneksi di atas adalah pekerjaan mendatang ketika batas tersebut dibuka.

Rilis tetap eksperimental per fitur/profil sampai bukti fisik tersedia. Setiap fitur boleh lulus secara mandiri; fitur unknown/unsupported/cloud-required tetap dijelaskan tanpa mengklaim kompatibilitas seluruh kamera V380.

**Peta perubahan kode saat implementasi**

| Area | Lokasi dan keputusan |
|---|---|
| Model | Perluas `session/CameraModels.kt` dengan model kecil yang benar-benar dipakai; tidak membuat registry kemampuan atau hierarki plugin baru |
| Codec wire | Gunakan `IoTCommonProtocol.kt`, `IoTControlProtocol.kt`, dan `LegacyLanProtocol.kt`; tambahkan `protocol/CameraSettingsProtocol.kt` dan `protocol/RecordingProtocol.kt` untuk payload/parser baru |
| Sesi | Perluas `session/CameraSession.kt` untuk operasi baru, generation dan cleanup yang sama; tidak membuat session owner kedua |
| Koordinasi UI | Perluas `ui/CameraViewModel.kt` dan routing lokal `ui/MainActivity.kt`; hindari ViewModel/service/repository berlapis tanpa kebutuhan nyata |
| Layar | Perluas `ui/LivePlayerScreen.kt`; tambah `ui/RecordingsScreen.kt` dan `ui/CameraSettingsScreen.kt`. Ekstrak Surface composable hanya ketika live dan playback benar-benar menjadi dua pemakai |
| File media | Tambah `media/RecordingExporter.kt`; gunakan `AnnexB.kt`, codec metadata, dan API Android yang ada |
| Recovery format | Perluas `ui/ProfileStore.kt` dengan pending marker tersendiri, tanpa menyimpan credential atau sesi |
| Diagnostik | Perluas enum/allowlist dalam `session/Diagnostics.kt`; audit pesan error untuk membedakan jaringan, izin kamera dan penyimpanan file |
| Dokumentasi | Arsipkan spesifikasi dan bukti; perbarui PRODUCT/DESIGN/IMPLEMENTATION sesuai perilaku akhir, bukan sebelum fitur tersedia |

Tidak ada dependensi baru yang diperlukan oleh rencana dasar. Tidak ada penyalinan library proprietary, speculative abstraction, atau refactor seluruh aplikasi. Estimasi waktu implementasi ditetapkan setelah Tahap0 menunjukkan seberapa banyak kontrak native dan routing yang masih harus dipulihkan.
