# Manajemen kamera IoT LAN — implementasi debug 0.4.2

14 Oktober 2026. Implementasi sebagian dari [rencana](camera-management-implementation-plan.md). Tidak ada pengujian koneksi kamera atau eksekusi APK vendor. Semua penulisan menggunakan autentikasi, transport, enkripsi, dan pemilik sesi yang tersedia; tidak ada fallback cloud/MQ atau library baru.

## Kontrak yang dipulihkan

APK V380 6.4.90, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. REA/JADX dibaca melalui service lokal REA6.4.0, JADX0.7.1. Excerpt `management-*.sanitized.json` mempertahankan source evidence ID dan limitations. Instruksi DEX adalah bukti statis pelengkap, dikumpulkan dengan collector `ApDex` yang sudah diarsipkan dalam `ap-dex-instructions.java`. Tidak ada dekompilasi yang diklaim sebagai source asli atau bukti runtime.

`Protocol.<clinit>` DEX dan formula PID/DAID menghasilkan [ID properti](evidence/management-property-ids.json); [instruksi initializer](evidence/management-protocol-clinit.dex.txt) memungkinkan penelusuran ulang tanpa mengeksekusi kode vendor. Assignment dasar group1 dan index0 berasal dari encoded/default static field. Java property API memakai GET197376/197377, SET197378/197379 dan action197384/197385; berbeda dari dialek PID native live. Respons per properti harus mempunyai code1000.

| Fitur | Kontrak dan implementasi |
|---|---|
| Alarm | Main655616, suara655617, motion655620, jadwal655621, area655622. Boolean mengikuti properti vendor. `createAlarmConfigPidList1` mengirim jadwal mingguan dengan `mon`…`sun`, `cnt`, `sche`, serta `enable,mode,s_time,e_time` per interval. Editor menambah/menghapus/mengubah interval sesuai batas konservatif. Indeks asal mempertahankan bit `mode`, field tambahan, dan jenis detector lain. [Bukti dan batas tahap2](alarm-stage2-wire.md). Mode rekaman tidak diubah. |
| Sensitivitas gerakan | DPID655619 ditemukan dalam initializer, tetapi writer/rentang Java property belum dipulihkan. Tidak dibaca sebagai kontrol yang dapat ditulis dan tidak memakai native live sensitivity sebagai substitusi. |
| Area | Parser mengenali grid row/column dan daftar0/1 yang berukuran bounded. Editor referensi PixelCopy tersedia untuk lens flat eksplisit/satu view, full frame tanpa crop/rotasi. Reference session diperlukan sebelum write; field tambahan/dimensi dipertahankan. Varian ambigu dan rectangle V3 tetap terkunci. [Kontrak/batas tahap3](area-stage3-wire.md). |
| Lampu | Sensitivitas327939 bernilai1 rendah,2 normal,3 tinggi menurut `getLightSensitivityResId`; writer dibuktikan `createDeviceXParamConfigPidList`. Jadwal327940 memiliki `defaultAction`, `capability`, `total`, `time_schedule`; interval `enable` integer0/1, waktu `HH:mm:ss`. Writer vendor menambahkan `:00` ke waktu menit. |
| Jumlah jadwal lampu | `DeviceConfigSetting.p` dan `DeviceConfigSettingWhileLightFragment.onBackPressed` membuktikan total adalah jumlah interval, bukan kapasitas. Client memperbarui total saat tambah/hapus, menjaga capability dan field tambahan. Vendor UI membuat satu interval baru; client dapat membuat satu ketika kosong, dan mempertahankan jumlah slot awal ketika kamera sudah mempunyai lebih banyak. Ekspansi kapasitas di luar itu belum tersedia. |
| Preset | GET393472 berisi `pos_mark_cnt,pos_mark_details`; slot zero-based. Kapasitas berasal dari ctrl[1] view0. Action393729 memakai `vsbmp=1` dan `in:{ctrl_flag:1/2/3,pos_mark_id_cnt:1,pos_mark_id:[slot]}` untuk simpan/panggil/hapus. Timpa/hapus dikonfirmasi. Daftar dibaca ulang; daftar slot terisi tidak membuktikan koordinat posisi tersimpan/tercapai. |
| Rute patroli | GET/SET393473 memakai schema `PtzCruiseJsonParse`. Client mengedit rute, urutan preset, waktu, dan jeda menit hanya ketika auto/timer disabled; batas rute/posisi dari objek kamera. Mode priority, konfigurasi timer dan field tambahan dipertahankan. Aktivasi dan stop patroli belum diekspos karena status motor belum direkonstruksi. |
| Kalibrasi | Action393732, view0 mask1, tanpa `in`, dengan respons. Capability terpisah: ctrl[0] bit16. ACK hanya diterima; status selesai belum tersedia. Marker kalibrasi nonrahasia dipulihkan saat startup/pergantian kamera; aksi motor diblokir sampai pengguna memeriksa gerak berhenti dan menutup pemeriksaan. Pemeriksaan ini bukan verifikasi status oleh firmware; proses mati tidak menjamin motor berhenti. |
| Versi | GET model65796, software65793, module65794, hardware65795. String dibatasi panjang/karakter; tidak ada nilai firmware default dari dokumentasi. |
| Restart | Action66050 tanpa `in`; capability misc[6] bit1. Marker durable mendahului write. Discovery mencocokkan device ID dan autentikasi ulang; alamat profil diperbarui setelah cocok. Tanpa boot/uptime evidence, status hanya kamera tersambung kembali, bukan restart terverifikasi. |
| Format SD | Action131584 terbukti; enum terminal131330 tetap belum terbukti setelah scan DEX tambahan. Writer/UI tetap dikunci. Marker versi1/2 dipulihkan read-only. Pembacaan status khusus memeriksa identitas/revisi sesi dan korelasi; writer yang masih gated memakai marker sebelum write, ID operasi dan cleanup terverifikasi. [Bukti dan batas terbaru](sd-maintenance-wire.md). |

Capability [REA dan DEX](evidence/management-ability.dex.txt): view0 memakai `msc[0].ctrl` bila tersedia; tidak menggabungkan motor lensa lain. ctrl[1]=jumlah preset, ctrl[2]>0 mendukung auto cruise, ctrl[0]&16 mendukung kalibrasi; light[3]>0 mendukung jadwal. Field hilang tetap unknown. Kemampuan patrol tidak dianggap sebagai status motor berjalan. Pada perangkat yang mendeklarasikan cruise, kontrol arah memerlukan pembacaan konfigurasi motor tidak aktif terlebih dahulu.

## Pengiriman dan pemulihan

- Mutasi dipagari sebelum coroutine dijadwalkan. Aksi baru tidak membatalkan write lama lalu membuat write baru. Pembacaan grup tetap dapat dilakukan terpisah dari stream/Surface.
- Snapshot terkait profil, device ID, dan revisi autentikasi. Preflight membaca ulang konfigurasi dan menolak baseline stale. Writer memeriksa ulang kepemilikan sesi sebelum socket write.
- SET membaca ulang nilai. Penolakan parsial/hasil ambigu direkonsiliasi per field dan draft yang belum cocok dipertahankan; tidak mengulang batch atau rollback otomatis.
- Preset/kalibrasi/restart mempunyai operation ID pengiriman; partial write/timeout tidak memicu replay. Format, restart, dan pemeriksaan kalibrasi mempunyai marker AtomicFile terpisah dari AP/provisioning, tanpa credential/session material.
- Marker format versi1 berubah menjadi catatan unknown dengan ID legacy; tidak menjadi antrean format. Cleanup menggunakan operation ID. Penghapusan profil yang masih mempunyai pending operasi ditolak.
- Restart foreground memantau maksimal120 detik; restore proses hanya membaca/verifikasi. **Tutup catatan restart** menghapus catatan lokal dengan konfirmasi, tanpa membatalkan atau mengulang restart kamera.
- Tidak ada klaim exactly-once pada perangkat, motor berhenti saat jaringan hilang, atau status akhir operasi berdasarkan ACK saja.

## Pekerjaan tersisa dan status validasi

Rencana belum selesai penuh. Yang masih memerlukan kontrak/bukti: start/stop dan status motor patroli; status akhir kalibrasi; sensitivitas gerakan; varian pemetaan area non-flat; serta status terminal format SD. Format tetap tidak dapat dipakai. Legacy untuk fitur baru, multi-view property variants, dan ekspansi slot lampu belum diterapkan.

Hasil build/tes serta crash emulator: [validation/area-stage3/results.json](../validation/area-stage3/results.json). Instrumentation dikompilasi, tetapi runtime UI/storage dan kompatibilitas kamera belum diklaim. Penundaan pengujian koneksi masih berlaku. Fitur tetap eksperimental pada debug dan belum memenuhi acceptance firmware.
