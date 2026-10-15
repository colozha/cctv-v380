# Hapus rekaman SD dan pemulihan format — 15 Oktober 2026

## Hasil dan batas aktivasi

| Fitur | Hasil investigasi | Perilaku aplikasi |
|---|---|---|
| Hapus satu rekaman SD | Perintah, identitas file, cakupan source/pre-roll dan readback TF belum terbukti | Menu tetap nonaktif; label menjelaskan keterbatasan aplikasi tanpa menyatakan firmware pengguna tidak mendukungnya |
| Format SD | Action131584 terbukti; enum terminal131330 dan korelasi terhadap operasi baru belum terbukti | Writer/UI tetap dikunci oleh `FORMAT_STATUS_VERIFIED=false` |
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

Pembukaan fitur memerlukan bukti delete TF dengan batas source yang aman, serta enum terminal format dan korelasi terhadap operasi baru. Setelah bukti tersedia, tambahkan writer/marker delete, verifikasi daftar lengkap tanpa filter pada rentang asli, evaluator format, dan tes write/cancellation/process recreation. Keberhasilan sintetis bukan acceptance firmware pengguna.
