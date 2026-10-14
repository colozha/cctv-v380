# Rekaman dan pengaturan IoT LAN — 0.3.0

14 Oktober 2026. Implementasi independen, eksperimental pada debug; belum diuji dengan kamera. Rencana acuan: [recordings-settings-implementation-plan.md](recordings-settings-implementation-plan.md). Tidak ada library atau kode native vendor yang dipaketkan.

## Asal bukti

APK V380 6.4.90, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. REA 6.4.0 dengan JADX headless 0.7.1 dipakai read-only untuk method Java. [Indeks REA](evidence/features-rea-index.json) menyimpan evidence ID dan parameter pemanggilan; 18 excerpt tersanitasi berada bersama indeks. DEX dipakai untuk memeriksa cabang yang gagal didekompilasi. [Excerpt ELF](evidence/features-native-contracts.sanitized.json) berasal dari disassembly statis `lib/arm64-v8a/libhsMediaLibrary.so`, hash `1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc`, melalui Capstone5 dan symbol/PLT readelf. Anotasi alamat string bukan bukti data-flow lengkap; kesimpulan dibatasi pada cabang yang ditelusuri. Tidak ada eksekusi vendor, pembacaan credential aplikasi vendor, atau operasi kamera.

## Property dan action

GET `197376/197377`, SET `197378/197379`, action `197384/197385`, memakai common IoT header/JSON, SID dan key sesi lokal yang sudah terautentikasi. GET `params.dplist` berisi paling banyak 10 integer. SET berisi objek `{dpid,value}`. Respons `data.dplist_result` menyertakan `dpid,code,value` dan opsional `vs`; keberhasilan setiap properti memerlukan `code=1000`. Properti hilang tetap unknown. Tidak membuat nilai default, enum atau kemampuan dari timeout.

Java membentuk DPID `((group&255)<<16)|256|(index&255)` dan DAID dengan `512` menggantikan `256`. Dialek PID native live yang berbeda tidak dicampur dengan API properti Java.

| Properti | ID | Kontrak yang dipakai |
|---|---:|---|
| SD status | 131328 | 1 normal, 2 gangguan, 3 penuh, 4 sedang format, 5 tidak ada kartu, 6 melepas, 7 memasang, 8 perlu format |
| SD capacity | 131329 | String `total\|remaining\|third`; unit belum pasti, UI hanya persentase tersisa |
| SD format state | 131330 | Integer mentah; **enum terminal belum terbukti** |
| Recording mode | 131331 | Bit continuous1, event2; bit lainnya dipertahankan |
| Recording quality | 131332 | 1 SD, 2 HD, 3 FHD; pilihan dibatasi `misc[8]` ability bits1/2/4 |
| Recording audio/loop | 131333/131334 | Boolean atau integer0/1 saat membaca; boolean saat menulis |
| White light | 327936 | 0 otomatis, 1 hidup, 2 mati |
| Light sensitivity | 327939 | Nilai1..3 ditemukan; label makna belum pasti, tidak ditampilkan sebagai kontrol |
| Indicator | 327941 | Boolean |
| Flip mode | 524544 | Dibaca tanpa mengarang enum; action524800 dengan `in:{action:1}` membalik orientasi, lalu memerlukan readback berubah |

Write pengaturan dinyatakan sukses setelah ACK per properti dan pembacaan ulang sama dengan nilai yang diminta. Bila hasil ambigu, aplikasi membaca ulang tanpa mengirim write lagi. Flip tidak mengubah Surface lokal. Readback orientasi yang sama dilaporkan sebagai hasil yang belum berhasil, bukan dicoba ulang otomatis.

Format DAID `131584` menghilangkan `in` dan meminta respons; tidak memakai `no_rsp:true` milik PTZ. DEX parser Java secara eksplisit melompati SD_FORMAT_STATE ([bukti](evidence/features-property-parser.dex.txt)). SD normal/ACK/daftar kosong tidak cukup untuk membuktikan format selesai. Karena rencana mensyaratkan kontrak terminal sebelum aktivasi, `FORMAT_STATUS_VERIFIED=false` mengunci UI dan entry point ViewModel. Penulis send-once, konfirmasi, pending AtomicFile dan pemeriksaan status disiapkan; operasi format **belum tersedia untuk pengguna**. Mengaktifkannya memerlukan bukti terminal dan penerapan evaluator status, bukan sekadar membalik konstanta. Pending tidak pernah dipakai sebagai antrean retry; penutupan status oleh pengguna bukan klaim format sukses.

## Daftar rekaman

LIST `197152/197153`. `params:{filter:15,mode:2,start:epochMs,end:epochMs}` untuk semua, filter2 untuk peristiwa. ReplaySDCardFragment.GetRecFileThread dan constructor RecordFileParam mengikat mode/filter ini; rentang UI satu hari dalam zona waktu Android, dengan pengingat agar sesuai kamera.

`data.slice` berurutan mulai1. `flag=0` selesai, nonzero masih ada halaman. `list` dan `event` memuat `s` offset ms dari awal query, `d` durasi ms (maksimal satu jam), `t` low4 bits jenis1/2/8. `r` opsional menyimpan offset source/pre-roll. Identitas segmen tetap start/end/jenis; source start disimpan terpisah. Mode `brief` tidak diperlakukan sebagai daftar playable kosong. Batas: 10.000 segmen, 512 slice, deadline30 detik, ukuran common frame yang sudah ada.

## Replay dan penyalinan

Native HSReplayDataTFV3Transmitter memakai kontrol `197154/197155`, informasi `197156`. Parameter awal:

```json
{"sts":10000,"pts":12001,"time_id":1,"frames":100,"speed":10,"mode":0,"video":1,"audio":0,"rtype":2}
```

`sts` adalah start segmen; playback `pts=posisi+1`; download `pts=sourceStart+1` dan `mode=2`. Source pre-roll diperlukan untuk keyframe. `rtype` wire1/2/8; Java menerjemahkan jenis1/3/4 lalu native mengembalikannya ke1/2/8. Native mengisi ulang window frame melalui `{frames:100}`; client mengirim sesudah90 frame. Request ID/reply, SID, cipher, time_id dan channel diperiksa.

Info `params.time_id`, `info_id`, `info`; ID1 memberi metadata `info.vattr`/`msvi`, ID3 mencapai callbackReceiveEnd native. EOF TCP tanpa ID3 bukan sukses. Inner media32B memakai parser sparse ECB/CBC yang sama dengan live; byte0 adalah replay time_id, byte30 channel. Client memilih time_id1/view0/video saja. Bukan opcode legacy402 atau live197124.

Playback memakai antrean bounded dengan backpressure dan pacing timestamp kamera. Seek/jeda menutup sesi media lalu permintaan baru, satu media aktif. Ekspor melewati decoder agar tidak memakai kebijakan drop live. Android MediaMuxer menulis AVC/HEVC video-only dengan CSD AnnexB, keyframe awal, PTS asli yang dinormalisasi, serta penolakan perubahan format/PTS mundur. Finish hanya setelah notifikasi receive-end; MediaExtractor memeriksa jumlah sample sebelum file disalin ke SAF. File cache dibersihkan saat selesai/batal/error/startup; penghapusan dokumen tujuan parsial best effort, kegagalan penghapusan dijelaskan. SAF dapat menyisakan dokumen kosong/parsial bila provider menolak penghapusan atau proses mati; tidak pernah dinyatakan tersimpan dari kondisi tersebut. Tidak ada transcoding, audio, batch, resume atau background service.

## Batas implementasi dan validasi

Fitur baru memilih sesi IoT lokal yang sudah ada; Legacy mengembalikan unsupported. Jalur Java pver≥5 dapat memilih MQ, sedangkan native memiliki jalur direct LAN bersyarat SID. Kontrak native tersebut mendasari implementasi eksperimental, bukan bukti kompatibilitas offline firmware pengguna. Tidak ada fallback cloud. Sensitivitas/schedule lampu, variasi dialect, dan enum terminal format tetap gated sampai bukti cukup.

Build, parser, lint dan status runtime aktual: [validation/recordings-settings/results.json](../validation/recordings-settings/results.json). Instruksi menunda pengujian koneksi tetap berlaku; kamera, loopback session, download nyata, pengaturan nyata dan format kartu tidak diuji.

## Perbaikan tab Rekaman — 14 Oktober 2026

Tombol Simpan tidak lagi membutuhkan checkbox video-only. SAF CreateDocument tetap digunakan; grant URI dari picker tidak membutuhkan izin storage menyeluruh. Playback dihentikan sebelum picker, handoff tidak menjalankan cleanup background biasa, dan metadata profil/segmen disimpan dalam SavedStateHandle tanpa credential. Tujuan yang sudah dipilih menunggu autentikasi jika sesi hilang; perubahan profil/tanggal/filter atau pembatalan membuang permintaan. Status selesai memerlukan transfer lengkap, validasi MP4, copy, flush dan penutupan stream tujuan. Error ruang lokal, tujuan, transfer dan MP4 dibedakan.

Player dan daftar menggunakan formatter rentang tanggal/waktu yang sama. Urutan presentasi default descending, dapat diubah lokal menjadi ascending dengan tie-break start/end/jenis. Status/kapasitas SD dibaca lewat GET khusus dua properti, terpisah dari daftar dan draft pengaturan. Ambang ≥90% membandingkan remaining≤total/10 sebelum pembulatan; unit kapasitas tetap tidak diasumsikan. Snapshot kapasitas dibuang ketika sesi/profil/tab berubah.

**Penghapusan rekaman SD belum diaktifkan.** [Inventaris REA](evidence/recordings-delete-inventory.sanitized.json) mencatat RecordFileHelper, ReplaySDCardFragment dan Protocol. Tidak ditemukan kontrak delete TF per segmen pada jalur yang ditelusuri. Nama method yang diobfuscate membatasi kesimpulan ini: tidak mengklaim semua firmware mustahil mendukung penghapusan.

[Kontrol file generik dan xref](evidence/recordings-file-control.sanitized.json) memang menunjukkan enum DELETE dan bentuk `{dfid,ctrl,cnt,list}`. Namun pemanggil DeviceFileSetting.fileListCtrl hanya `DeviceConfigSettingActivity.doDeletePromptSounds` dan `doRenamePromptSound` pada inventaris xref lengkap. Tidak ada bukti pemetaan dfid/list ke identitas `RecordingSegment`, cakupan source/pre-roll, ataupun readback penghapusan TF. Karena itu tidak mengirim opcode tebakan, tidak memakai format kartu sebagai pengganti, dan tidak menambahkan pilihan batch yang tidak bisa dijalankan. UI menampilkan alasan serta tombol hapus nonaktif sesuai keputusan pengguna.

Validasi perbaikan ini dicatat terpisah di [hasil terbaru](../validation/recordings-fixes/results.json). Hasil historis di atas tidak dianggap pengujian source terbaru.

## Desain dan diagnosis replay 0.4.3

Penyebab kegagalan 297KB belum diketahui dari screenshot. Pesan lama membungkus semua exception penerimaan sebagai IOException generik, sehingga `CameraSession.reason` menyatakan NetworkUnavailable bahkan untuk timeout/penolakan/paket rusak. RecordingExportException sekarang mempertahankan reason, detail, pesan terarah dan cause; diagnostik diambil sebelum cleanup dan cleanup tidak menimpa exception transfer pertama. Timestamp di luar batas replay dilaporkan tersendiri. Batas source/pre-roll +60detik yang ada belum dilonggarkan tanpa bukti baru.

Receive loop playback/download bersama dipindah ke RecordingReplay agar dapat menjalankan transcript socket terenkripsi dalam memori. Wire tetap memakai mode0/2, pts/sourceStart+1, window100/refill setiap90, korelasi ACK, SID/cipher/time_id, parser media dan notifikasi info_id3. Fixture melintasi245 frame, metadata, ACK refill, pre-roll, EOF tanpa notifikasi akhir, timeout, penolakan1109, ID/time_id/SID salah dan timestamp invalid. Fixture bukan capture firmware pengguna.

Schema4 menambah detail fase replay/MP4/tujuan serta fields opsional framesReceived, downloadBytes dan timeId. Event hanya pada fase, window dan kegagalan; buffer tetap80 event. Tidak ada payload, frame bytes, string exception bebas, SID, session key atau credential yang disimpan. Byte counter adalah payload yang berhasil diserahkan ke consumer, bukan ukuran file MP4.

Simpan dari daftar tidak membuat Surface atau memulai playback. Metadata picker mencatat apakah sebelumnya mode player. Kehilangan Surface selama Choosing/Awaiting/Downloading/Saving tidak mengantrekan stopMedia baru. File tujuan wajib dapat dibaca ulang dan dibandingkan dengan MP4 cache sebelum Complete. Kegagalan readback dibedakan dari write; pembatalan/error tetap membersihkan file parsial best effort.

Tanggal/filter dibaca otomatis oleh satu pemicu UI; pemilihan nilai yang sama tidak mengosongkan daftar. Commit daftar mensyaratkan generation fitur, profil, tanggal dan filter yang masih sama. Pemilihan urutan hanya mengubah presentasi. Hapus SD tetap gated seperti bagian sebelumnya. [Hasil 0.4.3](../validation/recordings-redesign/results.json).
