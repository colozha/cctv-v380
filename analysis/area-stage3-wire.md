# Tahap 3 — editor area, debug 0.4.2

14 Oktober 2026. Editor grid area dengan referensi PixelCopy sudah terintegrasi pada IoT LAN debug. Pengujian offline lulus; runtime Android, visual, dan acceptance kamera belum terverifikasi. [Hasil/command/hash](../validation/area-stage3/results.json).

## Bukti dan dukungan

APK V3806.4.90, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Excerpt REA6.4.0/JADX0.7.1 pada `area-stage3-*.sanitized.json` mempertahankan source Evidence ID, identitas APK dan limitations. Canonical lokal berada pada `analysis/references/area-stage3-canonical/`, diabaikan Git. APK vendor tidak dijalankan atau dimodifikasi; library vendor tidak dipaketkan. [Indeks bukti](evidence-index.md#editor-area042-14-oktober2026).

- DEX parser `DeviceConfigSetting.a(DeviceConfigInfo,List)` cabang192e–198b menyalin daftar `AlarmAreaJsonParse` langsung ke `AlarmConfigInfo` dan `ServerAlarmAreaList`, serta mengambil row/column dari properti yang sama. JADX gagal mengisolasi overload karena fallback menggabungkan overload; dump DEX dengan descriptor tepat digunakan sebagai pengganti, bukan hasil Java yang diklaim lengkap.
- `showAfterGetAlarmConfigUI` menyalin `ServerAlarmAreaList` ke draft. `initAlarmAreaView` memakai jumlah kolom kamera dan daftar tersebut dalam GridView; adapter memakai indeks daftar dan tinggi grid dibagi jumlah baris. Touch vendor mengubah posisi yang disentuh menjadi0/1. Writer `createAlarmConfigPidList1` menyusun objek area tanpa mengurutkan ulang sel. Pemetaan flat adalah baris demi baris dari kiri atas, terlepas dari locale client.
- `getCamType` membaca enum `MVS_DMISC_ABLC_LENS`; DEX membuktikan ordinal2. Caller area memperlakukan nilai nonzero sebagai jalur khusus dan memakai renderer berbeda untuk fisheye/gambar gabungan. Client memerlukan **misc[2]=0 yang eksplisit dan tepat satu objek msc**. Field hilang tetap unknown; default satu channel vendor tidak dipakai untuk membuka editor. Video metadata juga harus konsisten dengan satu tampilan/view0.
- `onClicked` vendor mendukung pilih semua dan kosongkan pilihan dengan array0/1, lalu menyimpan draft melalui `saveAreaSelected`. Client memakai label pilihan sel; tidak menyimpulkan status fisik detector dari mask saja.

Batas client tetap1–16 baris/kolom, maksimal256 sel, ukuran tidak dapat diubah. Grid di luar budget, rectangle V3, fisheye, multi-view/gambar gabungan, metadata ambigu, serta decoded crop/rotation yang tidak flat tidak membuka writer. Batas ini bukan kapasitas universal firmware.

## Alur dan kepemilikan referensi

**Ambil gambar referensi** menampilkan preview sementara pada tab Pengaturan, melalui pemilik sesi dan MediaCodec yang sama. Capture mengunci operasi, membaca ulang area/orientasi, dan mensyaratkan konfigurasi patroli tidak aktif. Tidak ada preview reconnect otomatis. Input PTZ client diblokir selama capture. Hilangnya Surface Live lama tidak membatalkan pekerjaan perpindahan ke Pengaturan; cleanup hanya membatalkan playback ketika tab Live masih aktif.

Decoder melaporkan output width/height/crop/rotation dan waktu frame benar-benar dirender. Referensi hanya menerima frame penuh, tanpa crop/rotasi, dengan dimensi cocok metadata video. PixelCopy menunggu Surface dan buffer berasio cocok serta frame baru sesudah layout siap. Bitmap ARGB dibatasi sisi terpanjang1024, maksimum sekitar4MiB; yang disalin hanya Surface video, bukan screenshot seluruh jendela. H.264 berpadding/crop yang tidak dapat dipetakan tetap ditolak pada tahap ini.

Media dihentikan sebelum referensi diterbitkan. Session membaca ulang area/orientasi dan memeriksa kembali pemilik, profil/device, revisi autentikasi, generation, cancellation, dan maintenance. Orientation property diperlakukan sebagai token opaque yang harus tetap sama, bukan enum flip yang ditebak. Tidak ada rotasi/mirror lokal. Reference merupakan objek yang diterbitkan session; objek buatan caller dengan field sama tidak dapat mengizinkan write.

Editor menampilkan gambar tetap dengan overlay grid, penanda sel terpilih, dan pilihan baris/kolom plus toggle48dp sebagai alternatif ketukan presisi pada gambar. **Gunakan area** mengubah draft alarm; **Simpan alarm** memakai preflight/SET/readback yang tersedia. **Batal** mempertahankan draft sebelumnya dan melepas referensi; draft area yang sudah ada memerlukan capture/use baru sebelum disimpan. Keluar dengan editor aktif meminta konfirmasi seperti draft lainnya.

Writer mempertahankan row/column dan field tambahan baseline; hanya mask berubah. Sebelum write, session memeriksa ulang reference yang masih dimilikinya, identity/revision/generation, baseline dan orientation terkini, lalu mengecek lagi sebelum socket write. Perbandingan metadata baseline sengaja exact; perubahan representasi JSON dapat meminta capture ulang meskipun mask semantik sama. Readback membandingkan dimensi dan sel. Hasil ambigu hanya direkonsiliasi melalui GET, tanpa replay. Referensi dikonsumsi setelah percobaan write; draft yang belum cocok harus diperiksa dengan referensi baru.

Stream baru, arah motor client, orientasi, close/abort/auth error, background/pergantian kamera, dan perubahan baseline membuat reference tidak berlaku. Referensi dan gambar hanya berada di memori, tidak masuk ProfileStore, SavedState, diagnostik, file atau ekspor. Setelah digunakan/dibatalkan, bitmap dilepas dari state; bitmap yang sudah dipublikasikan tidak direcycle saat render mungkin masih memakainya. Bitmap capture yang gagal/batal sebelum publikasi dibersihkan oleh pemilik/callback PixelCopy.

**Batas observasi:** firmware belum menyediakan telemetry posisi motor yang terbukti. Gerakan dari client menginvalidasi referensi, tetapi gerakan fisik atau perintah dari aplikasi lain tidak diklaim dapat terdeteksi otomatis. Bila arah kamera berubah, pengguna perlu mengambil referensi ulang. Status patrol disabled bukan bukti gerak fisik berhenti.

## Validasi dan pekerjaan tersisa

Build debug/instrumentation berhasil, **65 tes JVM lulus**, lint **0 error,14 warning,1 hint**. Warning tambahan menyarankan helper KTX untuk alokasi bitmap; native Bitmap API sengaja dipertahankan tanpa dependensi baru. Vector baru mencakup grid nonpersegi, batas/koordinat invalid, mask/dimensi, metadata, lens/channel unknown, reference stale/palsu dan crop/rotation/budget gambar. Regresi offline alarm/lampu/PTZ/recording/AP tetap dipilih tanpa suite socket.

Tiga fixture Android baru dikompilasi: editor ketukan/alternatif aksesibel/use/cancel, loading/error/disabled serta Save tanpa referensi, dan PixelCopy pada Surface berwarna sintetis. Fixture menyediakan capture PNG sintetis bernama tema/orientasi/font untuk QA bila runtime tersedia; **belum ada PNG baru atau tes native yang dijalankan**.

Emulator tetap37.2.12.0, lingkungan yang sama dengan percobaan API35/API26 KVM yang SIGSEGV pada tahap2. Tidak mengulang boot yang sama. Runtime UI/storage/PixelCopy, tema terang/gelap, portrait/landscape, font1,3× dan TalkBack masih belum diverifikasi. Command instrumentation yang disiapkan hanya memilih kelas UI/storage lokal. Pengujian koneksi, sesi berbasis socket, dan kamera fisik tetap ditunda.

Sensitivitas gerakan, start/stop/status patroli, status akhir kalibrasi/format SD, bukti reboot, Legacy/multi-view, serta perluasan slot lampu tetap belum selesai/di luar tahap ini. Rencana manajemen keseluruhan tetap sebagian dan adapter tetap debug-only.
