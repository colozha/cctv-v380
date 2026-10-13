# Putusnya stream setelah beberapa detik — perbaikan0.2.1

Pengguna melaporkan client berhasil tersambung tetapi selalu terputus. Diagnostik schema1 menunjukkan autentikasi/capability berhasil dan callback render pada tiga sesi. Waktu mulai stream hingga kegagalan:10.782detik,9.659detik,0.366detik (belum merender), dan5.651detik. Frame pertama pada sesi yang merender:500ms,537ms,2916ms. [Ringkasan tersanitasi](../validation/user-stream-observation.json) menyimpan hash sumber tanpa menyalin IP lokal atau payload.

Log lama selalu memakai label `AUTH FAILED` dari fungsi `fail`, bahkan bila error datang dari parser stream atau tulis PTZ. Tidak ada alasan error, fase parser, atau command pada baris kegagalan. Karena itu **penyebab tepat sesi pengguna belum dapat dipastikan dari log tersebut**. Perbaikan di bawah didasarkan pada cacat source yang dapat dibuktikan dan kesesuaiannya dengan instruksi APK.

## Parser sparse cipher

Parser sebelumnya mewajibkan `chunk <= payloadSize` dan `gap <= payloadSize`. Native `iotc_ms_frame_cipher` membiarkan sisa video yang lebih pendek daripada satu chunk tetap plaintext (`0x93a28..0x93a7c`), serta membatasi gap ke sisa frame (`0x93b20..0x93b40`). Ukuran pola dipilih independen dari ukuran frame. Frame delta kecil yang sah dapat ditolak oleh parser lama setelah frame yang lebih besar berhasil.

`IoTMediaProtocol.parseFrame` kini menerima chunk/gap lebih besar daripada payload video, dengan batas resource dan alignment16B. Prefix tetap harus berada di dalam payload; chunk audio terenkripsi tetap harus muat karena jalurnya memproses satu chunk langsung. Panjang body/header dan batas4MiB tetap divalidasi. Regresi sintetis menguji CBC/ECB untuk payload7B dengan chunk128B, serta gap4096B setelah chunk16B.

## Feedback notifikasi

Native receiver membalas **COMMAND_NOTIFY + method197120** dengan COMMAND_IOT + method197120 dan params `{flag:2,tms1,decode_tms}`. `tms1` menyalin angka challenge, bukan clock lokal. Tidak ditemukan pemeriksaan `flag ==1` pada handler masuk. Jalur berbeda untuk NOTIFY dan IOT mencegah loop balasan; respons IOT biasa tidak dibalas lagi.

Client kini mengirim balasan terenkripsi pada sesi/channel yang sama, hanya untuk notifikasi valid dengan angka `tms1` finite. `decode_tms=0` memakai default awal/base handle vendor, karena antrean MediaCodec independen belum mengukur akumulator defisit decode signed millisecond milik HSLivePlayer. Nilai ini tidak diganti dengan PTS atau waktu monotonic. Tidak ada heartbeat periodik yang ditebak. Bukti tidak membuktikan firmware pengguna memutus sesi karena feedback sebelumnya tidak dikirim.

## Penanganan kegagalan

- Paket ber-command tidak dikenal dapat dilewati hanya pada reader stream setelah panjang/batas versi/security divalidasi; transaksi auth tetap strict. Shutdown teridentifikasi menjadi EOF/network recovery, bukan kegagalan password.
- Media malformed dengan batas paket luar yang valid dibuang, decoder kembali menunggu keyframe; tiga kegagalan media berturut-turut mengakhiri sesi. Header/ukuran tak aman tetap langsung ditolak, tanpa scanning payload untuk mencari header.
- Kegagalan tulis PTZ dan reader masuk ke satu penanganan per generation. Error tulis tidak lebih dulu mengganti `Streaming` dengan `Error` dan menggagalkan keputusan reconnect. Percobaan tetap1/2/4detik, tanpa melanjutkan gerak otomatis.
- Error stop watchdog ditangani sebagai best effort; tidak dibiarkan menjadi exception coroutine yang tidak tertangani. Stop motor tetap belum diverifikasi.
- Diagnostik schema2 mencantumkan versi app, `reason`, `detail` fase (header/body/media/control/decoder/PTZ), command dan panjang bila tersedia. Error stream dicatat di STREAM/FRAME, bukan selalu AUTH. Tidak ada raw exception message, payload, key, SID/handle, password atau footage yang ditambahkan.

## Bukti dan batas validasi

[Ledger instruksi statis](evidence/iot-disconnect-static.md) mencatat alamat ELF, hash APK/library, gating feedback dan arti `decode_tms`. Sumber berasal dari ekstraksi REA yang cocok, disassembly Capstone dan relocations readelf; library vendor tidak dieksekusi atau dimasukkan ke aplikasi.

Build APK debug0.2.1 dan34 tes JVM tanpa koneksi lulus; lint0 error. Tes mencakup regresi sparse-tail/gap, header unknown yang bounded dengan auth strict, encoding feedback, schema diagnostik, dan kegagalan stop watchdog. [Hasil/hash](../validation/results.json) dan [command offline](../validation/offline-build-command.txt) merekam pemeriksaan. APK pengujian Android hanya dikompilasi. Tidak ada kamera, loopback socket, emulator, probe LAN, atau pengujian koneksi yang dijalankan pada perbaikan ini, sesuai instruksi pengguna sebelumnya.

Pembaruan ini belum membuktikan stream30menit, pemulihan Wi-Fi, motor PTZ, atau seluruh setup AP. Log pengguna merupakan bukti interoperabilitas parsial versi sebelumnya, bukan acceptance penuh atau bukti bahwa putusnya koneksi telah terselesaikan pada perangkat fisik.
