# Tahap 2 — alarm lanjutan, debug 0.4.1

14 Oktober 2026. Tambah/hapus interval alarm dan formulir terpisah sudah diimplementasikan untuk IoT LAN view pertama. Sensitivitas gerakan tetap terkunci. Pengujian kamera/koneksi ditunda; runtime UI/storage belum berjalan karena kedua percobaan emulator gagal saat boot. [Laporan pemeriksaan](../validation/alarm-stage2/results.json).

## Bukti statis dan batas interpretasi

APK V380 6.4.90, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`; REA6.4.0 dengan JADX0.7.1. APK vendor tidak dieksekusi atau dimodifikasi. Excerpt `alarm-stage2-*.sanitized.json` mempertahankan Evidence ID, subject, parameter dan limitations. Canonical Evidence lokal berada pada direktori reference yang diabaikan Git; [indeks](evidence-index.md#alarm-lanjutan041-14-oktober2026) mencatat hash excerpt dan bukti DEX pelengkap.

- `convertTimeBarInfoListToDayBean` membuat `sche`, mengisi `enable=true`, waktu, `mode`, lalu `cnt=arrayList.size()`. Tidak ada minimum satu interval pada writer. `saveTimePlan` membentuk tujuh hari lalu mengirim melalui jalur pengaturan yang sudah dipulihkan sebelumnya.
- `TimeBarInfo.tryCoverTimeBarInfo` menerima hasil edit paling banyak **lima** interval. Ini batas editor vendor yang diamati, bukan bukti kapasitas seluruh firmware.
- `AlarmTimeGetJsonParse.DayBean` memiliki `cnt,max,sche`; trace `getMax` tidak menemukan caller. **Makna persis `max` belum terbukti.** Client mempertahankannya dan memakai nilai tersebut hanya sebagai tambahan batas konservatif: `min(5,max)`. Bila tidak ada, ekspansi dibatasi jumlah awal hari itu. Nilai maksimum yang besar tidak membuka kapasitas baru. Konfigurasi lama di atas batas editor tetap dapat dipertahankan/dikurangi tanpa menambah interval.
- `createAlarmConfigPidList` membuktikan default interval sepanjang hari `0:0:0`–`23:59:59`, `enable=true`, dengan mode motion ketika detector motion aktif. DEX `ABL_ALERT_DETECT_MOTION=BMPC(1)` dan `BMPC(n)=1<<(n-1)` membuktikan mode **1**. Interval baru client khusus gerakan; penambahan memerlukan capability motion dan switch motion aktif. Interval lama dengan jenis peristiwa lain dipertahankan.
- `isAllEmpty` menjumlahkan ukuran daftar mingguan. Caller `showCommonBottomDialog.onClickCancel` mematikan alarm utama sebelum menyimpan minggu kosong. Client tidak mematikan alarm diam-diam: penyimpanan minggu kosong mensyaratkan draft/nilai Alarm utama mati.
- Scan seluruh instruksi DEX untuk field `DPID_ALARM_MT_SENSITIVITY` dan literal655619 hanya menemukan assignment initializer; `AlarmConfigInfo` tidak mempunyai anggota sensitivitas. `HSMediaPlayer.setSensitivityParam` meneruskan ke JNI live dan bukan bukti writer property655619. Getter/writer, rentang, label dan capability property LAN belum terbukti; angka sensitivitas lampu tidak digunakan. Scan ini tidak membuktikan bahwa semua firmware tidak mendukung sensitivitas.

## Perilaku implementasi

`CameraSchedule` menyimpan metadata maksimum per hari; setiap interval terbaca membawa indeks asal. Serializer mengambil field tambahan dan mode dari interval asal pada baseline, sehingga penghapusan interval tengah tidak memindahkan metadata ke tetangganya. Referensi duplikat/di luar rentang, perubahan mode lama, dan perubahan metadata baseline ditolak. Interval baru hanya mempunyai field kontrak yang terbukti; metadata vendor tidak disalin dari interval lain atau dari source draft.

Preflight membandingkan nilai, metadata dan batas jadwal dengan snapshot sebelumnya. Perbandingan metadata bersifat konservatif, termasuk representasi JSON; perubahan representasi dapat meminta muat ulang meskipun nilai semantik sama. Readback membandingkan waktu, enable dan mode secara semantik tanpa indeks lokal, termasuk bila kamera mengurutkan ulang interval. Hasil parsial mempertahankan draft yang belum cocok tanpa replay. Draft dengan baseline yang sudah bergeser harus dibatalkan/dimuat ulang sebelum dikirim kembali.

Form alarm memakai pilihan hari native, TimeInput, tambah/hapus dalam draft, **Simpan**, dan **Batalkan perubahan alarm**. Kapasitas, waktu invalid, overlap interval aktif, minggu kosong dan kebutuhan motion divalidasi sebelum Simpan aktif. Waktu lintas tengah malam tidak ditawarkan. Penghapusan tidak langsung mengirim perintah kamera. Tidak ada API eksternal, dependensi atau migrasi penyimpanan baru; autentikasi, single-flight, maintenance guard dan readback tetap digunakan.

## Validasi dan pekerjaan tersisa

Build debug dan instrumentation berhasil; **60 tes JVM offline lulus**, lint **0 error,13 warning,1 hint**. Tes baru mencakup penghapusan tengah, metadata/bit peristiwa, default dan batas interval, hari/minggu kosong, metadata stale, waktu invalid serta gerbang sensitivitas. Fixture Compose mencakup tambah/hapus, waktu, kapasitas, simpan/batal dan kontrol disabled tanpa sesi kamera, tetapi baru dikompilasi.

Percobaan API35 dengan KVM/SwiftShader dan satu alternatif API26 dengan KVM/GPU off sama-sama exit139 (SIGSEGV) saat boot. Tidak ada instrumentation runtime atau screenshot baru. Pemeriksaan visual terang/gelap, landscape dan font1,3× belum dapat dijalankan. Command runtime yang disiapkan hanya memilih `CameraManagementUiTest` dan `FeatureStorageAndExportTest`; suite socket/session tidak dijalankan.

Sensitivitas gerakan belum diimplementasikan karena kontraknya belum terbukti. Editor area, start/stop patroli, status akhir kalibrasi/format, bukti reboot, Legacy/multi-view, perluasan slot lampu dan acceptance firmware tetap di luar tahap ini. Rencana manajemen kamera keseluruhan masih sebagian.
