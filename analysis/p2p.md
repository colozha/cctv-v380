# APK: pemilihan transport, MR relay, dan MQTT

Status: analisis statis terbatas melalui REA/JADX. APK `/home/husain/Downloads/v380-6-4-90.apk`, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. APK tidak dijalankan atau dimodifikasi, tidak ada koneksi kamera/cloud, dan tidak ada nilai kredensial/token akun yang diekstrak. Referensi nama field sesi di bawah menjelaskan alur kode saja, tanpa nilainya.

## Kesimpulan

**Teramati:** `Functions.connectToMRServer2` memilih endpoint TCP langsung atau MR/forwarding server; `Functions.connectToServer` benar-benar memakai `java.net.Socket.connect`. Pemilihan endpoint mengikuti alamat eksplisit, cache, hasil dispatch, domain fallback, lalu daftar IP fallback. Ini bukti relay selection, bukan bukti UDP hole punching.

**Teramati:** untuk platform baru, jalur live memakai `protocolType=2`; `pver>=5` dengan flag P2P tidak aktif memakai MQTT sebagai signaling sebelum native `HSMediaLibrary.startPlay`. Ini berlaku pada cabang MR maupun LAN. Gagal publish memanggil callback `onMqErrorStartP2p`; callback tersebut membuktikan adanya mekanisme fallback bernama P2P, bukan bentuk paket hole punching.

**Belum terjawab:** algoritme NAT traversal, rendezvous UDP, NAT classification, sequence/ack/retransmit P2P, pemilihan direct/relay dalam native, destination port media, dan framing wire modern. Inventaris native menunjukkan socket API dan beberapa generasi transmitter, tetapi tidak cukup untuk menyimpulkan transport aktif. [Analisis native](native.md) mempertahankan batas ini; provider disassembler native belum tersedia.

## Evidence dan batas lokasi

Seluruh signature adalah display signature JADX; nomor baris di bawah mengacu pada `normalized_result.source.text` dalam JSON evidence, **bukan offset instruksi DEX atau source asli**. REA tidak menyediakan DEX descriptor/offset pada provider ini. JVM heap dibatasi2048MiB dan setiap call dijalankan berurutan pada worker analisis ini.

| Label | Evidence file | Evidence ID |
|---|---|---|
| F-class | `evidence/p2p-Functions-class.json` | `ev_8aba393d7c39bc36b1038d01cd668e1a932bf2cefb4d70fc957728129eeb2540` |
| MR2 | `evidence/p2p-connectToMRServer2.json` | `ev_4db0b574a6ecb93da98d92e15b27e79c387e4018da419466f20aafdd6320e103` |
| TCP | `evidence/p2p-connectToServer.json` | `ev_4a6640d1b5d37600bae5613ae30d2a2af2ec6c1a8a8f8eeb95d562271e4e8367` |
| MQ-class | `evidence/p2p-MqttHelper-class.json` | `ev_f96b70d1c391d9b6bd4a994029181617d67a28a38698eea9c6651ab077840cf9` |
| MQ-exchange | `evidence/p2p-mqExchangePublish.json` | `ev_5ad0309ee5dc70d97db56ba3acf4f20cfe706c2348ca3c3ec4b37d84207e0113` |
| MQ-wakeup | `evidence/p2p-AppCallDeviceWakeUp.json` | `ev_a28cee8f91ee88d58f4a2dda5c03c33633926017fe898e93f1204709c3b941d4` |
| Play | `evidence/method-HSMediaPlayer-startPlayEX2.json` | `ev_3ba10a3d2f85e6a919d9ae1ec8a2d1a6d384771c637e3a6ba1d7571be82b4f30` |

Inventaris class dipakai untuk memilih overload, tanpa menebak indeks. `Functions.connectToMRServer2(String,int,int,int,int,boolean,boolean,int,int[])→ConnectResult` dan `connectToServer(String,int,int,int[])→Socket` masing-masing overload0 yang unik. Method Play adalah overload0 dari tiga `startPlayEX2`.

## MR/relay endpoint selection

Pada `com.macrovideo.sdk.tools.Functions.connectToMRServer2`, MR2 baris31–47 mencoba alamat eksplisit yang tidak kosong dengan port>0 dan langsung mengembalikan hasil `ConnectResult.ip/socket`. Artinya alamat tersebut tidak otomatis berarti cloud; pemanggil bisa memberi IP LAN maupun server tertentu. Method ini tidak melakukan login sendiri.

Tanpa alamat eksplisit, MR2 baris49–68 mempertimbangkan `DispatchServerGetter.getDispatchServer`, bergantung flag assigned-forward-server, masa cache, perubahan status, dan jenis kamera normal/panorama. Implementasi detail request dispatch tidak ditelusuri pada subanalisis ini. Baris69–71 menggunakan port8800 bila port masukan tidak positif dan menandai kasus cache hasil login1002 untuk melewati kandidat tertentu.

Urutan kandidat yang terlihat:

1. Cache MR aktif normal/panorama bila masih berlaku dan tidak diinvalidasi (MR2:78–143). Batas `_lUseMRTime` adalah600000ms.
2. Daftar server hasil dispatch `normalMRList` atau `panoMRList` (MR2:149–292).
3. `getMRServerDomain(deviceId)` (MR2:294–317).
4. Hingga tiga kandidat `getMRServerIP(index,deviceId)` (MR2:318–357).
5. Bila socket tersambung dan endpoint tidak ditentukan eksplisit, cache kembali host IP/port beserta waktu (MR2:365–374).

Setiap kandidat menggunakan `connectToServer`; event login dan telemetry merekam percobaan, tetapi tidak mengganti transport. `sIsAssignForwardServer` dapat mengubah alur. Nilai/domain/IP kandidat tidak diinventarisasi atau dihubungi.

JADX memberi warning duplicated-block pada MR2 dan output menyisakan artefak seperti perbandingan socket dengan0. Urutan endpoint dan pemanggilan `connectToServer` terbaca jelas; detail equivalence boolean pada branch kompleks harus diverifikasi melalui DEX/disassembly bila diperlukan.

## TCP dan IPv6

`Functions.connectToServer` memvalidasi host dan port, membuat `Socket`, lalu `socket.connect(new InetSocketAddress(host,port), timeout)` pada bagian akhir evidence TCP. Tidak ada `DatagramSocket`, STUN, atau paket UDP pada body method ini.

Sebelum connect, ada cabang alamat IPv4 yang memeriksa hasil DNS IPv6 sebuah domain dispatch, menggunakan cache60000ms, menghapus dua grup terakhir prefix IPv6 dan menambahkan representasi heksadesimal empat oktet target IPv4. Ia menulis flag `GlobalDefines.ISIPV6`. Ini observasi transformasi alamat yang menyerupai adaptasi IPv4-ke-IPv6; **tidak cukup untuk menyimpulkan NAT64 standar atau NAT hole punching**. Fungsi `pingNetwork` dipanggil pada salah satu cabang untuk pemeriksaan reachability; body-nya tidak dianalisis, sehingga jenis/tujuan probe itu belum diketahui.

## Pemilihan live signaling dan native transport

`HSMediaPlayer.startPlayEX2` menyimpan konfigurasi stream/channel, endpoint MR, index P2P, dan `isP2PStart=z6` (Play:26–54). Ia memilih alamat LAN bila `LoginHandle.isbInLan`, selain itu endpoint MR (Play:43).

| Kondisi | Alur teramati | Lokasi Play |
|---|---|---|
| Platform lama | `protocolType=0`, native `HSMediaLibrary.startPlay` | :284–297 |
| Platform baru | `protocolType=2` | :56–57 |
| Baru, MR, pver>=5, flag P2P=false | signaling HTTPS MQ, kemudian MQTT live exchange, lalu native | :58–169 |
| Baru, MR, pver<5 atau flag P2P=true | signaling HTTPS biasa, endpoint TRS dari hasil, kemudian native | :171–237 |
| Baru, LAN, pver>=5, flag P2P=false | UUID lokal dan MQTT live exchange, kemudian native | :238–278 |
| Baru, LAN, pver<5 atau flag P2P=true | langsung native | :279–282 |

Hasil signaling MR memasukkan endpoint TRS dan parameter channel sesi ke player sebelum native start. Cabang MQ menyiapkan metadata identitas kunci melalui fungsi SHA-256/ED25519Utils sebelum publish. Nilai kunci/parameter sesi tidak ditampilkan. Nama primitive ini tidak membuktikan detail handshake kriptografi lengkap; derivasi dan signature wire harus ditelusuri terpisah.

Publish yang sukses (`CallComplete(0)`) memanggil native `HSMediaLibrary.startPlay`. Publish gagal memanggil `onMqErrorStartP2p(window,1,deviceId)` untuk MR atau mode2 untuk LAN (Play:135,274). Call-site menunjukkan permintaan fallback; body penerima callback tidak ditelusuri. Error signaling tertentu menandai device offline atau berhenti, sedangkan error lain dijadwalkan ulang maksimal20 iterasi. Jangan menganggap semua kegagalan MQTT otomatis berhasil membuka P2P.

## MQTT sebagai control plane

`com.macrovideo.sdk.org.eclipse.paho.mqttv5.util.MqttHelper.mqExchangePublish(int,String,CallMqServiceHttpsResult,int,String)→Thread` mengambil info MQ perangkat, menandai LAN bila hasil HTTPS null atau non-LAN bila tersedia, lalu menunggu koneksi broker per-position (MQ-exchange:7–33). Loop tidur50ms dan berhenti sekitar200 iterasi; ini menunjukkan batas tunggu sekitar10detik, bukan jaminan deadline keras.

Method memeriksa `controlThreadMap[controlType]` agar hanya request aktif yang lanjut (MQ-exchange:34–42). Pemanggil Play menggunakan control type `live`; bukan bukti bahwa byte video dibawa oleh MQTT.

Dispatch versi MQTT teramati (MQ-exchange:44–49):

- `pver<3`: `publishKeyExchangePver2(...).connect(position)`.
- `pver>=5`: `AppCallDeviceWakeUp(...).connect(position)`.
- Selainnya: `publishKeyExchangePver3(...).connect(position)`.

Jadi nama wrapper `mqExchangePublish` tidak berarti setiap versi langsung mengirim payload key exchange dengan format sama. Method `AppCallDeviceWakeUp` membuat topic dinamis `CID/CID{deviceId}/UID/UID{userId}/call`; bagian placeholder adalah variabel runtime, bukan nilai identifier pengguna yang diekstrak. JSON memuat `call=1`, `mid=requestId`, dan `ctx=createCallInWakeUp(...)`; MQTT QoS1 dan response-topic berakhiran requestId dipasang. Payload dibungkus `MessagePayloadTools.newMessageLoad` dengan identitas tujuan/pengirim dan `DeviceMqInfo.getRandKey`, lalu diteruskan ke publisher internal (MQ-wakeup:3–16). Tidak diketahui dari body ini bagaimana wrapper mengenkripsi/sign payload atau bagaimana response memicu tahap berikutnya.

Koneksi broker, subscription, format `ctx`, format message wrapper, signature, key agreement lengkap, dan penanganan response belum dipetakan. Tidak ada login akun/broker credential method yang dibaca untuk ekstraksi rahasia.

## Hubungan dengan dua repository pembanding

`umar14/v380-android` tidak menyediakan P2P/MQTT atau relay; ia membuka socket TCP LAN dengan protokol legacy1167/301/303. `PyanSofyan/V380Decoder` menyediakan dispatch ke relay TCP8800, sehingga paling dekat dengan sebagian MR relay behavior, tetapi tidak merekonstruksi pemilihan multi-kandidat, `protocolType=2`, pver>=5 MQ/wakeup, atau native fallback yang terlihat pada APK ini. Bukti source dan commit ada pada [comparison.md](comparison.md).

Karena itu, keberhasilan client legacy LAN tidak berarti implementasinya mencakup semua kamera yang didukung APK6.4.90. Device platform, protocolVersion/pver, communicationVersion, dan capability harus menjadi profil eksplisit; jangan memilih protokol hanya berdasarkan nama merek atau versi aplikasi.

## Implementasi yang direkomendasikan dan gap

Implementasi Android pertama sebaiknya menargetkan **LAN legacy yang bisa diverifikasi**: discovery → auth lokal → stream framing → decoder hardware → PTZ. Pisahkan `EndpointResolver` (explicit/cache/dispatch), `SessionAuthenticator`, `SignalingTransport`, `MediaTransport`, dan `MediaDecoder` melalui interface kecil. MQTT signaling harus tetap terpisah dari penerimaan video; native renderer/decoder juga tidak boleh mengendalikan kebijakan cloud.

Jangan menyalin dependency native vendor untuk menganggap P2P terpecahkan. Untuk protocol2/pver>=5, butuhkan bukti tambahan: alur wakeup response dan key exchange, struktur wire TRS/native transmitter, destination UDP/TCP nyata, direct/relay decision, serta crypto dan frame format. Socket API imports atau nama P2P saja tidak memenuhi kebutuhan tersebut. Perekaman traffic tersanitasi pada kamera pengguna dan perangkat uji yang diizinkan dapat menjawab gap, tetapi belum dilakukan pada investigasi statis ini.
