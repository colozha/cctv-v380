# Bukti native statis V380 6.4.90

Analisis ini merupakan **bukti shell statis pelengkap**, bukan hasil dekompilasi fungsi native REA, bukan call graph, dan bukan pengamatan runtime. Input adalah hasil `extract_artifact` REA `ev_f2eaec0ee202bdfaab2d678c927f134eae42d7df9523d26a818a0c13cba2959d`; APK SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Semua pemeriksaan memakai header ELF, dynamic symbols, DT_NEEDED, dan pencarian literal nama API yang terbatas. APK serta library target tidak dijalankan dan tidak dimodifikasi. Tidak ada nilai kredensial, kunci, atau token yang diekstrak.

Bukti terstruktur: [native-symbols.json](evidence/native-symbols.json). Kolektor yang dapat direproduksi: [collect_native_symbols.py](evidence/collect_native_symbols.py). JSON mencatat SHA-256, ukuran, mesin ELF, dependensi, simbol terpilih, virtual address, dan file offset. Alamat di bawah berasal dari varian **arm64-v8a**; untuk simbol yang tercantum, virtual address relatif ELF dan file offset bernilai sama. Nilai tersebut bukan alamat runtime karena ASLR/load bias.

## Identitas dan batas ABI

Terdapat 99 ELF `.so`: 53 AArch64 dalam `arm64-v8a`, 45 ARM dalam `armeabi-v7a`, serta satu Intel 80386 bernama `armeabi-v7a/libsys_misc-x86.so`. Nama direktori tidak boleh dijadikan satu-satunya sumber identitas arsitektur. Tidak ada inventaris ABI x86-64 pada path `lib/*/*.so` yang diekstrak.

| Library arm64-v8a | SHA-256 |
| --- | --- |
| `libhsMediaLibrary.so` | `1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc` |
| `libmideaCodec.so` | `43938dcd760acde7dd31b93ec1c132d2936137f3d9d38d6261e709b33c6717df` |
| `libffavc.so` | `1e214164a6c153f74f25018c1625f3e71d1d5ba601f6592c4dd89385dcc4b0bc` |

## Batas JNI dan pemutaran live

`libhsMediaLibrary.so` mengekspor 78 nama JNI `Java_com_macrovideo_sdk_media_HSMediaLibrary_*`. Ini mengikat API Java `com.macrovideo.sdk.media.HSMediaLibrary` dengan native entry points berikut:

| Native entry point (prefix JNI dipendekkan) | VA / offset | Ukuran |
| --- | --- | --- |
| `HSMediaLibrary_initJavaVM` | `0x953fc` | 380 |
| `HSMediaLibrary_startPlay` | `0x969d0` | 384 |
| `HSMediaLibrary_stopPlay` | `0x96b50` | 296 |
| `HSMediaLibrary_stopPlayAll` | `0x96c78` | 268 |
| `HSMediaLibrary_setPTZXParam` | `0x97640` | 224 |
| `HSMediaLibrary_setZoomParam` | `0x97808` | 240 |
| `HSMediaLibrary_setPTZParam` | `0x978f8` | 332 |
| `HSMediaLibrary_updateLoginHandle` | `0x9bd0c` | 36 |
| `HSMediaLibrary_refreshSession` | `0xa0834` | 200 |
| `HSMediaLibrary_refreshIpPortHandleToken` | `0xa08fc` | 400 |
| `HSMediaLibrary_refreshIpPortHandleTokenUuid` | `0xa0a8c` | 844 |
| `HSMediaLibrary_refreshMqKeyExchangeInfo` | `0xa0dd8` | 564 |
| `HSMediaLibrary_refreshIpPortHandleTokenSession` | `0xa100c` | 468 |

Nama parameter dalam API adalah metadata fungsi, bukan nilai token. Isi nilai runtime tidak diperiksa.

Terdapat pula ekspor C++ `HSMediaManager::startPlay(tagPlayParam*)` (`0x80a50`, 336 byte), `HSLivePlayer::startPlay(tagPlayParam*)` (`0x85350`, 1880 byte), `HSLivePlayer::onRecvFrameData(tagFrameData*)` (`0x87780`, 928 byte), `HSLivePlayer::videoDecodeThreadFunc(int)` (`0x8610c`, 5396 byte), dan `audioDecodeThreadFunc()`. Tiga keluarga bernama `HSLiveDataV1Transmitter`, `HSLiveDataV2Transmitter`, dan `HSLiveDataV3Transmitter` masing-masing menyediakan `startTransmitter`, `dataGetThreadFunc`, `actionCtrlThreadFunc`, serta fungsi pengiriman audio bicara. Ada `parseFrameDataV13` pada V1/V2 dan `parseFrameDataV20` pada V2.

**Observasi:** API dan keluarga implementasi tersebut benar-benar terdapat dalam shipped ELF. **Inferensi:** desain memisahkan lifecycle player, penerimaan media, parsing frame, action/control, dan decode thread; ada dukungan beberapa generasi transport/format frame. **Belum dibuktikan:** pemilihan V1/V2/V3, urutan pemanggilan, packet framing, arti versi 13/20, ukuran struktur `tagPlayParam`/`tagFrameData`, atau format respons kamera. Simbol-simbol ini tidak cukup untuk menulis parser kompatibel.

## TCP/UDP/P2P dan kriptografi

`libhsMediaLibrary.so` mengimpor `socket`, `connect`, `sendto`, dan `recvfrom` dari libc. Ini membuktikan ketersediaan operasi socket native. `sendto`/`recvfrom` saja tidak menetapkan jenis socket, port, UDP vs TCP untuk setiap jalur, atau fungsi yang memakainya.

Ekspor `HSLiveDataBaseTransmitter::callbackMqErrorStartP2p()` berada pada `0x3dd04` (100 byte); ada padanannya pada replay transmitter. Nama ini mendukung hipotesis jalur transisi dari kesalahan MQ ke P2P, tetapi bukan bukti bahwa fallback benar-benar terjadi atau bahwa NAT traversal memakai algoritme tertentu. Pencarian dynamic symbols core tidak menghasilkan API bernama hole-punch/NAT yang cukup untuk merekonstruksi handshake. Batas pencarian ini tidak berarti implementasinya tidak ada: fungsi dapat tersembunyi, stripped, atau berada di Java.

Hubungan linkage kriptografi yang teramati:

- `libhsMediaLibrary.so` mengimpor `media_data_encrypt`, `media_data_decrypt`, dan `media_data_decrypt_pre_2k`. `libaes.so` mengekspor nama yang sama pada `0x3f5c`, `0x3ffc`, dan `0x409c`.
- `libhsMediaLibrary.so` mengimpor `mvt_cipher_start`, `mvt_cipher_process`, dan `mvt_cipher_finish`. `libHSMbedtls.so` mengekspor nama yang sama pada `0xe3f8`, `0xe614`, dan `0xe984`.
- `libhsMediaLibrary.so` mengekspor `iotc_ms_frame_cipher_init` (`0x93578`), `iotc_ms_frame_cipher` (`0x9394c`), `iotc_ms_frame_cipher_end` (`0x93b80`), serta `cipherJsonData` (`0x93e24`, C++ entry point).
- DT_NEEDED mencakup `libaes.so`, `libHSMbedtls.so`, dan `libed25519.so`.

Inventaris simbol ini membuktikan adanya API media/JSON cipher dan dependensi kriptografi. Addendum di bawah memulihkan mode serta cakupan byte pada dua primitive media melalui instruksi. Pemilihan primitive saat negosiasi, key exchange, derivasi kunci, autentikasi pesan, dan hubungan cipher dengan login kamera tetap **belum diketahui**. Jangan menyimpulkan algoritme autentikasi atau mode cipher aktif hanya dari nama library/API.

## Decoding video dan dependency boundary

`libhsMediaLibrary.so` memiliki undefined imports `initMediaDecoder`, `decodeFrameData`, dan `releaseMediaDecoder`. `libmideaCodec.so` mengekspor fungsi dengan nama identik:

| Fungsi di libmideaCodec | VA / offset | Ukuran |
| --- | --- | --- |
| `initMediaDecoder` | `0x23880` | 96 |
| `releaseMediaDecoder` | `0x238e0` | 52 |
| `decodeFrameData` | `0x23930` | 44 |
| `createDecoder(HSMEDIA_CODEC_ID)` | `0x23a38` | 280 |
| `HSFFMPEGH264Decoder::decodeData(tagFrameData*, tagFrameData*)` | `0x28138` | 880 |
| `HSFFMPEGH265Decoder::decodeData(tagFrameData*, tagFrameData*)` | `0x28ba4` | 880 |

`libhsMediaLibrary.so` dan `libmideaCodec.so` sama-sama mendeklarasikan dependensi FFmpeg `libavcodec`, `libavformat`, `libavutil`, `libavfilter`, `libpostproc`, `libswscale`, dan `libswresample`. `libhsMediaLibrary.so` juga membutuhkan `libHYFisheyePano`, `libGLESv3`, `libEGL`, `libhsUtils`, `libfaac`, `libfaad`, dan dependensi sistem. Nama decoder H.264/H.265, dependency graph, serta import/export API mendukung keberadaan jalur decode native FFmpeg; belum membuktikan kapan jalur itu dipilih dibanding MediaCodec.

`libffavc.so` mengekspor `ffavc::DecoderFactory::GetHandle()` (`0x564a8`, 88 byte) dan `Java_org_ffavc_DecoderFactory_GetHandle` (`0x56548`, 4 byte). DT_NEEDED-nya hanya `liblog`, `libm`, `libdl`, dan `libc`. Tidak ada exported JNI bernama `VideoDecoder`. Pemeriksaan literal terarah juga tidak menemukan `com/macrovideo/sdk/media/VideoDecoder` atau `org/ffavc/VideoDecoder` dalam kedua `libhsMediaLibrary.so` dan `libffavc.so`; lihat file `*-bridge-names.json` di evidence. Ini **tidak** membuktikan tidak ada callback/dynamic registration/bridge. Hubungan pemanggilan native langsung ke `VideoDecoder.init/decode/release` belum dapat ditetapkan dari bukti ini.

Perlu menggabungkan temuan Java REA `VideoDecoder` dan callback player dengan bukti ini. Jangan menggambar panah pemanggilan `HSLivePlayer → VideoDecoder` sebagai fakta tanpa xref, disassembly, atau runtime trace.

## Implikasi untuk Android client independen

Pisahkan modul `transport/session`, `frame parser`, `EncodedFrame` queue, dan `decoder`; pilih decoder melalui interface yang menerima frame terkompresi dan metadata codec/timestamp. Simbol transmitter V1/V2/V3 menyarankan adapter kemampuan/generasi transport, tetapi implementasi awal cukup satu generasi yang terverifikasi pada kamera milik pengguna. MediaCodec dapat menjadi decoder utama setelah framing H.264/H.265 diketahui; fallback FFmpeg perlu keputusan terpisah terkait build/ABI/lisensi. PTZ berada pada control interface dan tidak seharusnya tergantung pada decoder.

Keberadaan binary proprietary di APK tidak merupakan izin redistribusi. Client independen sebaiknya merealisasikan kontrak yang telah dibuktikan, tanpa menjadikan library APK yang tidak terdokumentasi sebagai fondasi publik.

## Batas investigasi native

Tidak ada decompiler native REA (Ghidra/IDA) terkonfigurasi dalam investigasi ini. GNU objdump yang tersedia tidak mendukung ARM. Addendum berikut menambahkan disassembly statis terbatas melalui Capstone untuk primitive media decrypt; tetap tidak ada native xref/call graph lengkap atau dekompilasi fungsi. Tidak ada APK/library target yang dieksekusi, tidak ada capture jaringan, dan tidak ada koneksi ke kamera/server. Fakta tentang port, packet header, NAT traversal, autentikasi/handshake, cipher aktif, callback Java, dan pemilihan decoder harus berasal dari temuan Java atau investigasi lanjutan yang sesuai. Helper statis Capstone tersedia terisolasi di `/tmp/v380-static-analysis-tools`; hanya dipakai pada fungsi dan PLT stub yang dicatat dalam addendum berikut.


## Addendum: korelasi primitive decrypt dengan V380Decoder

Analisis tambahan berikut memakai **Capstone 5.0.6**, statis dan terbatas pada `media_data_decrypt`, `media_data_decrypt_pre_2k`, direct callee `aes128_ecb_encrypt`, `AES_ecb_encrypt`, serta empat PLT stub. Ini tetap bukti shell pelengkap, bukan fungsi analisis native REA. Tidak ada target yang dieksekusi. Hanya instruction bytes dan relocation/symbol metadata yang dibaca; isi literal/data/kunci yang dirujuk ADR/ADRP tidak dereferensi.

Input: `lib/arm64-v8a/libaes.so`, SHA-256 `f1e4eae24df2683866385e416f6d324000460711fad124962eeed38178ff9020`. Bukti semua instruksi beserta byte/VA/file offset dan relocation: [native-media-decrypt-disassembly.json](evidence/native-media-decrypt-disassembly.json). Kolektor: [disassemble_media_decrypt.py](evidence/disassemble_media_decrypt.py).

Untuk **panjang positif** dan dengan penamaan argumen inferensial `(buffer, length, keyPointer, flag)`, semantik yang diturunkan dari instruksi:

```text
media_data_decrypt(buffer, length, keyPointer, flag):
    if flag != 0:
        decrypt_ecb_in_place(buffer, floor(length/16)*16, keyPointer)
    else:
        offset = 0
        while length - offset >= 64:
            decrypt_ecb_in_place(buffer + offset, 64, keyPointer)
            offset += 80
    return length

media_data_decrypt_pre_2k(buffer, length, keyPointer, flag):
    n = floor(length/16)*16
    if flag == 0:
        n = min(n, 2048)
    decrypt_ecb_in_place(buffer, n, keyPointer)
    return length
```

Pada sparse loop, kegagalan callee menyebabkan loop berhenti; kedua wrapper tetap mengembalikan panjang input. Pseudocode di atas menyajikan jalur sukses. Byte setelah daerah yang diproses tetap tidak diproses oleh wrapper: sparse loop melewati 16 byte setiap stride dan tidak memproses fragmen terakhir yang kurang dari 64 byte. Jalur contiguous tidak memproses sisa panjang modulo 16.

| Klaim korelasi | Bukti instruksi | Kesimpulan |
| --- | --- | --- |
| Decrypt 64 byte, stride 80 byte | `0x405c/0x4060`: dua argumen panjang `0x40`; `0x4078`: remaining dikurangi `0x50`; `0x407c`: pointer ditambah `0x50`; `0x4080`: syarat sisa `>=0x40` | **Terkonfirmasi** untuk `media_data_decrypt`, `flag==0` |
| Decrypt semua blok lengkap 16 byte | `0x4020`: modulo melalui mask `0xf`; `0x4034`: panjang dikurangi remainder; `0x4044`: panggil callee | **Terkonfirmasi** untuk `media_data_decrypt`, `flag!=0` |
| Varian pre2k cap 2048 | `0x40b4`: `0x800`; `0x40bc/0x40c8`: cap bila panjang >2048; `0x40dc/0x40e8`: pilih cap hanya ketika `flag==0` | **Terkonfirmasi**, cap bersyarat, bukan semua media |
| Pemakaian AES-128 ECB decrypt | Flow mode0 + decrypt-key setup 128-bit + independent block loop + PLT relocation di bawah | **Terkonfirmasi untuk primitive ini**, bukan seluruh protokol/semua stream |

Bukti mode cipher tidak hanya berasal dari nama fungsi:

1. Wrapper mengisi argumen mode `w4=0` pada `0x403c`, `0x4068`, dan `0x40f0`, serta memanggil PLT `0x41d0`.
2. PLT `0x41d0` memuat GOT slot `0x14410`; `R_AARCH64_JUMP_SLOT` mengikat slot itu ke `aes128_ecb_encrypt` (`0x3b68`).
3. Callee memilih cabang decrypt karena mode bukan 1 (`0x3bb4/0x3bb8`, kemudian `0x3d14/0x3d28`); mengisi ukuran kunci `w1=0x80` (=128 bit) di `0x3d10`; PLT `0x4170` terikat pada `private_AES_set_decrypt_key` lewat GOT `0x143e0`.
4. Di `0x3cd8` callee mengisi mode0, memakai input block dan output temporer, lalu memanggil `AES_ecb_encrypt` melalui PLT `0x41a0` / GOT `0x143f8` (`0x3ce8`). `AES_ecb_encrypt` (`0x3a70`) pada mode bukan 1 bercabang ke PLT `0x4190`, yang relocation-nya menuju `AES_decrypt` (`0x3744`).
5. Callee menyalin kembali 16 byte output, menambah input pointer 16 byte (`0x3cfc`), dan mengulang tanpa input IV atau penggabungan hasil block sebelumnya. Ini mendukung ECB block independen secara struktural.

**Perbandingan dengan pola C# V380Decoder:** pola 64/80 dan blok 16 penuh cocok pada level primitive APK ini; cap 2048 juga cocok dengan pengecualian bahwa flag nonzero tidak dicap. Penamaan *video* untuk flag0 dan *audio* untuk flag nonzero masih memerlukan bukti pemanggil yang memasok flag. Hasil ini tidak membuktikan bahwa kamera pengguna, semua versi firmware, atau jalur V3/MQ memakai primitive legacy yang sama. Pemilihan cipher/primitive oleh native caller, derivasi kunci, framing sebelum decrypt, dan autentikasi pesan tetap belum direkonstruksi. Tidak ada nilai kunci yang dibaca.
