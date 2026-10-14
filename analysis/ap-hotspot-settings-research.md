# Riset perubahan SSID dan password AP kamera

Tanggal: 14 Oktober 2026. Target: V380 6.4.90, APK SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Analisis statis REA/JADX; APK tidak dijalankan dan tidak ada koneksi/perubahan konfigurasi kamera.

## Kesimpulan

**Memungkinkan pada tingkat SDK/protokol: ada operasi yang menerima SSID dan password AP kamera. Dukungan pada firmware kamera pengguna belum terverifikasi.** Pengaturan ini berbeda dari password login lokal kamera dan kredensial router tujuan.

## Platform baru: bukti langsung

`DeviceNetworkSetting.setWiFiAPConfig(DeviceInfo, LoginHandle, String, String)`:

- Menolak device/login/string null dengan `-274`.
- Mengembalikan `0` tanpa mengirim konfigurasi jika platform login lebih tua dari `DEVICE_PLATFORM_TYPE_NEW`; jangan menganggap nilai ini bukti berhasil mengubah AP legacy.
- Membentuk input action berikut, kemudian memanggil `DeviceConfigSetting.startActionNewPlatform(deviceInfo, loginHandle, Protocol.DAID_NET_CONFIG, input)`:

```json
{"mode":1,"ssid":"Base64(bytes SSID baru)","pwd":"Base64(bytes password AP baru)"}
```

- Meneruskan `ActionResult.getResult()` bila hasil tersedia. Tidak ada aturan panjang/karakter maupun pemeriksaan kemampuan firmware dalam method ini.
- `getBytes()` pada method AP tidak menyebut charset secara eksplisit; jangan mengklaim pemulihan literal UTF-8 dari method tersebut.

Evidence: [setWiFiAPConfig](evidence/ap-hotspot-DeviceNetworkSetting-setWiFiAPConfig-0.sanitized.json), source ID `ev_f4de5f581315f53bb59eceb7342cdc05ccc42dc228124ae2c37b0ef7564e1ce9`.

Konstanta `DAID_NET_CONFIG = 262656` sudah dipulihkan pada [bukti konstanta](evidence/ap-public-constants.json). Jalur action yang sebelumnya dipulihkan memakai method request/response `197384/197385`, params `{daid:262656,in:input}`, dan sesi terautentikasi. Lihat [AP provisioning](ap-provisioning.md). Nilai pembeda: pengaturan AP menggunakan **mode 1**; pengaturan kamera bergabung ke router menggunakan **mode 2**. Jalur direct/MQ tergantung login/platform; adanya SDK tidak menjamin seluruh firmware menerima action.

Penelusuran referensi REA menemukan satu caller: `DeviceConfigSettingNetManagerFragment.AnonymousClass4.run`. [Bukti referensi](evidence/ap-hotspot-refs.sanitized.json). [Body caller](evidence/ap-hotspot-ui-save.sanitized.json) memperlihatkan `saveConfig()` membaca `layoutInputWifiName.etInput` dan `layoutInputWifiPassword.etInput`, melakukan trim/validasi, lalu meneruskan keduanya ke `setWiFiAPConfig`. Caller menganggap hasil SDK **256** sukses, kemudian menyimpan `strApName` dan `strApPassword`. Jadi ada alur UI untuk mengirim nama dan password; batas/prefix firmware tetap belum terbukti. Excerpt anonymous caller ditampilkan decompiler dalam konteks method induk `saveConfig()`.

### Validasi UI vendor

[checkWifiName](evidence/ap-hotspot-ui-checkWifiName.sanitized.json) memeriksa nama tidak kosong dan panjang maksimal **30**. [checkWifiPassword](evidence/ap-hotspot-ui-checkWifiPassword.sanitized.json) memeriksa password **8–30**. Penghitungan memakai `Editable.length()`/`String.length()`, bukan panjang byte UTF-8. `saveConfig()` mengirim hasil trim; validator awal menerima Editable sebelum trim, sehingga ini bukan validasi ideal yang harus disalin persis.

[initViews](evidence/ap-hotspot-ui-initViews.sanitized.json) mengisi kedua input dari `getStrApName()` dan `getStrApPassword()`, mengaitkan TextWatcher untuk keduanya, dan memasang filter karakter dari resource `edittext_rule_username`/`edittext_rule_password_new`. Isi resource tersebut belum dipulihkan di riset ini. Ini memperkuat bahwa form tersebut mengedit hotspot kamera. Batas UI bukan bukti batas firmware atau seluruh protokol.

## Legacy: tersedia, format belum siap implementasi

`setNetworkAP(DeviceInfo, boolean, String, String, boolean, String, String, LoginHandle)` memiliki dua kelompok flag/nama/password. Cabang lama menulis command `112`, flag kelompok AP pada offset 68, nama pada 72, password pada 136; cabang versi setting baru menulis command `312`, flag pada 13, nama pada 17, password pada 81. Pseudocode memeriksa command reply `212/412` dan status `1001`, dipetakan menjadi hasil SDK `256`.

**Ini observasi dekompilasi, bukan spesifikasi wire tervalidasi.** Ada warning duplicated blocks dan kejanggalan cabang `312`: sebagian header ditulis ke buffer `b` tetapi buffer `a` dikirim, serta field pada offset 9 berasal dari `getVersion()`. DEX instruction tracing diperlukan sebelum dipakai; jangan mengganti command `320` yang sudah ada secara coba-coba.

Evidence: [setNetworkAP](evidence/ap-hotspot-DeviceNetworkSetting-setNetworkAP-0.sanitized.json), `ev_5fbc05e1c4eb28796691398aec6f500340bb708fd75a34722b40d2431b34b835`. [getNetworkConfig](evidence/ap-hotspot-DeviceNetworkSetting-getNetworkConfig-0.sanitized.json) dan [NetworkConfigInfo](evidence/ap-hotspot-NetworkConfigInfo.sanitized.json) membedakan `strApName`/`strApPassword` dari `strWifiName`/`strWifiPassword`.

## Implikasi untuk aplikasi ini

`ApProtocol.wifiConfigParams()` saat ini membuat `mode:2`: fitur provisioning router, bukan editor hotspot. Dukungan AP memerlukan operasi terpisah memakai `mode:1`, login valid, penanganan hasil action, dan pembaruan koneksi/profil setelah verifikasi. Jangan mengubah mode builder provisioning yang sudah ada.

Urutan verifikasi yang disarankan: identifikasi platform dan kemampuan firmware, pertahankan SSID lama saat pertama memverifikasi perubahan password, lalu verifikasi rename secara terpisah. Setelah perubahan, sambungkan ulang ke SSID dengan kredensial baru dan pastikan device ID sama. ACK action saja tidak membuktikan hotspot sudah berubah atau perubahan bertahan setelah reboot. Batas firmware untuk password/SSID, aturan prefix, AP+station simultan, dan persistensi belum dibuktikan; batas UI vendor tercatat di atas.

## Sumber publik pelengkap

Pencarian publik juga menemukan petunjuk perubahan password hotspot, tetapi tidak memberi kontrak rename untuk kamera pengguna. [Manual GuangDong V380 D8](https://device.report/m/8c7d3fe8267e3be2e6ef3526edb8ebb49873c5a0ef43fcd94e9a07b0919316ed) menyebut perubahan password melalui Device network management dengan batas eksplisit perangkat CPE. Ini manual produk yang dihosting pihak ketiga dan bukan bukti kompatibilitas target. Kesimpulan teknis utama di atas berasal dari APK yang dianalisis.

Semua berkas evidence baru merupakan turunan REA tersanitasi/noncanonical yang menyimpan source evidence ID, bukan pengganti bundle REA kanonis.

## Hasil implementasi debug

`ApHotspotProtocol` menyediakan builder action mode1, pembacaan AP dan verifikasi; `CameraSession` menjaga autentikasi aktif dan snapshot jaringan sebelum writer. Pending marker atomik dan alur reconnect/verifikasi ditambahkan ke ViewModel/Pengaturan. Builder provisioning mode2 dipertahankan. Tidak ada pengujian kamera atau perubahan SSID/password nyata.

Temuan tambahan sebelum implementasi:

- Network DPID group4: `NETWORK_MODE=262401`, `AP_SSID=262403`, `AP_PASSWORD=262405`. [Konstanta terpilih](evidence/ap-hotspot-network-constants.sanitized.json), [parser DEX](evidence/ap-hotspot-property-parser.dex.txt). Mode readback menggunakan `getLongValue` sebagai **bitmap antarmuka**, bukan enum action mode1/mode2; nama/password readback menggunakan string langsung, bukan Base64.
- Capability `net[1]` adalah NETWORK_CFG; `SOFTAP_CFG=BMPC(3)=4`, dengan `BMPC(i)=1<<(i-1)`. [Bukti DEX](evidence/ap-hotspot-ability.dex.txt). Client memerlukan bit eksplisit; null/absen tidak dianggap didukung seperti default optimistis vendor pada `isSupportNetworkConfig`.
- [Resource karakter](evidence/ap-hotspot-input-resources.txt) membatasi nama baru ke alfanumerik dan password ke alfanumerik ditambah `@#_^*%.+`. Nama aktual yang dibaca boleh dipertahankan jika memenuhi batas ukuran/karakter kontrol; ini memungkinkan perubahan password pada SSID pabrik dengan underscore tanpa mengarang aturan rename prefix.
- [Daftar property vendor](evidence/ap-hotspot-DeviceConfigSetting-getDeviceConfigureNewPlatform-1.sanitized.json) meminta ketiga property jaringan tersebut. Client tidak meminta AP_PASSWORD untuk prefill, hanya untuk membuktikan hasil setelah perubahan. Ini menghindari sukses palsu dari asosiasi SSID lama yang masih di-cache Android. Field tersebut tidak dipersistenkan atau diekspor.

Penyesuaian rencana: kode dipisahkan ke `ApHotspotProtocol.kt` agar dialect AP tidak masuk batch pengaturan SD. Aktivasi debug didasarkan pada autentikasi LAN sukses, capability SoftAP eksplisit, readback lokal sukses, SSID jaringan Android cocok, dan discovery device ID pada jaringan terikat. `pver` belum tersedia pada kontrak key exchange LAN ini; tidak dibuat nilai default. Adapter menjalankan hanya action direct yang dipulihkan dan tidak mengimplementasikan kebijakan routing MQTT vendor. Keberhasilan getter/capability tidak menjamin action diterima firmware; penolakan/hasil ambigu tetap ditangani tanpa fallback atau pengiriman ulang. Dukungan vendor untuk pver>=5 serta profil yang hanya bekerja melalui MQ/cloud tidak diklaim.

Pemeriksaan SSID Android berasal dari NetworkSpecifier yang telah dipenuhi untuk Network yang sama, atau SSID aktual dari WifiInfo/default Wi-Fi bila tersedia. Jika SSID tidak dapat dibaca atau jaringan tidak cocok, writer terkunci. Kehilangan jaringan yang sedang dipakai menginvalidasi sesi; penghentian/penggantian sesi juga mencabut akses sebelum menunggu mutex.

Batas pengujian: [rekaman validasi implementasi](../validation/ap-hotspot/results.json). Build, lint dan tes di memori membuktikan logika perangkat lunak saja. Acceptance firmware, readback password perangkat nyata, persistensi reboot, izin Android/OEM, koneksi radio AP dan render/TalkBack masih belum terverifikasi. Emulator API30 berhenti SIGSEGV saat boot; tes UI/storage native hanya dikompilasi pada task ini.
