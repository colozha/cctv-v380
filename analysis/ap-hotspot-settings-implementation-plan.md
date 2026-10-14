# Rencana implementasi pengaturan SSID dan password AP kamera

Tanggal: 14 Oktober 2026. Status: implementasi debug tersedia. Build/lint dan tes offline dijalankan; UI/storage native hanya dikompilasi karena emulator SIGSEGV, sedangkan acceptance kamera tetap ditunda. [Hasil dan penyesuaian lingkup](ap-hotspot-settings-research.md#hasil-implementasi-debug).

Tujuan: pengguna dapat mengganti nama SSID, password, atau keduanya pada hotspot kamera yang sudah berhasil tersambung dan terautentikasi. Profil tersimpan, hasil discovery, atau koneksi Wi-Fi Android saja tidak membuka akses perubahan.

Dasar: [riset AP hotspot](ap-hotspot-settings-research.md), [kontrak provisioning](ap-provisioning.md), [IoT LAN](iot-lan-protocol.md), serta implementasi `CameraSession`, `CameraViewModel`, `CameraSettingsScreen`, `ApProtocol`, `WifiNetwork`, dan `ProfileStore` saat ini. Prinsip Ponytail: gunakan satu sesi pemilik, transport/kripto dan Material Compose yang sudah tersedia; tidak memerlukan dependensi atau modul baru.

## 1. Keputusan lingkup

- Tambahkan bagian **Hotspot kamera** di tab **Pengaturan**, dengan aksi simpan tersendiri dari pengaturan gambar/lampu/perekaman.
- Versi awal menggunakan jalur IoT lokal terautentikasi yang kontraknya terbukti. Tetap eksperimental/debug sampai acceptance firmware selesai. Legacy dan jalur yang memerlukan MQ/cloud tetap tidak dapat menulis.
- Pengguna harus sudah berhasil login ke kamera yang sedang dibuka. Frame video pertama tidak menjadi syarat; sesi `Ready` yang sah cukup sehingga fitur dapat dipakai tanpa Surface/decoder.
- Batasi aktivasi awal pada kamera dengan AP yang sudah aktif dan koneksi melalui AP kamera tersebut sudah diverifikasi. Action `mode:1` belum terbukti mempertahankan koneksi station atau mengaktifkan AP+station; versi awal tidak menawarkan perpindahan kamera dari station/router ke AP. Perluasan akses melalui LAN dapat dilakukan setelah semantik mode dan readback terbukti pada firmware sasaran.
- SSID dan password selalu dikirim sebagai satu pasangan karena demikian kontrak action. Perubahan SSID saja mengharuskan pengguna memasukkan password AP yang ingin dipertahankan; tidak mengirim password kosong sebagai tanda “tidak berubah”. Perubahan password saja mempertahankan SSID yang telah dibaca/diverifikasi.
- Satu aksi pengguna menghasilkan paling banyak satu pengiriman action. Timeout, restart, reconnect, atau tombol pemeriksaan status tidak mengirim action lagi.
- Sukses berarti AP tujuan dapat diakses dengan kredensial yang diminta, kamera dengan device ID yang sama berhasil ditemukan dan diautentikasi ulang. ACK sendiri diberi status **Diterima kamera, menunggu verifikasi**.
- Password AP hanya disimpan sementara di memori untuk pengiriman dan reconnect. Password lokal/login kamera dan opsi penyimpanannya tetap terpisah.

## 2. Kontrak yang sudah diketahui dan pekerjaan pembuktian

| Bagian | Keputusan |
|---|---|
| Operasi vendor | `DeviceNetworkSetting.setWiFiAPConfig(DeviceInfo, LoginHandle, String, String)` |
| Action | `DAID_NET_CONFIG = 262656` |
| Params | `{daid:262656, in:{mode:1, ssid:Base64(bytes), pwd:Base64(bytes)}}` |
| Transport direct | Common IoT action request/response `197384/197385`; sesi autentikasi aktif, secure type 2, magic 0, respons diminta |
| Korelasi | Validasi command, request ID, response method, channel, secure type dan session ID sesuai kontrak yang sudah dipulihkan |
| Sukses wire | Kode common-IoT `1000`; wrapper/caller SDK memetakan keberhasilan menjadi `256`. `0` dari cabang platform lama bukan sukses write |
| Pembeda provisioning | Builder router yang sudah ada memakai `mode:2`; perilakunya dipertahankan dan tidak dijadikan parameter mode bebas dari UI |
| Input vendor | Nama 1–30 dan password 8–30 menurut panjang string/Editable; validasi UI bukan bukti batas firmware |
| Readback | Pulihkan property/action baca mode AP dan SSID aktual dari platform baru. `getNetworkConfig` legacy tidak boleh dianggap getter IoT |
| Pemilihan jalur | Pulihkan metadata/capability/routing yang menentukan action lokal; keberhasilan live, `wifiConfiguration=true`, nama SSID, atau override profil saja tidak membuktikan dukungan pengaturan AP |

Tahap awal riset statis melengkapi readback, filter karakter resource `edittext_rule_username`/`edittext_rule_password_new`, dan pemilihan direct versus MQ. `IoTSession` saat ini tidak membawa `pver`; jangan menggunakan default rekaan atau menyamakan versi common header dengan `pver`. Tambahkan metadata pasif hanya jika hasil ekstraksi memang membutuhkannya.

Jika kontrak untuk suatu profil belum lengkap, tampilkan **Dukungan hotspot belum dapat dipastikan** dan nonaktifkan writer untuk profil tersebut. Penolakan kamera harus membedakan unsupported, izin ditolak, sesi kedaluwarsa, dan kebutuhan cloud; semantik bit permission tidak ditebak.

## 3. Gerbang wajib: kamera sudah benar-benar terkoneksi

### Temuan pada kode saat ini

`CameraSession.connect()` mengisi `profile` sebelum `authenticate()` selesai. `CameraViewModel.launchFeature()` dan sejumlah kontrol pengaturan sekarang memeriksa kesamaan `connectedProfile.id`. Pemeriksaan itu tidak cukup untuk fitur ini: identitas profil yang terpasang belum membuktikan autentikasi berhasil.

`generation` juga berubah pada `stopStream()`, bukan hanya saat sesi autentikasi diganti. Jangan membuat status autentikasi hilang setiap kali berpindah dari Live ke Pengaturan; bedakan identitas sesi autentikasi dari generation pekerjaan media.

### Aturan akses

| Kondisi | Edit/simpan hotspot |
|---|---|
| Profil ada atau kamera ditemukan, belum login | Dikunci |
| Android tersambung ke SSID AP, belum login kamera | Dikunci |
| `Connecting`, `Authenticating`, `Reconnecting`, `Idle`, `Error` | Dikunci |
| `Ready` dengan autentikasi aktif, identitas cocok, AP aktif terverifikasi, jalur didukung | Boleh mengisi; simpan mengikuti validasi dan status pekerjaan |
| Live/playback aktif dengan autentikasi sah | Masuk Pengaturan menghentikan media/PTZ dahulu; writer hanya berjalan dari keadaan siap |
| Sesi terputus/kedaluwarsa atau jaringan hilang ketika form terbuka | Simpan langsung dikunci; draft tidak menjadi antrean pengiriman |
| Profil/kamera berganti, sesi lama masih memiliki callback | Dikunci untuk sesi lama; callback diabaikan |
| Sedang menyimpan, reconnect, verifikasi, download, format, provisioning, atau ada hasil AP pending | Tidak boleh memulai write lain |

Implementasikan satu predicate status akses yang bersumber dari pemilik sesi, lalu gunakan di UI dan ViewModel. Pengecekan otoritatif tetap di method session sebelum menulis, meskipun dipanggil langsung tanpa UI.

Syarat session sebelum write:

1. `profileId` dan device ID sesuai kamera/form yang memulai operasi; sesi IoT masih aktif dan berasal dari login yang benar-benar selesai.
2. Tidak ada close, pergantian sesi, kehilangan jaringan yang sudah terdeteksi, atau reconnect yang menginvalidasi operasi.
3. Profil/jalur mendukung action AP; AP aktif dan jaringan terikat ke AP kamera yang telah diverifikasi. Unknown tetap terkunci.
4. Media/PTZ sudah berhenti; state siap dan operasi lain tidak sedang berjalan. Ambil snapshot identitas `IoTSession` dan generation setelah penghentian media, bukan sebelumnya.
5. Jalankan pembacaan AP/status terautentikasi yang tidak mengubah konfigurasi pada jaringan yang sama, dengan deadline. Pemeriksaan ini membuktikan kamera masih merespons sebelum action; kegagalan membatalkan write.
6. Di dalam mutex operasi, periksa kembali snapshot sesi, identitas, state, cancellation, dan kepemilikan pekerjaan tepat sebelum write. Jangan membuat sesi otomatis dari draft yang sebelumnya ditolak.

Identitas objek `IoTSession` dapat menjadi token sesi yang tidak diserialisasi; gunakan mekanisme paling kecil yang cukup untuk menolak hasil dari sesi lama. Invalidate status akses pada close/abort, auth gagal/kedaluwarsa, recovery, pergantian profil dan background. Penerimaan ulang membutuhkan login yang berhasil.

Tidak ada pemeriksaan yang menjamin radio tetap hidup setelah preflight. Gangguan setelah write mulai harus ditangani sebagai hasil yang mungkin sudah diterapkan, bukan alasan mengirim ulang.

## 4. Alur layar dan validasi

Bagian Hotspot kamera berada di luar blok `settings != null` milik pengaturan SD/gambar: kegagalan membaca pengaturan SD tidak otomatis menyembunyikan fitur AP. Snapshot AP mempunyai status baca sendiri dalam state fitur yang sama.

| Elemen | Perilaku |
|---|---|
| Belum tersambung | Pesan **Sambungkan kamera terlebih dahulu untuk mengubah hotspot.** Gunakan kontrol Sambungkan yang sudah ada; input dan simpan terkunci |
| Belum terverifikasi/didukung | Alasan spesifik dan aksi baca ulang bila relevan; tidak membuat nilai default |
| Nama SSID | Prefill hanya dari snapshot AP yang terverifikasi untuk sesi/profil sekarang; label **Nama hotspot kamera** |
| Password | Kosong secara default, tersamarkan, dengan tombol tampil/sembunyikan berlabel aksesibilitas; label **Password hotspot kamera** |
| Penjelasan | **Perubahan dapat memutus koneksi. Aplikasi akan mencoba menyambung ke hotspot dengan nama dan password ini. Password login kamera tetap sama.** |
| Tombol utama | **Simpan hotspot**; aktif hanya jika gerbang akses lulus, pasangan input valid, tidak busy/pending dan tidak ada draft pengaturan lain yang belum diselesaikan |
| Pemeriksaan hasil | **Sambungkan dan periksa hotspot** hanya melakukan koneksi/discovery/auth/readback, bukan write |
| Perangkat legacy/cloud | Alasan keterbatasan dalam bahasa pengguna; detail protokol hanya di diagnostik/dokumentasi |

Tidak perlu dialog konfirmasi aplikasi tambahan. Klik Simpan hotspot adalah aksi eksplisit pengguna; dialog izin/koneksi Android tetap mengikuti API sistem.

Keputusan validasi versi awal:

- SSID tidak kosong/tidak seluruhnya whitespace, panjang maksimal 30 unit string dan maksimal 32 byte UTF-8. Tolak NUL, karakter kontrol, serta whitespace awal/akhir dengan pesan yang jelas; tidak memotong atau melakukan trim diam-diam.
- Password panjang 8–30. Gunakan ASCII yang dapat dicetak tanpa spasi untuk versi awal, sehingga panjang byte dan karakter konsisten saat menghubungkan lewat WPA2 Android. Jangan memangkas, menormalisasi, atau mengganti password pengguna.
- Pemakaian UTF-8 di client merupakan keputusan implementasi eksplisit; method APK memakai `getBytes()` tanpa literal charset. Batas/prefix firmware dan filter vendor diperiksa pada tahap kontrak. Jika hasilnya mewajibkan batas lebih ketat, terapkan dan dokumentasikan sebelum writer diaktifkan.
- Tidak ada pembuatan prefix `MV`/`HSC` otomatis. Jika firmware membatasi prefix, aturan harus bersumber dari bukti profil.
- Validasi dilakukan di form dan serializer/session. Perubahan SSID saja tetap membutuhkan password yang diminta untuk AP tujuan. Jangan membaca/menampilkan password lama dari kamera hanya untuk prefill.

Gunakan komponen Material yang ada, target sentuh minimal 48dp, scroll/IME insets, tema terang/gelap, font scaling, TalkBack, dan layout landscape. Password tidak masuk `rememberSaveable`, `SavedStateHandle`, Bundle, clipboard otomatis, notifikasi, atau diagnostik.

## 5. Transaksi simpan dan status hasil

1. Bekukan pasangan input dan identitas profil/sesi ketika Simpan hotspot ditekan; cegah double tap sebelum coroutine dijadwalkan. Jangan menggunakan perilaku replace-job untuk membatalkan write lama lalu menjalankan write baru.
2. Validasi ulang akses dan input. Hentikan media/PTZ melalui pemilik sesi yang sama; jangan menutup material login yang masih dibutuhkan action.
3. Lakukan preflight terautentikasi dan cek snapshot sesi di mutex. Jika gagal, tampilkan **Belum dikirim** dan kunci akses sampai koneksi pulih.
4. Tulis pending marker secara atomik sebelum action; jika penyimpanan gagal, action tidak dikirim. Marker merupakan catatan pemulihan/verifikasi, bukan antrean pekerjaan.
5. Recheck sesi/cancellation setelah penyimpanan marker, kemudian kirim action satu kali dengan deadline. Catat secara internal apakah write belum dimulai atau sudah mungkin mencapai kamera; partial write termasuk hasil tidak pasti.
6. Validasi ACK terautentikasi dan terkorelasi. Penolakan definitif dari respons valid menjadi **Ditolak kamera**. Respons malformed, unrelated, atau plaintext success tidak menjadi sukses maupun bukti penolakan konfigurasi.
7. Setelah ACK sukses atau putus/timeout yang mungkin terjadi setelah pengiriman, hentikan recovery stream biasa dan invalidasi sesi lama. Masuk alur verifikasi AP; tidak mencoba login berulang ke host lama atau mengirim action kembali.
8. Sambungkan ulang ke SSID tujuan, discovery pada jaringan yang dipilih, cocokkan device ID yang sama, autentikasi dengan password lokal kamera, lalu baca mode/nama AP jika kontraknya tersedia. Host/port baru berasal dari discovery yang cocok, bukan asumsi gateway tertentu.
9. Setelah verifikasi berhasil, perbarui host/port profil secara atomik, pertahankan identitas dan password login kamera, hapus marker, lalu tampilkan **Hotspot terverifikasi**. Jika penyimpanan profil/marker gagal, tampilkan keberhasilan kamera dan kegagalan lokal secara terpisah; jangan menyarankan write ulang.

| Status operasi | Makna dan tindakan berikutnya |
|---|---|
| Siap | Kamera terautentikasi dan syarat akses lulus |
| Memeriksa koneksi | Preflight; belum mengirim action |
| Mengirim | Pasangan dibekukan, action paling banyak satu kali |
| Diterima kamera | ACK valid diterima; masih menunggu hasil AP |
| Menyambungkan ulang / Memverifikasi | Hanya operasi koneksi dan pembacaan |
| Hotspot terverifikasi | Kredensial AP tujuan bekerja dan identitas kamera sama setelah login ulang |
| Belum dikirim | Kegagalan definitif sebelum write; boleh menyimpan kembali setelah syarat akses pulih |
| Ditolak kamera | Respons penolakan valid; boleh memperbaiki input setelah status sesi diperiksa |
| Hasil belum diketahui | Write mungkin sudah diterapkan; arahkan ke pemeriksaan, tanpa retry action otomatis |

Jika SSID tujuan terdeteksi tetapi kamera belum dapat diautentikasi, tetap **Hasil belum diketahui**. Jika hotspot baru tidak muncul atau password ditolak Android, jangan menyatakan kamera menolak action; pengguna dapat mencoba menghubungkan ke hotspot lama/baru dan membaca status. Write berikutnya memerlukan rekonsiliasi pending serta koneksi autentikasi baru.

## 6. Reconnect, lifecycle, dan pemulihan

- Gunakan `WifiNetwork.connectAp()` dan socket yang diikat ke `Network` hasilnya. API29+ memakai permintaan koneksi/dialog sistem; API26–28 membuka pengaturan Wi-Fi lalu melanjutkan pemeriksaan ketika aplikasi kembali.
- Tidak menggunakan `ProvisioningManager.connectAp()` sebagai pintasan: alur setup memanggil disconnect/resume provisioning dan mempunyai pending marker sendiri. Gunakan helper jaringan dan discovery yang sudah ada dengan koordinasi AP edit dalam ViewModel.
- Tambahkan konteks handoff khusus edit AP pada `onBackground()`/`onForeground()`. `expectedHandoff` saat ini terkait provisioning dan berakhir setelah 30 detik; jangan meneruskannya tanpa membedakan pemilik operasi.
- Batas awal: request koneksi mengikuti 30 detik helper yang ada; total upaya otomatis koneksi/discovery/auth/readback maksimal 120 detik. Handoff sistem boleh mempertahankan salinan kredensial sementara sampai 120 detik; background biasa langsung menghapusnya. Timeout mengakhiri pemantauan dengan hasil belum diketahui.
- Jika lifecycle menutup sesi saat dialog sistem berlangsung, lanjutkan dengan autentikasi baru setelah kembali, bukan memakai SID/key lama. Password login tidak tersimpan diminta kembali melalui kontrol akses yang sudah ada.
- Saat aplikasi kembali setelah process death, baca marker dan tawarkan pemeriksaan saja. Password AP tujuan dimasukkan lagi karena tidak dipersistenkan. Tidak ada auto-submit ketika login/reconnect berhasil.
- Back/Batal sebelum write membatalkan tanpa perubahan kamera. Setelah write mungkin terkirim, tombol bernama **Hentikan pemeriksaan**; menutup layar tidak menjanjikan rollback atau pembatalan di kamera.
- Saat berpindah kamera, pending tetap terikat ke profil/device ID asal dan callback lama diabaikan. Jangan memindahkan akses atau draft password ke kamera baru.
- Pemulihan yang membuktikan kamera masih memakai AP lama boleh menutup status pending dengan hasil **Perubahan belum terverifikasi**. Jangan menawarkan reset kamera otomatis. Jika hasil tetap ambigu, status dipertahankan; pengguna dapat menutup catatan secara eksplisit dengan penjelasan bahwa itu hanya menutup status lokal, bukan mengubah hotspot.

## 7. Penyimpanan dan kerahasiaan

Perluas `ProfileStore` dengan satu `AtomicFile` pending AP yang terpisah dari pending setup dan format. Cukup satu operasi pending AP per aplikasi; penggunaan beberapa kamera secara bersamaan tidak termasuk lingkup.

Isi minimum: versi schema, ID operasi lokal, profile ID, device ID, SSID asal bila diketahui, SSID tujuan, waktu pembuatan, dan tahap terakhir yang aman dipulihkan. Batasi panjang/ukuran, validasi saat membaca, dan perlakukan marker rusak sebagai status yang perlu ditangani, bukan alasan mengirim ulang. Jangan menyimpan password AP, password login, payload, SID, handle, atau key. SSID merupakan metadata lokal untuk pemulihan dan tidak diekspor lewat diagnostik.

Marker yang tersimpan sebelum crash harus selalu diperlakukan konservatif sebagai mungkin terkirim. Dalam proses yang masih hidup, marker boleh dihapus setelah kegagalan yang terbukti terjadi sebelum write atau penolakan definitif. Setelah ACK/hasil ambigu, marker tetap sampai verifikasi atau penutupan status lokal yang eksplisit.

AP edit tidak memerlukan field password baru pada `CameraProfile`, perubahan `hasSavedPassword`, maupun pemanggilan `changeLanPassword()`. Setelah verifikasi, hanya host/port yang perlu diperbarui jika berubah. Penyimpanan kredensial AP permanen tidak termasuk versi awal.

Gunakan `CharArray` dan buffer byte yang dihapus dalam `finally`; ambil salinan terpisah untuk reconnect karena `WifiNetwork.connectAp()` menghapus argumennya. String Compose/JSON/Base64 yang tak dapat dihapus deterministik tetap berumur sesingkat mungkin; jangan mengklaim seluruh jejak secret di heap bisa dihapus. Hapus draft/salinan saat selesai, batal, berganti profil, background biasa, atau batas handoff habis.

Diagnostik memakai allowlist: tahap, status, kode perangkat, reason enum, durasi, dan jumlah percobaan verifikasi bila diperlukan. Tidak mencatat SSID, password, body, session/key, atau `toString()` objek yang mengandung secret. Tambahkan tahap AP edit/AP verify pada `Diagnostics.kt`; sesuaikan schema/export hanya bila format ekspor benar-benar berubah.

## 8. Peta perubahan kode

| File | Perubahan yang direncanakan |
|---|---|
| `protocol/ApProtocol.kt` | Builder/transaction AP khusus `mode:1`, validasi pasangan, reuse framing/enkripsi/respons; builder router `mode:2` tetap terpisah |
| `protocol/CameraSettingsProtocol.kt` | Tambahkan kontrak readback AP yang benar-benar dipulihkan, dengan parser bounded; jangan memasukkan property AP ke batch SD tanpa kebutuhan |
| `session/CameraModels.kt` | Model kecil snapshot/availability AP jika perlu; tidak memakai boolean provisioning sebagai dukungan AP edit |
| `session/CameraSession.kt` | Status akses autentikasi yang sah, readback/preflight, method `configureApHotspot` dengan pemeriksaan wajib, mutex dan penolakan sesi lama; kendalikan recovery saat AP berganti |
| `ui/CameraViewModel.kt` | State AP dalam pemilik fitur yang sama, draft sementara, single flight, pending recovery, handoff dan verifikasi; hasil lama dijaga oleh versi operasi/profil |
| `ui/CameraSettingsScreen.kt` | Bagian Hotspot kamera, input terkunci sebelum koneksi sah, validasi, status dan aksi verifikasi |
| `ui/ProfileStore.kt` | Pending marker AP atomik tersendiri; update host/port setelah verifikasi tanpa mengganti credential login |
| `transport/WifiNetwork.kt` | Reuse connect/release/binding; perluas pemberitahuan kehilangan jaringan hanya jika diperlukan untuk mengunci akses dan menginvalidasi operasi |
| `ui/MainActivity.kt` | Routing pengaturan Wi-Fi sistem/permission dan handoff API26–28 bila callback yang ada belum dapat digunakan ulang |
| `session/Diagnostics.kt` | Tahap/status AP dan pesan error yang tidak menyarankan write ulang pada hasil ambigu |

Tidak membuat pemilik sesi kedua, repository/service tambahan, cloud fallback, generic mode editor, background worker, atau retry queue.

## 9. Tahapan implementasi dan kriteria lulus

### Tahap 0 — lengkapi kontrak statis

Pulihkan readback AP platform baru, filter karakter, pemilihan jalur dan kemampuan, serta semantik action `mode:1`. Arsipkan excerpt tersanitasi dengan source evidence ID dan perbarui indeks. Batas unknown dipertahankan per profil; legacy menunggu DEX tracing terpisah.

**Lulus:** profil yang akan diaktifkan mempunyai request/response, cara mengetahui AP aktif dan SSID aktual, aturan routing, serta definisi dukungan yang dapat dijelaskan. Tidak membutuhkan pengubahan kamera untuk menyelesaikan riset statis.

### Tahap 1 — akses sesi dan codec

Implementasikan guard yang tidak bergantung hanya pada `connectedProfile`, builder AP, validasi dan parser readback. Tambahkan pemeriksaan pada entry point session sehingga bypass UI tetap ditolak. Pertahankan provisioning `mode:2`.

**Lulus:** semua keadaan belum autentikasi/tidak cocok/invalid tidak menghasilkan write action; vector AP valid memakai mode/action yang benar dan respons palsu ditolak.

### Tahap 2 — form dan transaksi satu kali

Tambahkan bagian UI, state operasi, single flight, preflight dan pending marker. Gunakan version guard yang sudah ada dengan perlakuan khusus bahwa write aktif tidak boleh diganti oleh write baru.

**Lulus:** form hanya dapat dipakai sesudah koneksi sah, double tap tidak menggandakan action, kehilangan koneksi mengunci simpan, dan hasil ambigu tidak diberi label sukses.

### Tahap 3 — handoff dan verifikasi

Hubungkan reconnect API29+/pengaturan sistem API26–28, discovery identitas, autentikasi ulang, readback dan update profil. Pulihkan marker setelah restart tanpa membawa secret atau write otomatis.

**Lulus:** verifikasi mengakses SSID tujuan dengan password tujuan dan kamera yang sama; cancel/background/crash hanya memulihkan pemeriksaan. Penyimpanan lokal gagal tidak mengubah keputusan pengiriman.

### Tahap 4 — validasi offline dan dokumentasi

Gunakan JUnit, coroutine test, server loopback/transport sintetis, dan instrumentation yang sudah tersedia. Tambahkan kasus yang membuktikan kontrak/risiko, bukan test per getter. Jalankan build dan subset pemeriksaan relevan saat implementasi; jangan menjalankan rangkaian tes koneksi hanya karena task Gradle menggabungkannya.

**Lulus:** matriks di bawah terverifikasi secara offline; dokumentasi menjelaskan dukungan dan batas fisik dengan jujur. Update readme/IMPLEMENTATION/DESIGN hanya sesuai perilaku yang sudah diimplementasikan.

### Tahap 5 — acceptance firmware dan rilis

Pengujian koneksi masih [ditunda](../validation/connection-tests-deferred/status.json). Tahap ini adalah pekerjaan mendatang; persetujuan rekomendasi rencana tidak otomatis mencabut batas pengujian tersebut. Jangan menjalankan koneksi/perubahan kamera selama penyusunan rencana ini.

Ketika pengujian dibuka, catat firmware/platform/routing yang sebenarnya. Uji perubahan password dengan SSID tetap dahulu, kemudian rename dengan password tetap, lalu perubahan keduanya. Verifikasi jaringan tujuan, device ID, login dan readback pada setiap operasi. Pemeriksaan persistensi setelah reboot dilakukan sebagai acceptance tersendiri; hasil reconnect saja tidak membuktikan persistensi reboot.

**Lulus rilis:** dukungan per firmware terbukti, offline AP bekerja tanpa internet, gerbang sesi/lifecycle lulus, reconnect Android tervalidasi dan tidak ada write ulang otomatis. Profil yang belum lulus tetap eksperimental atau terkunci.

## 10. Matriks pengujian wajib

| Area | Kasus dan hasil minimum |
|---|---|
| Belum terkoneksi | Profil tersimpan, discovery sukses, Wi-Fi AP tersambung, `profile` sudah terisi tetapi auth pending/gagal: UI terkunci dan pemanggilan session langsung mengirim nol action |
| Akses sah | Login berhasil tanpa frame/Surface: form dapat aktif setelah AP/routing terverifikasi; berpindah Live → Pengaturan tidak menghapus autentikasi yang sah |
| Sesi stale | Putus/error/reconnect, close/abort, ganti kamera, atau auth expiry saat preflight/sebelum write: action ditolak; callback lama tidak mengubah profil/status baru |
| Race lifecycle | `stopMedia()` menaikkan generation; snapshot diambil sesudahnya. Abort/background ketika writer menunggu mutex atau penyimpanan marker tidak mengizinkan action dari sesi lama |
| Dukungan | Legacy, unknown, cloud-only, AP tidak aktif, generic `wifiConfiguration=true` tanpa bukti AP: tidak dapat menulis |
| Input | SSID kosong/whitespace/kontrol, 30/31 unit, UTF-8 >32 byte, password 7/8/30/31, spasi/non-ASCII, password kosong untuk rename: validasi konsisten, invalid menghasilkan nol action |
| Protokol | Mode1 versus mode2, Base64 benar, secure type2, korelasi ID/method/channel/SID, kode1000 versus wrapper256; respons unrelated/plaintext/malformed tidak menjadi sukses |
| Single flight | Double tap, recomposition, pergantian tab, klik pemeriksaan, reconnect dan restore: jumlah action maksimal satu per submit, nol pada recovery |
| Kegagalan write | Preflight gagal/marker gagal sebelum write: belum dikirim. Partial write, timeout atau EOF setelah write mulai: hasil belum diketahui dan hanya verifikasi |
| Penolakan | Respons penolakan autentik/terkorelasi dibedakan dari hasil ambigu; tidak menyatakan password salah hanya karena jaringan belum muncul |
| Verifikasi | SSID ada tetapi kamera berbeda/login gagal/readback tidak cocok: bukan sukses. Jaringan tujuan + kredensial tujuan + identitas sama + auth ulang sah: terverifikasi |
| Storage | Crash setelah marker/sebelum atau sesudah write/ACK, marker rusak, disk penuh, update host gagal: tidak mengirim ulang dan credential login tidak tertimpa |
| Secret | Password AP tidak masuk file/Bundle/saved state/log/export; buffer disapu pada semua exit dan timeout handoff; Android helper menerima salinan yang tepat |
| Android/UI | API26–28 manual dan API29+ dialog sistem; permission ditolak, network lost, font scale1.3, portrait/landscape, TalkBack, tema terang/gelap, keyboard, Back dan background |
| Regresi | Provisioning router tetap mode2; password LAN tetap terpisah; PTZ berhenti; live/playback/download dan pending format tidak berebut pekerjaan dengan AP edit |

## 11. Definisi selesai

Fitur selesai diimplementasikan ketika pengguna kamera yang sudah terautentikasi dapat mengubah pasangan AP melalui profil lokal yang didukung, semua gerbang dan pengujian offline di atas lulus, pemulihan tidak pernah memutar ulang write, dan UI membedakan ACK dari verifikasi hasil. Klaim kompatibilitas kamera fisik hanya diberikan setelah acceptance firmware tahap5; sampai saat itu status eksperimental tetap terlihat.

Dokumen ini awalnya dibuat pada task perencanaan tanpa implementasi atau pengujian. Implementasi berikutnya dicatat di riset dan validasi yang ditautkan pada status di atas; kamera fisik tidak diakses atau diubah.
