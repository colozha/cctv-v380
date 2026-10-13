# Observasi kamera pengguna — 13 Oktober 2026

**Pembaruan14 Oktober2026:** pengguna mengirim diagnostik client independen dan melaporkan kamera berhasil terkoneksi tetapi selalu terputus. Log berisi auth/capability sukses dan callback render pada tiga sesi. Kode app menunjukkan pola CAPABILITIES berasal dari adapter IoT LAN; ini inferensi dari jalur source, bukan pembacaan metadata firmware baru. PTZ OK hanya membuktikan write, bukan motor/stop. Durasi sampai error dan hash file sumber tersimpan di [ringkasan tersanitasi](../validation/user-stream-observation.json). IP, kredensial, SID/key dan footage tidak disalin. [Analisis disconnect0.2.1](stream-disconnect.md) membedakan cacat parser yang terbukti, feedback native yang dipulihkan, dan penyebab runtime yang masih belum diketahui. Tidak ada pengujian koneksi oleh agen pada perbaikan ini. Catatan13 Oktober di bawah adalah riwayat awal.


Sumber: keterangan dan screenshot yang dikirim pengguna dalam percakapan. Ini bukti tampilan aplikasi vendor, bukan capture jaringan atau bukti interoperabilitas client independen. Tidak ada kredensial, token, atau device ID dicatat.

- Aplikasi: V380 non-Pro; kamera ditambahkan sendiri dari awal melalui AP kamera.
- Software version yang ditampilkan: `V2.1.6.0(HSC_A2X17XHR_20250804)`.
- Firmware version yang ditampilkan: `Hw_EV2X_NA1_XHR_V1.0_WF_20250804`.
- Halaman Advanced settings memuat IP, Device voice prompt, Onvif setting, Anti-flicker settings, dan Audio encoding settings (`AAC_ADTS`).
- Toggle Onvif setting tampak nonaktif. Menu username/password tidak tampak pada dua halaman pengaturan yang dikirim; halaman General belum diperiksa.
- Pengguna melaporkan toggle ONVIF gagal terupdate, live video vendor tetap berjalan, dan HP serta kamera berada pada jaringan Wi-Fi yang sama. Penyebab kegagalan toggle belum diketahui; keberhasilan video vendor tidak membuktikan transport video melewati LAN langsung.
- Model produk, IP kamera, credential lokal, jenis protokol vendor, endpoint ONVIF/RTSP, codec video, dan capability PTZ ONVIF belum diverifikasi.

Versi software/firmware tersebut tidak boleh disamakan dengan field `mProtocolVersion` dalam SDK APK. Label HSC tidak cukup untuk menetapkan jalur V2/V3 atau kebutuhan cloud.

Langkah berikutnya: identifikasi IP kamera dari menu IP dan cocokkan jaringan komputer sebelum uji layanan terarah. Aktivasi ONVIF telah gagal, sehingga tidak diasumsikan aktif atau tersedia. Kehadiran toggle adalah petunjuk dukungan yang perlu diuji; bukan bukti endpoint, autentikasi, stream atau PTZ sudah bekerja. Jika layanan standar terbukti, ONVIF/RTSP menjadi kandidat jalur implementasi terpisah yang perlu disepakati terhadap rencana wire vendor awal. Gerbang profil proprietary tetap tertutup.
