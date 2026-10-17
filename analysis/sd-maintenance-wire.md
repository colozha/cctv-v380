# Hapus rekaman SD dan pemulihan format — 15 Oktober 2026

## Aktivasi paritas vendor — 17 Oktober 2026

Fitur format SD kini aktif dengan **kontrak penerimaan vendor**, bukan dengan klaim penyelesaian terminal. REA V380 6.4.90 membuktikan action `DAID 131584` dikirim lewat method `197384/197385` dengan params persis `{"daid":131584}`; respons berkorelasi `result.code == 1000` adalah jalur sukses vendor (SDK `256` → `setSDCardFormat` `1001`). Properti `131330` tetap dilewati parser vendor dan enum terminalnya **belum terbukti**.

Keputusan produk yang disetujui pemilik: ACK berkorelasi `1000` berarti perintah diterima. Aplikasi lalu memantau `SD_STATUS` setiap 2 detik hingga 120 detik. `SD_STATUS=4` berarti kamera melaporkan sedang format; bila setelah pengamatan itu status kembali `1` (siap), aplikasi menampilkan selesai dan menghapus marker. Bila transisi 4→1 tidak teramati, hasil tetap `UNKNOWN`, marker dipertahankan, dan tidak ada pengiriman ulang. Proses mati selalu dipulihkan sebagai `UNKNOWN`. Aplikasi tidak pernah menafsirkan nilai mentah `131330` sebagai sukses/gagal dan tidak mengklaim format fisik selesai.

Perubahan implementasi: `FORMAT_STATUS_VERIFIED` diganti `FORMAT_ACTION_VERIFIED=true` dan `FORMAT_TERMINAL_VERIFIED=false`; gate `BuildConfig.DEBUG` pada writer dihapus (koneksi build release tetap ditolak oleh gate aplikasi); prasyarat `131330 != null` dihapus, sedangkan `SD_STATUS ∈ {1,2,3,8}` dipertahankan; `sendSdFormat` mengembalikan `SdFormatAck.ACCEPTED` hanya setelah ACK berkorelasi; UI menampilkan fase `ACCEPTED`, `FORMATTING`, `COMPLETE`, `REJECTED`, dan `UNKNOWN`. Marker tetap ditulis sebelum satu-satunya write dan tidak pernah menjadi antrean retry.

Validasi pada perubahan ini bersifat sintetis/offline: [hasil, source revision, build dan batas](../validation/sd-format-activation/results.json). Tidak ada koneksi kamera, format nyata, atau acceptance firmware pengguna.

Checkpoint 16 Oktober di bawah adalah catatan historis. Syarat "bukti terminal penuh" tidak lagi menjadi blocker aktivasi, tetapi enum terminal `131330` tetap pekerjaan riset yang belum selesai dan tidak boleh ditafsirkan.

## Penelusuran penyelesaian format — 16 Oktober 2026 (historis)

**Catatan historis:** keputusan 17 Oktober mengaktifkan format dengan paritas ACK vendor; bagian ini mencatat mengapa bukti terminal belum tersedia dan tetap berlaku untuk pekerjaan riset tersebut. Rencana lanjutan saat itu menetapkan **aktivasi setelah bukti terminal lengkap** dan **validasi offline saja**. Checkpoint bukti belum terpenuhi: `FORMAT_STATUS_VERIFIED` tetap `false`; tidak ada perubahan perilaku aplikasi, writer aktif, evaluator terminal, atau migrasi marker pada tindak lanjut ini. Ini bukan penyelesaian fitur format. Hasil dan batas investigasi 15 Oktober di bawah tetap berlaku.

### Mengapa V380 asli menyediakan format

Penelusuran baru mengikuti pemanggil sampai handler hasil, melampaui pencarian nama/literal sebelumnya. APK dan JAR JADX sama dengan investigasi sebelumnya; [manifest](evidence/sd-format-completion-summary.json) menyimpan hash, Evidence ID REA, descriptor/rentang DEX, serta perintah reproduksi. [Kolektor](evidence/sd-format-completion-collect.py) tidak menjalankan APK/library vendor dan menyamarkan seluruh literal string Java. Evidence kanonis disimpan lokal pada direktori references yang diabaikan Git.

| Jalur yang diperiksa | Hasil yang terlihat | Batas kesimpulan |
|---|---|---|
| `DeviceSDCardSetting.setSDCardFormat`, cabang platform baru ([bukti sebelumnya](evidence/features-DeviceSDCardSetting-setSDCardFormat-0.sanitized.json)) | Mengirim `DAID_SD_FORMAT`; hasil action SDK `256` dipetakan menjadi return `1001` | `1001` merupakan kode hasil method SDK, bukan nilai property `131330` |
| `DeviceConfigSettingDeviceRecordFragment2.FormatSDCardThread.run` dan runnable hasilnya ([REA](evidence/sd-format-completion-thread2.sanitized.json), [DEX thread](evidence/sd-format-completion-thread2.dex.txt), [DEX hasil](evidence/sd-format-completion-thread2-result.dex.txt)) | Return `1001` langsung menandai daftar replay perlu refresh, menampilkan resource sukses, lalu menutup Activity; return `2001` menampilkan gagal | Cabang ini tidak membaca status terminal sesudah action. ID thread hanya menolak callback UI lama |
| `DeviceConfigSettingDeviceRecordFragment.handleMessage` ([REA](evidence/sd-format-completion-record-handler.sanitized.json), [DEX](evidence/sd-format-completion-record-handler.dex.txt)) | Return `1001` juga menampilkan sukses dan menutup Activity | Pembacaan konfigurasi rekaman merupakan cabang message lain, bukan bukti penyelesaian operasi format tersebut |
| `DeviceConfigSettingHomePageFragment.handleMessage` ([REA](evidence/sd-format-completion-home-handler.sanitized.json), [DEX](evidence/sd-format-completion-home-handler.dex.txt)) | Return `1001` menampilkan sukses dan mengubah `RecordConfigInfo.nRecordStat` lokal menjadi `1` | Assignment lokal bukan readback kamera. DEX menunjukkan branch sukses menuju return; decompiler Java menghasilkan fall-through gagal yang menyesatkan |

Temuan tersebut menjelaskan perbedaan tombol dengan aplikasi asli: UI vendor yang diperiksa menerima hasil action sebagai dasar pesan sukses. Tidak ada dasar untuk mengubahnya menjadi klaim format fisik selesai pada client ini. `2001` di handler juga tidak membuktikan enum kegagalan terminal `SD_FORMAT_STATE`. Jalur Legacy dan IoT tidak disamakan.

[Parser DEX sebelumnya](evidence/sd-maintenance-format-parser.dex.txt) tetap menunjukkan property `131330` dilewati. Cabang SDK platform baru mengirim action tanpa membaca property tersebut sebagai prasyarat. Jadi kewajiban `SD_FORMAT_STATE != null` pada client adalah guard konservatif lokal, bukan prasyarat vendor yang terbukti. Guard tetap dipertahankan selama fitur terkunci; belum ada kontrak terminal alternatif untuk menggantikannya. Menjadikannya opsional sekarang belum membuka fitur secara benar.

### Checkpoint implementasi dan validasi

Aktivasi, state `SUCCEEDED`/`FAILED`, cleanup otomatis hasil terminal, serta perubahan marker tetap ditunda. Untuk melanjutkan dibutuhkan sumber tepercaya yang mengikat **indikator/nilai sukses dan gagal** pada **operasi format baru**; ACK, status normal, kapasitas berubah, dan daftar kosong sendiri tidak mencukupi. Bila penyelesaian menggunakan property lain atau notifikasi, wajib buktikan framing, identitas sesi, dan aturan freshness/korelasinya sebelum mengubah guard. Ini tidak membuktikan bahwa firmware pengguna tidak mendukung format atau bahwa semua jalur SDK telah diperiksa.

[Validasi revisi ini](../validation/sd-format-completion/results.json): 79 tes JVM terpilih lulus dengan locale Indonesia, termasuk enam tes SD dan satu tes session guard; lint 0 error/35 warning. Build debug/instrumentation dan analisis lint dinyatakan up-to-date oleh Gradle pada source aplikasi yang tidak berubah. Percobaan awal mencatat satu kegagalan fixture tanggal (`October` versus `Oktober`) pada locale Inggris; log/XML awal tetap disimpan terpisah. Tidak ada koneksi kamera, pengiriman format, atau pengujian UI runtime baru. Tidak ada kompatibilitas firmware yang dinyatakan verified.

## Hasil dan batas aktivasi

| Fitur | Hasil investigasi | Perilaku aplikasi |
|---|---|---|
| Hapus satu rekaman SD | Perintah, identitas file, cakupan source/pre-roll dan readback TF belum terbukti | Menu tetap nonaktif; label menjelaskan keterbatasan aplikasi tanpa menyatakan firmware pengguna tidak mendukungnya |
| Format SD | Action131584 dan ACK berkorelasi1000 terbukti (paritas vendor); enum terminal131330 belum terbukti | Aktif 17 Oktober 2026: ACK = diterima, polling4→1 untuk selesai; nilai131330 tidak ditafsirkan |
| Pemeriksaan format tertunda | GET tiga properti SD dan status SD4=sedang format sudah mempunyai dasar kontrak | Read-only dengan identitas sesi dan korelasi respons ketat, tanpa mengirim ulang format |

Rencana diterapkan sampai batas bukti tersebut. API/marker delete dan dialog penghapusan **belum ditambahkan**, karena belum ada identitas target wire yang benar. Evaluator sukses/gagal format, invalidasi setelah sukses dan pengujian write aktif juga ditunda. Membalik konstanta saja tidak menyelesaikan fitur. Pengujian koneksi/kamera fisik tetap ditunda sesuai instruksi pengguna.

## Bukti statis baru

Input APK V3806.4.90 lokal mempunyai SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Kolektor memakai dexlib2 dari JAR JADX headless0.7.1 dan `readelf`, tanpa menjalankan target. Identitas alat, kolektor dan hasil ada pada [manifest](evidence/sd-maintenance-static-summary.json).

- [Scan DEX](evidence/sd-maintenance-refs.dex.txt) memeriksa **5.429.555 instruksi**, memilih referensi format, literal131330/131584, delete/remove dengan nama rekaman/SD/TF, serta keluarga FileListCtrl. Pencarian nama/literal tidak mengecualikan operasi obfuscated/inlined.
- [Parser property](evidence/sd-maintenance-format-parser.dex.txt), descriptor tepat `DeviceConfigSetting.a(DeviceConfigInfo,List)`, melewati pemrosesan nilai `DPID_SD_FORMAT_STATE`. Referensi lain menambahkan property ke permintaan; belum ditemukan evaluator terminal pada jalur Java yang diperiksa.
- [Thread format vendor](evidence/sd-maintenance-format-thread.dex.txt) melakukan login, memanggil `setSDCardFormat`, kemudian meneruskan kode hasil ke Handler. Ini membuktikan pemanggilan, bukan penyelesaian format. Kontrak action tetap berasal dari [bukti sebelumnya](recordings-settings-wire.md).
- Call-site `DeviceFileSetting.fileListCtrl` tetap `doDeletePromptSounds` dan `doRenamePromptSound`. Referensi delete rekaman lain terkait cloud atau file suara lokal. [Inventaris sebelumnya](evidence/recordings-delete-inventory.sanitized.json) dan [kontrol file generik](evidence/recordings-file-control.sanitized.json) belum memetakan target ke `RecordingSegment`.
- [Simbol ELF arm64](evidence/sd-maintenance-native-symbols.json) meliputi libhsMediaLibrary, libhsUtils, libdevInfo dan libcommonV2DevInfo. Simbol terpilih belum memberikan wire delete TF atau enum terminal. Ini bukan dekompilasi/call graph lengkap; simbol stripped tidak membuktikan ketiadaan dukungan firmware.

[Kolektor Java](evidence/sd-maintenance-reference-scan.java), [dump instruksi](evidence/ap-dex-instructions.java), dan [kolektor ELF](evidence/sd-maintenance-native-scan.py) menyimpan struktur instruksi/simbol; string bebas dan array DEX dikecualikan. Perintah reproduksi disimpan dalam manifest. Tidak ada credential, token atau library proprietary yang dimasukkan ke aplikasi.

## Perubahan implementasi

`readSdFormatStatus(profileId,deviceId)` membaca131328/131329/131330. Autentikasi, profil, device ID, owner IoT dan revisi diperiksa sebelum/sesudah read. SID, cipher, request ID, channel dan method respons diperiksa; respons auth tanpa korelasi ditolak. Read tetap boleh saat maintenance terkunci. Readback menghapus nilai SD lama yang hilang tanpa membuang properti pengaturan lain.

Writer format yang masih dikunci menerima profil/device/operation ID dan callback marker sebelum write. Kelayakan kartu dibaca kembali, media dihentikan, owner diperiksa ulang, ID duplikat ditolak dan maintenance dikunci sebelum satu pengiriman. ViewModel membedakan belum dikirim, penolakan action berkorelasi, serta hasil ambigu setelah write mungkin terjadi. Error GET sesudah ACK tidak dianggap penolakan action. Monitoring disiapkan setiap2detik dengan deadline120detik. Status4 hanya membuktikan sedang format; integer131330 tidak dipetakan ke sukses/gagal.

Marker tetap versi2; versi1 dipulihkan sebagai unknown tanpa retry. Persistensi memakai sync yang dapat melempar kegagalan dan readback sebelum writer diizinkan. Cleanup wajib operation ID dan verifikasi marker terhapus. Langkah tersebut diperlukan karena `finishWrite`/penghapusan sendiri dapat mencatat kegagalan tanpa exception pada [implementasi AOSP AtomicFile](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/util/AtomicFile.java). Tidak ada baseline terminal yang dibuat tanpa kontrak, sehingga marker lama tidak dipromosikan menjadi sukses dari status tidak berkorelasi.

**Periksa status kartu** menggunakan GET khusus tersebut. **Tutup catatan format** tersedia walaupun settings belum dimuat, meminta konfirmasi, dan mengikat ID operasi pada dialog. Penutupan tetap menyatakan hasil unknown, bukan sukses/pembatalan kamera. Format ditolak selama pemilihan tujuan/ekspor. Tidak ada dependensi atau perubahan API eksternal.

## Validasi dan pekerjaan tersisa

[Hasil aktual](../validation/sd-maintenance/results.json) mencatat build, lint dan tes JVM terpilih. Fixture baru memakai state autentikasi sintetis dan stream terenkripsi dalam memori, tanpa socket jaringan. Kasus mencakup GET tiga properti saat maintenance terkunci, identitas salah, sesi dicabut, korelasi ID/method/channel/SID salah, auth tanpa korelasi, timeout/penolakan tanpa retry, dan properti hilang. Writer gated diuji tidak memanggil marker/transport pada pengajuan berulang.

Tes instrumentation ditambah untuk ID stale, marker/recreation, kegagalan storage, recovery tanpa settings, konfirmasi/batal dan kontrol disabled pada light/dark/font besar/landscape. **Belum dijalankan dan tidak ada screenshot baru terverifikasi:** emulator API37 berhenti saat boot dengan SIGSEGV, baik KVM maupun `-accel off`. Jalur write aktif, ACK format hilang dan batas polling120detik belum diuji karena kontrak terminal masih mengunci writer. Tidak ada penghapusan/format kamera nyata.

Pembukaan fitur delete TF tetap memerlukan bukti batas source yang aman; writer/marker delete belum ditambahkan. Format SD kini aktif dengan kontrak ACK vendor, bukan enum terminal: hasil fisik tetap tidak diklaim dan `131330` tetap tidak ditafsirkan. Verifikasi daftar lengkap tanpa filter pada rentang asli serta tes write/cancellation/process recreation sintetis tetap menjadi syarat mutu. Keberhasilan sintetis bukan acceptance firmware pengguna.
