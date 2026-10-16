# Indeks bukti V380 6.4.90

APK SHA-256: `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. REA 6.4.0, JADX headless 0.7.1.

Berkas REA menyimpan Evidence kanonis langsung atau di dalam envelope `data`; raw_result dan limitations dipertahankan. Excerpt tersanitasi memiliki ID sumber tetapi **bukan** Evidence kanonis; jangan menggunakannya untuk validasi digest kanonis. Bukti native Capstone/readelf merupakan bukti pelengkap statis. Tidak ada hasil runtime.

## Evidence REA dan excerpt turunan

| Berkas | Jenis / operasi | Selector | Evidence ID sumber | SHA-256 berkas |
|---|---|---|---|---|
| [class-HSMediaLibrary.json](evidence/class-HSMediaLibrary.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.media.HSMediaLibrary` | `ev_d2d3f8f1a7227031378f9345b4e881b72f396964ca87d49dfcf717cbb081a86f` | `b9cf0154b983390f430f2aa6974afb259989f7f28e2a5f20df7a027a804df333` |
| [class-HSMediaPlayer.json](evidence/class-HSMediaPlayer.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.media.HSMediaPlayer` | `ev_75f2f95de0503687e83c2cfa2a61b7e584993cc97681656c8b8bc7c0b82d6653` | `4751a066fb05f81b30a3f98f900b5755601a61645f48ed55dfc6cacbf463d085` |
| [class-LoginHelper.json](evidence/class-LoginHelper.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.media.LoginHelper` | `ev_b5c217059683d175e94e818e415f8bd19c94bedba08eedcde51cf118f6a9dea2` | `38c522139bd3de4a4b667333a6aaa53399ecb62223ef0558d7bda05111707719` |
| [class-Protocol.json](evidence/class-Protocol.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.defines.Protocol` | `ev_726e1b373b5f033ea2891d852207a2cad164898ffec4adb1231263601eeb5b9c` | `81d310757375b8dab1dfe84e266a939ada0b51008343f83b9c3e705a1aee88f9` |
| [class-VideoDecoder.json](evidence/class-VideoDecoder.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.media.VideoDecoder` | `ev_8fd4dd26a87e9358054cc543992216d55d92fb0655802ddc5d37a1f38378bfb6` | `7fe6ed9ce04f8b0a90e92181df20d0f5f563ab3e372d80179cff58cd1c5c72ac` |
| [classes-macrovideo.json](evidence/classes-macrovideo.json) | REA canonical: `search_android_classes` | `` | `ev_342ceebaa9b21d2e2cae96721ebf8839e4fe9c3140c61064fde3972cd261fe15` | `009e00cacb99d05e9f3ce41c320ec5753d9b708a0d17dc04a6bf64ace628ffc3` |
| [discovery-DeviceLanUDPScanner-class.json](evidence/discovery-DeviceLanUDPScanner-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner` | `ev_3c63d257db4b427ae80cc67ea0bbe2bd1e45f9b059f6094278913b3408aec5f3` | `040c02eeadfb7bc045cb98dd7ac2d4803249788135c6bce6f569e624f1516a56` |
| [discovery-Functions-class.json](evidence/discovery-Functions-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.tools.Functions` | `ev_b2e1b92a3c76151f098f981cf3f8919a664c85b8f6263f35bd01b2376d68aca5` | `027bb110decff3782b7907bac3f01a40417894edf4e7ebfc6284b96b3cf5b802` |
| [discovery-ParseDevice.sanitized.json](evidence/discovery-ParseDevice.sanitized.json) | Derived sanitized / noncanonical: `inspect_android_method` | `` | `ev_1db6ba89c5f7c7c9c5ef84e97fbf3e570950d96132bb7eb2cc01eb946552780b` | `9ef535a7c83021aeb887c8643e471c012da59bbb5ee90c2afe5d66112be4c681` |
| [discovery-Receiver-class.json](evidence/discovery-Receiver-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner.BroadcastReceiverThread` | `ev_bc17f0a310710d02312880d6a5d8026c00badaa82faae4d034a417b99b056ef4` | `c9e7300cd0d2076f6dde5161c44960f0014fc4bd6edf605a4b61b549a66df1aa` |
| [discovery-Receiver-run.sanitized.json](evidence/discovery-Receiver-run.sanitized.json) | Derived sanitized / noncanonical: `inspect_android_method` | `` | `ev_58da43d3c464389f6d5cd3ab537e9075116ecb2df136651f276a3ebe6aa59d75` | `c2d256391a34c789aecb7a94ef3e003f01647df12447cd01e4bb590d8724ea5e` |
| [discovery-Sender-class-dot.json](evidence/discovery-Sender-class-dot.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner.BroadcastSenderThread` | `ev_f51cdbbb383e4e595305a84b8c5763439324047b19ab344bc2879fb9518f6c62` | `b405f055c87cbbe53d49e66822d3bc8d5b9ce59954c1eeaac95c2fffff80a05e` |
| [discovery-Sender-run.json](evidence/discovery-Sender-run.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner.BroadcastSenderThread run(): void` | `ev_ff93ad2c5f12d48352a075edfd5278c02d63a2955e00d5c70d4a2f4fe9fe7aab` | `267d4022f7ce9c76b56344334882f7555dc5dc9c116fb88ff7f360c33b5563b1` |
| [discovery-changeProtocol.json](evidence/discovery-changeProtocol.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner changeDevLanSearchProtocol(com.macrovideo.sdk.GlobalConfiguration$DeviceProtocolType): void` | `ev_d648d10a75955df73d5b8cecbd68f00f3e8b48d4a9547b8c64f679801e8351d7` | `0a00cf999c3ea014a62ec961bc6c2c4e1cad7de65bd7838461bb4bef88518907` |
| [discovery-clinit.json](evidence/discovery-clinit.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner <clinit>(): void` | `ev_8978ac5c2d85605e57073d72bc93fb5e7a5e4197d777475ff1dfd23883d3c345` | `a3b657849a0aba091f128172af4342118a3fa91dd9c86d76e38fd6d08480a210` |
| [discovery-startSearching.json](evidence/discovery-startSearching.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.DeviceLanUDPScanner startSearching(int, int[]): boolean` | `ev_cc8db726946c1f478bed406d0406e08e875b5818be4ab72b861bf30b93621906` | `71b88fd1297ef9a225793e1aa38e9755e836b7f6940fd95be140ae043eb216dd` |
| [extraction.json](evidence/extraction.json) | REA canonical: `extract_artifact` | `` | `ev_f2eaec0ee202bdfaab2d678c927f134eae42d7df9523d26a818a0c13cba2959d` | `996a19e4e8d272c8c85e9805301220d97fec00f802cf11564a649f6a26421e8a` |
| [method-Functions-IntToBytes.json](evidence/method-Functions-IntToBytes.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.Functions IntToBytes(long, byte[], int): void` | `ev_010e1da1f42c19b07685b0b7d16f0d29de7d748501c26ed1eecbe6b93764a9ff` | `2f29e2bf323e0e2dfabbf72edd79c695c79abf81223ef6e8a4bf3e7b7cf446cf` |
| [method-Functions-encrypt.json](evidence/method-Functions-encrypt.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.Functions encrypt(byte[], byte[]): byte[]` | `ev_97e2d090fbe615c329e53eb048ae86f711ae4a375e6b472a7f2f782938c87c5d` | `6334057180cd471f920fa4c07e04e145101e6613741521ba03615a6ab74f05eb` |
| [method-HSMediaLibrary-clinit.json](evidence/method-HSMediaLibrary-clinit.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaLibrary <clinit>(): void` | `ev_a62d090583a4e2d7d0af1d2c69784f83d92feeaa5319048a31e6031f7b11522e` | `850407bb9525b334871200684aa7d0cd87309e0b2128fe4a54618eb69bc757be` |
| [method-HSMediaPlayer-startPlayEX2.json](evidence/method-HSMediaPlayer-startPlayEX2.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaPlayer startPlayEX2(int, int, int, int, boolean, int, boolean, com.macrovideo.sdk.media.LoginHandle, int, int, boolean, boolean, boolean, boolean): boolean` | `ev_3ba10a3d2f85e6a919d9ae1ec8a2d1a6d384771c637e3a6ba1d7571be82b4f30` | `2c55f7ff8c751296327ca9644230d4cef17969ad119767d42c4ef977c5945bfe` |
| [method-LoginHelper-a2.json](evidence/method-LoginHelper-a2.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.LoginHelper a(android.content.Context, java.lang.String, int, java.lang.String, java.lang.String, int, int, int, int, boolean): com.macrovideo.sdk.media.LoginHandle` | `ev_3b9378916b495d705cdca37cc7c011724820a6b05c4c8960dcce758840c29672` | `42e6dda67ae1c08667f9478d2fb74107c3b7fb135dcb7cbbe0c886c81ffaf2ea` |
| [method-LoginHelper-b.json](evidence/method-LoginHelper-b.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.LoginHelper b(java.lang.String, java.lang.String, java.lang.String, int, int): com.macrovideo.sdk.media.LoginHandle` | `ev_68a134a2ed8a9838f2f0b2679eaaee30301710cd9e2a9ac25b30248bc6809228` | `de9348cb84798eb24e733fad5f41d8a45c2f3aedf7fe04fd9f6505e093fe6cce` |
| [method-LoginHelper-keyExchangeByLanSync.json](evidence/method-LoginHelper-keyExchangeByLanSync.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.LoginHelper keyExchangeByLanSync(java.lang.String, int, java.lang.String): com.macrovideo.sdk.objects.KeyExchangeLanResult` | `ev_c43fdbdc570a5a9338d236fda296af170fd08058377511f101c14137adcba67a` | `9b08b06594ad22e18854cfaa8158249a6ae12c09d2ff386c76d51892d05aa760` |
| [method-LoginHelper-lanLogin.json](evidence/method-LoginHelper-lanLogin.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.LoginHelper lanLogin(com.macrovideo.sdk.objects.DeviceInfo, int): com.macrovideo.sdk.media.LoginHandle` | `ev_418d8e7633868095c1ac727236a706b0071c9308bee570953212ca4d55c18e30` | `e72c09ff2cdec89d981794e9e0c443e1dc066840de70439a1ba8f39d393c400c` |
| [method-Protocol-createCommonJsonRequestBuffer.json](evidence/method-Protocol-createCommonJsonRequestBuffer.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.defines.Protocol createCommonJsonRequestBuffer(boolean, int, int, int, short, byte, byte, byte, int, org.json.JSONObject, byte[]): byte[]` | `ev_ba28b4932b97302c9ad945b9973f73cb1ed8e467ee18f26b05546603ba0c0de6` | `7eb22cc785343e662d29308f1219eb9a5f4662c2011f7cd378d7ac77300a5afb` |
| [method-Protocol-initCommonDataSendBuffer.json](evidence/method-Protocol-initCommonDataSendBuffer.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.defines.Protocol initCommonDataSendBuffer(boolean, byte[], int, short, byte, byte, byte, byte[], int): int` | `ev_8ca8c9235b62f533ac86d8f0e2fe33180528cd7194e60a4a270cfae6014b2947` | `c6c9e396ea92daa221995c1da6e96a5a624000098aef8eadde475f3677b013ca` |
| [method-Protocol-parseResponseHeader.json](evidence/method-Protocol-parseResponseHeader.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.defines.Protocol parseResponseHeader(byte[]): com.macrovideo.sdk.objects.ResponseHeader` | `ev_7c0eb5cb7b6fb263690eb382b5e5db87156782e5d18c5ac1a34466ced4586e44` | `d631037715d9991a5c394b7b5b2c464c996bfac3dc9a71877aa2b48a9987b1fe` |
| [method-VideoDecoder-b.json](evidence/method-VideoDecoder-b.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.VideoDecoder b(byte[], int, byte[]): void` | `ev_e740bd7857c9d31c023a0a0206fe3a713e999c2a7be0619ace043279c772b4c2` | `710bb8c26dab211979754c5ecdb960af3a166ef30465e336752877a50263695f` |
| [method-VideoDecoder-decode.json](evidence/method-VideoDecoder-decode.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.VideoDecoder decode(byte[], int, byte[]): com.macrovideo.sdk.media.VideoDecoder$DecodeResultEnum` | `ev_fd34a09723358ce261e8306d1d89bc1ed97ca5ee1f0aa4e2ab559b8cf3be38da` | `7442117add90938fd2f515d54fb970cfd6456573d42da495afa74e1bf8925345` |
| [method-VideoDecoder-init.json](evidence/method-VideoDecoder-init.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.VideoDecoder init(int, int, android.view.Surface, int): boolean` | `ev_7e092e42f3b7f5be906a202a96c4a3101b9ecb06f7809710c286ff00fc085960` | `61508618bdcea3b53a0cb1ed5f668bce0a6bc007ccce18ce42b4d318c9835c1e` |
| [p2p-AppCallDeviceWakeUp.json](evidence/p2p-AppCallDeviceWakeUp.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.org.eclipse.paho.mqttv5.util.MqttHelper AppCallDeviceWakeUp(java.lang.String, com.macrovideo.sdk.objects.CallMqServiceHttpsResult, int, int, int): com.macrovideo.sdk.org.eclipse.paho.mqttv5.util.MqttHelper` | `ev_a28cee8f91ee88d58f4a2dda5c03c33633926017fe898e93f1204709c3b941d4` | `9becb8a0606d6e2f4fdb5a3afc1c4f675d720ec351fac04e43f1e24c802bba93` |
| [p2p-Functions-class.json](evidence/p2p-Functions-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.tools.Functions` | `ev_8aba393d7c39bc36b1038d01cd668e1a932bf2cefb4d70fc957728129eeb2540` | `813419d93bbca039ffc83a52d1b31ae12aa04730d6ef3f8c89ecf6d115a433bc` |
| [p2p-MqttHelper-class.json](evidence/p2p-MqttHelper-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.org.eclipse.paho.mqttv5.util.MqttHelper` | `ev_f96b70d1c391d9b6bd4a994029181617d67a28a38698eea9c6651ab077840cf9` | `a1b2357d39fdd4013bcaa8ff624f8a0846ac97b397f5d828e4399724fade6c53` |
| [p2p-connectToMRServer2.json](evidence/p2p-connectToMRServer2.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.Functions connectToMRServer2(java.lang.String, int, int, int, int, boolean, boolean, int, int[]): com.macrovideo.sdk.objects.ConnectResult` | `ev_4db0b574a6ecb93da98d92e15b27e79c387e4018da419466f20aafdd6320e103` | `cc559da69bb5dfe00132e56a6fb1236481494f8c7df84ebe481faf6317da30af` |
| [p2p-connectToServer.json](evidence/p2p-connectToServer.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.tools.Functions connectToServer(java.lang.String, int, int, int[]): java.net.Socket` | `ev_4a6640d1b5d37600bae5613ae30d2a2af2ec6c1a8a8f8eeb95d562271e4e8367` | `6f000601d19feba8799e18b47e5b0c7983dc94d37bf6f199be3d9ed5983b3c57` |
| [p2p-mqExchangePublish.json](evidence/p2p-mqExchangePublish.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.org.eclipse.paho.mqttv5.util.MqttHelper mqExchangePublish(int, java.lang.String, com.macrovideo.sdk.objects.CallMqServiceHttpsResult, int, java.lang.String): java.lang.Thread` | `ev_5ad0309ee5dc70d97db56ba3acf4f20cfe706c2348ca3c3ec4b37d84207e0113` | `06ea7cc3f958d7273b633957fd7376625e39b8c26bf1d8a16041dba598a2c3cd` |
| [package.json](evidence/package.json) | REA canonical: `inspect_android_package` | `` | `ev_5a4a59a232612f68272fbb910c1276eae7dc2e242fad9343db209f92b47b4a6e` | `57458a516ceb817dd032625a1a9651782b8d302cc5372370e1b3d70dec746ca7` |
| [ptz-PTZXController-class.json](evidence/ptz-PTZXController-class.json) | REA canonical: `inspect_android_class` | `com.macrovideo.sdk.media.PTZXController` | `ev_1d02854866895bc5f54aa2c4fbca9da1f4c7b46c4be4dcbfa77084dacbf5dc1d` | `a12d1f4f02767ab670b9c25ff4a72cdca319dcf04ce6ec1d3987952318eb46da` |
| [ptz-SendZoomAction.json](evidence/ptz-SendZoomAction.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaPlayer SendZoomAction(int, int): boolean` | `ev_7f27781be137721232091ab793e92fe8ef8d755ef5c11f0382b380faa9ca78d9` | `564d41c8d1f897372d47d5f8662d1fd402c876b8f8fd498e69bebf9ef89fb5a7` |
| [ptz-callPTZXLocation.json](evidence/ptz-callPTZXLocation.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaPlayer callPTZXLocation(int, int): boolean` | `ev_d06f4ececb676ff51796c85250956dc8fe9eae01eab0dcc217e4df2c766074d8` | `0f4146ee1bb743f5c1d6c4463628080afaacdfe12113050063a45028314d2030` |
| [ptz-setPTZAction.json](evidence/ptz-setPTZAction.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaPlayer setPTZAction(int, boolean, boolean, boolean, boolean, int): boolean` | `ev_553a3451e530d1a001103d08f8429d4f665d9adc28cb40e4a19f2a652b76d1d4` | `336499db76db7ad4b7095018979d98bf99ed9828133b8044d10b5048591ff046` |
| [ptz-setPTZXLocation.json](evidence/ptz-setPTZXLocation.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.HSMediaPlayer setPTZXLocation(int, int, com.macrovideo.sdk.media.LoginHandle, int): int` | `ev_f0e5f6213f19a8d65a55e358485e6f7465336e57588af1b3600cfc2338121fc3` | `4d80fb7fbfafd62fe2d4907cfeccc35a745192e288666c1ab8b63b6b73c0dc3e` |
| [ptz-setPTZXPoint.json](evidence/ptz-setPTZXPoint.json) | REA canonical: `inspect_android_method` | `com.macrovideo.sdk.media.PTZXController setPTZXPoint(com.macrovideo.sdk.media.LoginHandle, int, int, int, int): int` | `ev_864228b5a8ef62dc2348c061a61434454cf9d2c134bc6c65cfe12e724209076c` | `4c0b795b56134579e1340e75dd38c7aacbccafa2b0f82f306a6409069d3745ce` |
| [refs-LoginHelper-keyExchangeByLanSync.json](evidence/refs-LoginHelper-keyExchangeByLanSync.json) | REA canonical: `trace_android_references` | `com.macrovideo.sdk.media.LoginHelper` | `ev_9755ac39a945af3e4bded0d61cbb1d5bd3b8e78a7252bfc45d70df3ddfa0d0ec` | `801151309f011900fd70fd5a928d6445b7d9dbf669e5c6c4a2e9afe0dd59917a` |

## Bukti pelengkap

| Berkas | SHA-256 |
|---|---|
| [libffavc-bridge-names.json](evidence/libffavc-bridge-names.json) | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |
| [libhsMediaLibrary-bridge-names.json](evidence/libhsMediaLibrary-bridge-names.json) | `635f821243fba5d958976f9cdf17a908aec1d5f8f767f641caa9c9900dd5f5a5` |
| [native-media-decrypt-disassembly.json](evidence/native-media-decrypt-disassembly.json) | `b28928335febedffd4aad31c1c03a59430e42ab44b8c87803aef6e5b8c1b6a24` |
| [native-symbols.json](evidence/native-symbols.json) | `8981b64ed9d60a0e940e4f93dba85bda2c22dc8bf82324b4933deadfc5123a18` |

## Probe yang belum menghasilkan body atau ditolak

- [discovery-Sender-class.json](evidence/discovery-Sender-class.json): `invalid_request`; selector nested class memakai `$` ditolak dan diperbaiki menjadi dot pada probe berikutnya.

`discovery-clinit.json` memiliki Evidence inventaris tetapi body initializer tidak tersedia; payload dibuktikan melalui `changeDevLanSearchProtocol`, bukan ditebak dari initializer.

## Reproduksi terbatas

Contoh command read-only terhadap APK:

```bash
rea inspect-android-package /home/husain/Downloads/v380-6-4-90.apk --format json
REA_JADX_HEAP_MIB=2048 rea inspect-android-method /home/husain/Downloads/v380-6-4-90.apk com.macrovideo.sdk.media.LoginHelper lanLogin --format json
```

Gunakan JAR backend JADX yang telah tersedia. Selector/overload masing-masing tersimpan pada parameters Evidence. Ekstraksi REA memakai direktori temporer; analisis native dapat direproduksi dengan `collect_native_symbols.py` dan `disassemble_media_decrypt.py` setelah path hasil ekstraksi tersedia. Hash library dan instruksi yang dianalisis dipertahankan dalam JSON pelengkap.

## Bukti tambahan tahap implementasi


Bukti berikut berasal dari investigasi gerbang protokol. Status profil tetap unverified; lihat [protocol-gate.md](protocol-gate.md).

| Berkas | Jenis / Evidence ID | SHA-256 berkas |
|---|---|---|
| [gate-JNI-field-names.json](evidence/gate-JNI-field-names.json) | supplementary-bounded-JNI-field-metadata | `ae3602bbfaed0340b3bd297f9569231e7a88b07af98a5dc0b2e59d8728305c77` |
| [gate-LoginHelper-b.dex.txt](evidence/gate-LoginHelper-b.dex.txt) | Collector / instruksi statis pelengkap | `6f4ceab4f97375d6fcbeeec38e03d112f2862d855e4f544252cd1623f3a612f5` |
| [gate-LoginHelper-clinit.sanitized.json](evidence/gate-LoginHelper-clinit.sanitized.json) | Derived sanitized / noncanonical, sumber `ev_4b38c7e12b64ddb4c49a0b4d19a6a9568a9f7b5081d22155a49546c04aa1820c` | `516ee9b2161b3f84977dd4d0c370e3eccc02ce50eafdcaa5f93f560c8c124365` |
| [gate-changeDevLoginProtocol.json](evidence/gate-changeDevLoginProtocol.json) | REA canonical `ev_800279025fab79aabb0f89f7ba650b875921d6df4ed5e87b9ef20d3ac848354a` | `1ffc77071014b8175567ffb2768fa2788722b8ee2c5daf6358bfba0c91223086` |
| [gate-changeDevPwd.json](evidence/gate-changeDevPwd.json) | REA canonical `ev_8506a106a74fd43a557ba148c384453e9315e614ffdca2f411e0b0ba62199467` | `66332f8964fc88051526c5a2fa98e0fa1dcdc1924b178fdf5c33bff58448437b` |
| [gate-collect-native.py](evidence/gate-collect-native.py) | Collector / instruksi statis pelengkap | `24652c9a6115c5b80c4ff794365cbc9a081e530ae747534b216ddf90b7c5c190` |
| [gate-dex-instructions.java](evidence/gate-dex-instructions.java) | Collector / instruksi statis pelengkap | `52b835b607f4a2f7077a541dc501b9d4ea6a40c27a97d212465aa1efb1a992fc` |
| [gate-frame-type-switch.json](evidence/gate-frame-type-switch.json) | supplementary-bounded-static-switch-table | `ad2bec4b8a6e3cc9cfa33ef6be309e05848cc3f5f3893448394ead14bdadbc81` |
| [gate-getStrTimeWhenLANLogin.json](evidence/gate-getStrTimeWhenLANLogin.json) | REA canonical `ev_ba1a281f0251e17f557836454d3c04863142783910dbe6c551e9dd3ccb2cbf80` | `f68fe61cc066ae3271983cd21c438235286fffb3217c07ffd38054be92be3064` |
| [gate-md5.json](evidence/gate-md5.json) | REA canonical `ev_fb4d6967bf91246dab48cf0f37178106ff6d13f0bd3ef134f45aceedb3631d39` | `ad79162d39dec108fc83b3047bf4040bf5c668502ad1f118372cf29d26ab97ec` |
| [gate-md5_16.json](evidence/gate-md5_16.json) | REA canonical `ev_860b10bfb6845b2ec5c968c38918bf4a4cf927c404747ead4e4ded701dd9b8bd` | `dd88eda3d1d9ceaa2f97d7f039cd953f1ede5595fd1d5f5014602c023b9a19d6` |
| [gate-native.json](evidence/gate-native.json) | supplementary-bounded-static-disassembly | `68f80f8c0031aa2c42e69a2a364427be7e1108f5fe5b1816c1adb1888f744654` |
| [gate-provenance.json](evidence/gate-provenance.json) | supplementary-gate-artifact-provenance | `63f0bf033e4f324296b8eb59b292defca3f6adf075ae1ceecc715b472a9e1153` |
| [gate-stream-prefixes.json](evidence/gate-stream-prefixes.json) | supplementary-bounded-protocol-constant-read | `e8eca6edc63f1d7a506d32ed43dd9fdd7ffbdbc2c02131e3007f42d9241de0dc` |

## Bukti adapter eksperimental dan AP (14 Oktober2026)

REA/JADX, instruksi DEX dan disassembly statis; bukan capture kamera. Berkas tersanitasi adalah derivatif noncanonical dengan source Evidence ID bila tersedia. Spesifikasi: [AP](ap-provisioning.md), [IoT](iot-lan-protocol.md), [Legacy](legacy-wire-spec.md).

| Berkas | SHA-256 |
|---|---|
| [ap-class-com_macrovideo_sdk_objects_DeviceAsSearch.sanitized.json](evidence/ap-class-com_macrovideo_sdk_objects_DeviceAsSearch.sanitized.json) | `b28578d48557606b815487939b334d1a6a0220d633a9c0b88ff60016bde69a5e` |
| [ap-class-com_macrovideo_sdk_setting_DeviceConfigSetting.sanitized.json](evidence/ap-class-com_macrovideo_sdk_setting_DeviceConfigSetting.sanitized.json) | `8dd3157c8178aad329936906f9ea875080373e20974ec9765ebbd501cd9148b4` |
| [ap-class-com_macrovideo_sdk_setting_DeviceNetworkSetting.sanitized.json](evidence/ap-class-com_macrovideo_sdk_setting_DeviceNetworkSetting.sanitized.json) | `1227a34e10e9d153733cf08591ed2d87cd322e8e44bdd79f28d01c66da17f1a5` |
| [ap-class-com_macrovideo_sdk_smartlink_IoTManagerNative.sanitized.json](evidence/ap-class-com_macrovideo_sdk_smartlink_IoTManagerNative.sanitized.json) | `19dd01d8158b0b914dda5e346c707576c70fde4409a164cf68a973f253f4fda2` |
| [ap-class-com_macrovideo_v380_fragments_AddDeviceApFragment.sanitized.json](evidence/ap-class-com_macrovideo_v380_fragments_AddDeviceApFragment.sanitized.json) | `2764ff2cad0255f69abca72665fa047bd6e14bf942d676eb04dfa6198b273174` |
| [ap-class-com_macrovideo_v380_fragments_AddDeviceSetPwdFragment.sanitized.json](evidence/ap-class-com_macrovideo_v380_fragments_AddDeviceSetPwdFragment.sanitized.json) | `832cf01696da61c3438bee4d9f247b466d3760de09bafdbd4c2e8c88069e69ad` |
| [ap-class-com_macrovideo_v380_fragments_LanDeviceSetPasswordFragment.sanitized.json](evidence/ap-class-com_macrovideo_v380_fragments_LanDeviceSetPasswordFragment.sanitized.json) | `4a4d653bf145c03fcc0ed57342f1d108c3ffcee8b9c578130d09f9a76a7c7baf` |
| [ap-classes-AddDeviceApFragment.sanitized.json](evidence/ap-classes-AddDeviceApFragment.sanitized.json) | `29caf3579ee25805800bfaed2b7731320ef6347752ff8754fc95a1642a334d2f` |
| [ap-classes-DeviceNetworkSetting.sanitized.json](evidence/ap-classes-DeviceNetworkSetting.sanitized.json) | `de45916e1112d651927b26675c368f4d2c58b2f03f0408abc3ee33e02294981b` |
| [ap-classes-IoTManagerNative.sanitized.json](evidence/ap-classes-IoTManagerNative.sanitized.json) | `4768121eb9d9fc19988e38d97d6091d09d5583d2de93492080cb565898784e76` |
| [ap-classes-QRContentUtils.sanitized.json](evidence/ap-classes-QRContentUtils.sanitized.json) | `2896c804f5c1c95b0279abfbf0dcf9822fed39b969857a1d6332c4197a6f0f43` |
| [ap-defines-platform.dex.txt](evidence/ap-defines-platform.dex.txt) | `be091a9befb4c88aca75d6c2d03a1397b2ebbce6430e5315350ccad5510ac60a` |
| [ap-dex-instructions.java](evidence/ap-dex-instructions.java) | `7db057bd6cfbe5ac7ce7a6c7773d3e1a4a0665f8455dab04c269e49049f8f841` |
| [ap-functions-version.dex.txt](evidence/ap-functions-version.dex.txt) | `d654f1057e3855b8925d8cd7f5d5e073e03b2747d1670fe1cf4c068b7cdece72` |
| [ap-method-com_macrovideo_sdk_defines_Protocol-createBindInfoJsonObj.sanitized.json](evidence/ap-method-com_macrovideo_sdk_defines_Protocol-createBindInfoJsonObj.sanitized.json) | `d34c267a43209dcaba8b9aeccac5b28dbecb18498b5d3cb9b18c0449330e0926` |
| [ap-method-com_macrovideo_sdk_media_LoginHelper-changeLanPassSync.sanitized.json](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-changeLanPassSync.sanitized.json) | `facba2498376247ec80e8b83061c86415bdc113efd1e26ef62ac252521802ff0` |
| [ap-method-com_macrovideo_sdk_media_LoginHelper-getLoginHandleByAP.sanitized.json](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-getLoginHandleByAP.sanitized.json) | `4633c8f90d32dac8090a621cd534adafa585e3c069f623129e9833a61ee93cc5` |
| [ap-method-com_macrovideo_sdk_media_LoginHelper-requestBindIotDeviceByAP.sanitized.json](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-requestBindIotDeviceByAP.sanitized.json) | `3410638dd20529a4897519e242c63c1ea532404895ebf526b3cec2133a88a1d3` |
| [ap-method-com_macrovideo_sdk_media_LoginHelper-resetLanPassSync.sanitized.json](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-resetLanPassSync.sanitized.json) | `d76eec6981b6a07adf8fbb55f73e912f48c167323c9d9d522f30a6a5a7f956c9` |
| [ap-method-com_macrovideo_sdk_objects_DeviceAsSearch-_init_.sanitized.json](evidence/ap-method-com_macrovideo_sdk_objects_DeviceAsSearch-_init_.sanitized.json) | `27ddb604e1e0d675821b45f14ccd411a92d8760b4c348aabbbabfded7af3ff93` |
| [ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-b-overload0.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-b-overload0.sanitized.json) | `02aeaa5689508abe24cebaf012cb28944d6ebf7e518d77f1de63a520fe916e64` |
| [ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-b-overload1.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-b-overload1.sanitized.json) | `ba7eb70b2612c1269eb6c4e21fc37673f8c9947fbcf6766178218401ceb36a28` |
| [ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-createActionJsonObj-overload0.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-createActionJsonObj-overload0.sanitized.json) | `7ac0cfd2f55efe434dc9a18d36e8c90498cce77986381f550c4846ffe6553d43` |
| [ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-startActionNewPlatform-overload0.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-startActionNewPlatform-overload0.sanitized.json) | `2c33dd30dff02cf98e4d9ba6ba20682565f63e6550f9d047d3dbc40e34be6538` |
| [ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-a.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-a.sanitized.json) | `fcd32e9f3c807b895266e19a73e60a320e557487e7257ff6d993bb081818b408` |
| [ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-setNetworkConfig.sanitized.json](evidence/ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-setNetworkConfig.sanitized.json) | `2578f461ab129f6296951266e94d833aeb9e633388951fb3fe7d18c08d77bf94` |
| [ap-method-com_macrovideo_sdk_smartlink_IoTManagerNative-StartSmartConnection.sanitized.json](evidence/ap-method-com_macrovideo_sdk_smartlink_IoTManagerNative-StartSmartConnection.sanitized.json) | `0a178a43c58fa69f1b5abcf8072f0ce670d9ddae7ca15d10bdd80018f8322feb` |
| [ap-method-com_macrovideo_sdk_smartlink_IoTManagerNative-_clinit_.sanitized.json](evidence/ap-method-com_macrovideo_sdk_smartlink_IoTManagerNative-_clinit_.sanitized.json) | `7998823f69e0ba10b80651b64cf2cfc62ad39d89ee141c8758a7318268323de4` |
| [ap-method-com_macrovideo_sdk_tools_Functions-ParseDevice.sanitized.json](evidence/ap-method-com_macrovideo_sdk_tools_Functions-ParseDevice.sanitized.json) | `f403ff09d0b67079739ae36a71c1d9072e21245b059c22099ea0c62c88dff874` |
| [ap-method-com_macrovideo_sdk_tools_Functions-parseResult.sanitized.json](evidence/ap-method-com_macrovideo_sdk_tools_Functions-parseResult.sanitized.json) | `6f1cb9fc95e5bb04c768ef312c779764c65819d8f434b831edfa2afa026fe30a` |
| [ap-method-com_macrovideo_sdk_tools_QRContentUtils-generateConfigNetQRContent.sanitized.json](evidence/ap-method-com_macrovideo_sdk_tools_QRContentUtils-generateConfigNetQRContent.sanitized.json) | `7b9ecfd917191d1d9c8ea48fc41e02d84fdedfb0682d5320edcc691bd51565c4` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-broadcastPinCode.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-broadcastPinCode.sanitized.json) | `343a0d1db060ecae32d5c171f1fbe4695b4c8e6146637b79acf130d24d068b75` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-getBindToken.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-getBindToken.sanitized.json) | `4e307614bd3e24fe16d867b953003ad53be3012aa23187ef92a812c5ec14e02f` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-querySupperConfigNetWay.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-querySupperConfigNetWay.sanitized.json) | `3b930bbfa55443e75ff4d0497a4e4533bfc3e1832ff59c7e863cf96a74fb5f37` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-sendPinCode.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-sendPinCode.sanitized.json) | `d1dcc4836f2dc212a80cfa116d9e0e6e386f7746d2c914af8d6fab8c89c96d0c` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_AnonymousClass35-run.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_AnonymousClass35-run.sanitized.json) | `7dae87e16c3a2faf276b48ca4e83b3fdb46adbbf3d0365615ee0f87aad3956ba` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_ConfigHotspotToWifiThread-run.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_ConfigHotspotToWifiThread-run.sanitized.json) | `22c44308edfd5d3ce58ca0136b20652ae0eb90496ff39a843e272f2f08ceebce` |
| [ap-method-com_macrovideo_v380_fragments_AddDeviceSetPwdFragment_DevUserPwdConfigThread-run.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceSetPwdFragment_DevUserPwdConfigThread-run.sanitized.json) | `bff538b6dd0de391516a2a1c2c19fda436542414967d3a256a6f993734f4bf0a` |
| [ap-method-com_macrovideo_v380_fragments_LanDeviceSetPasswordFragment_SetLanPasswordThread-run.sanitized.json](evidence/ap-method-com_macrovideo_v380_fragments_LanDeviceSetPasswordFragment_SetLanPasswordThread-run.sanitized.json) | `99fbb87afc1a4eb786e61678c71d45be14838d5222ce318fb73b17ebe551fbc1` |
| [ap-network-a2.dex.txt](evidence/ap-network-a2.dex.txt) | `f505742b7cec8684beae67b60a01e6617929c6153bc77e0cee8dfc7e49820e6a` |
| [ap-public-constants.json](evidence/ap-public-constants.json) | `632e416de406943669e24692efd620cd887cb792d3c4f450121242291df7be57` |
| [ap-validation.json](evidence/ap-validation.json) | `e3a5c59cce9fd5a4f8d8f28efb2a200e273e622d8ee4838221169e04cdd901b9` |
| [iot-dex-contracts.sanitized.txt](evidence/iot-dex-contracts.sanitized.txt) | `689658652fe632001e19602f41d28ff96e9e3e61127b236dfe51774ba3266a9d` |
| [iot-java-contracts.sanitized.json](evidence/iot-java-contracts.sanitized.json) | `b988f84b322783ae894358a018c3488bceceedf3ef4d26abba95414794f36be7` |
| [iot-native-contracts.sanitized.json](evidence/iot-native-contracts.sanitized.json) | `53ea51ddb99c6fc56713059d80e2ce9309c4764625d45d358d2793285671ae89` |
| [legacy-framing-types.sanitized.json](evidence/legacy-framing-types.sanitized.json) | `f237df0fa9b1e5e8a66f8ee2fb17b04afb0518f5446ad944d5ce3ead17d8e51a` |
| [legacy-login-constant-provenance.sanitized.json](evidence/legacy-login-constant-provenance.sanitized.json) | `085f373462ddbbe12265974661faba6b4377b017b5362f2cacad42c0c69dbc79` |
| [legacy-media-constant-provenance.sanitized.json](evidence/legacy-media-constant-provenance.sanitized.json) | `e4fb48a31974d5ee60ff71142fe8feaef85d5e45689cedc6ed39d0cfdd737915` |
| [legacy-quality.dex.txt](evidence/legacy-quality.dex.txt) | `321db1d70daa176d5fc7e96eb64ab465d2d6317cf3334e5b27b987b2f70abd3e` |

## Bukti perbaikan disconnect0.2.1

Disassembly statis dari ekstraksi REA yang cocok; tidak ada eksekusi vendor atau pengujian koneksi. [Analisis perbaikan](stream-disconnect.md).

| Berkas | SHA-256 |
|---|---|
| [iot-disconnect-static.md](evidence/iot-disconnect-static.md) | `fa50727a9e9d112f1ea1f5b82846bb0f52315d915b2b18df19d4b100fef645af` |

## Bukti rekaman/pengaturan0.3.0

[Rincian kontrak dan batas](recordings-settings-wire.md). REA read-only/DEX/ELF, tanpa pengujian kamera. Hash berikut milik berkas turunan tersanitasi, bukan pengganti evidence ID REA pada indeks.

| Berkas | SHA-256 |
|---|---|
| [features-DeviceConfigSetting-a-10.sanitized.json](evidence/features-DeviceConfigSetting-a-10.sanitized.json) | `a609fa8690c7da24e73a68ceeb755cfcd20ea914124414d40fa3ed1ea2124e9d` |
| [features-DeviceConfigSetting-a-11.sanitized.json](evidence/features-DeviceConfigSetting-a-11.sanitized.json) | `356856b3b67569f5b8330565d6f796b7b4a408cf53c454f46d8dce86d124696a` |
| [features-DeviceConfigSetting-b-4.sanitized.json](evidence/features-DeviceConfigSetting-b-4.sanitized.json) | `faedfa9d0431a8b24459fd737f4e9fee9853511c333dfb887fdd65314add5a8d` |
| [features-DeviceConfigSetting-createIntValueJsonObj-1.sanitized.json](evidence/features-DeviceConfigSetting-createIntValueJsonObj-1.sanitized.json) | `1c41828d6cb0aee4330d2d3811464d32830d3899c522286e40c10da732241b5f` |
| [features-DeviceConfigSetting-createRecordConfigPidList-0.sanitized.json](evidence/features-DeviceConfigSetting-createRecordConfigPidList-0.sanitized.json) | `d839ae0805a5dd5613734f36b4fccbf6c0d18b4487954dcbdb36528602036bec` |
| [features-DeviceConfigSetting-createWhiteLightConfigPidList-0.sanitized.json](evidence/features-DeviceConfigSetting-createWhiteLightConfigPidList-0.sanitized.json) | `3cf9793b092c8ff26e4b0ec6ca364d62b2b7209c9c0f3d23c3aac675618cd6e0` |
| [features-DeviceConfigSetting-getBooleanValue-0.sanitized.json](evidence/features-DeviceConfigSetting-getBooleanValue-0.sanitized.json) | `12c43b65f7046e3ba5fa1de8121b5fa2638c603185b01f673c62c1c885c08189` |
| [features-DeviceSDCardSetting-setSDCardFormat-0.sanitized.json](evidence/features-DeviceSDCardSetting-setSDCardFormat-0.sanitized.json) | `288d6edbcdb7b72beb6cac8e256c1234da13d6c8c476732ca2b28a7aa0bbc877` |
| [features-GetRecFileThread-run-0.sanitized.json](evidence/features-GetRecFileThread-run-0.sanitized.json) | `84f1fec0108c84c81173ebf8e0d6939d11d897a3042775525e753674e2d7db75` |
| [features-HSFileDownloader-StartDownloadRecSegment-2.sanitized.json](evidence/features-HSFileDownloader-StartDownloadRecSegment-2.sanitized.json) | `85c6f2640980ad24bfe1df016c3c20486c0ceedb91afdb3d86fe672c14adeec9` |
| [features-HSTFCardRecNewPlatformInfo-createFrom-0.sanitized.json](evidence/features-HSTFCardRecNewPlatformInfo-createFrom-0.sanitized.json) | `bf2ae8d149e53beaf257be05fabd3db895781e0393b5bd61ce27240bc8735533` |
| [features-IoTDevAbility-isSupportRecordFHD-0.sanitized.json](evidence/features-IoTDevAbility-isSupportRecordFHD-0.sanitized.json) | `e48c9e8a7d608e7b1f80f7412fd9918e8fb073d082a0482aa6723b3615c1dbe5` |
| [features-IoTDevAbility-isSupportRecordHD-0.sanitized.json](evidence/features-IoTDevAbility-isSupportRecordHD-0.sanitized.json) | `ee1babc4252973eb947db58714c61b12f4e4069cd4574ae7df20a84a93d868d8` |
| [features-IoTDevAbility-isSupportRecordSD-0.sanitized.json](evidence/features-IoTDevAbility-isSupportRecordSD-0.sanitized.json) | `00d3ccafaa50e70942d2948d0fae03aaf5a3b9aa00a64d06da8444d81b5540bb` |
| [features-Protocol-MVSPT_IOTC_DAID-0.sanitized.json](evidence/features-Protocol-MVSPT_IOTC_DAID-0.sanitized.json) | `98eee574ad0716f978d324cb5299db2e6fc644f19476acb7d441625db8f2ed07` |
| [features-Protocol-MVSPT_IOTC_DPID-0.sanitized.json](evidence/features-Protocol-MVSPT_IOTC_DPID-0.sanitized.json) | `0b0ebc5c2c3910a13057fec999d9eef50aedd11c2f118bf3817aa7f060507084` |
| [features-RecordFileHelper-b-0.sanitized.json](evidence/features-RecordFileHelper-b-0.sanitized.json) | `e11cc3d0b7d1d9ac3cdccf68046ebc5b8a7e2e79a12b979856e9f41c8035ac45` |
| [features-RecordFileParam-init-1.sanitized.json](evidence/features-RecordFileParam-init-1.sanitized.json) | `00d4f8d5afd0db3cf0b1ad323fc343e7e5b94c8fa4ede894fe7bedffe5e747cf` |
| [features-method-ids.dex.txt](evidence/features-method-ids.dex.txt) | `a0a4eac9744e536f0d2de03b0e07dce62387c2ba6dfa6b91b89b8660894cc297` |
| [features-misc-ability.dex.txt](evidence/features-misc-ability.dex.txt) | `d5413282782d091edd466acc3503f6d693b36850d96d18ee36c3d5a7598e2e10` |
| [features-native-contracts.sanitized.json](evidence/features-native-contracts.sanitized.json) | `a9b8b089debf87dd527c51ecce3bf3ac621036406b85c1b76191d31d2a369751` |
| [features-property-parser.dex.txt](evidence/features-property-parser.dex.txt) | `b4315881fa4db6be74a7a976f9c20175dd4af7f7b24fe10c35cc89b06bb79f68` |
| [features-rea-index.json](evidence/features-rea-index.json) | `8e4bf0c40f4e6d692d56c5f00be0e25e26c1dd4faa8feeecaa6ee6ce2cf08e50` |

## Riset SSID/password hotspot, 14 Oktober 2026

[Riset AP hotspot](ap-hotspot-settings-research.md) memuat hasil REA tambahan untuk `setWiFiAPConfig`, caller UI, validator, serta jalur legacy. Berkas `ap-hotspot-*.sanitized.json` adalah turunan noncanonical yang mempertahankan source evidence ID dan limitations; tidak ada pengujian koneksi kamera.

Implementasi hotspot menambah [konstanta network](evidence/ap-hotspot-network-constants.sanitized.json), [parser property DEX](evidence/ap-hotspot-property-parser.dex.txt), [capability SoftAP DEX](evidence/ap-hotspot-ability.dex.txt), [resource filter input](evidence/ap-hotspot-input-resources.txt), dan excerpt `ap-hotspot-DeviceConfigSetting-*`/`ap-hotspot-IoTDevAbility-*`. DEX/aapt2 adalah bukti pelengkap statis; excerpt REA tetap noncanonical. [Hasil implementasi dan batas](ap-hotspot-settings-research.md#hasil-implementasi-debug).

## Manajemen kamera0.4.0, 14 Oktober2026

[Kontrak, implementasi sebagian, dan batas](camera-management-wire.md). Excerpt JSON adalah derivatif noncanonical REA; DEX/field evaluation merupakan bukti pelengkap statis. Tidak ada eksekusi APK vendor atau pengujian kamera.

| Berkas | Source Evidence ID / jenis | SHA-256 berkas |
|---|---|---|
| [management-AlarmAreaJsonParse.sanitized.json](evidence/management-AlarmAreaJsonParse.sanitized.json) | `ev_631d9872deed41839cecbbd6761744ac05306ddb309a80f09e7e31c51f0c9e8c` | `adcf82a56f5d06d580977812f24a512390fc7046e413affb098ff3bb75d47b03` |
| [management-AlarmTimeSetJsonParse.sanitized.json](evidence/management-AlarmTimeSetJsonParse.sanitized.json) | `ev_6b417cba7713e36b3e220ad597a204180e5c16c41b43fe4e5d1d363aef34b48e` | `ba0492c943310d5c88efca7c70bda011fa19c4c63a7fd7217c37197ba7c152fe` |
| [management-DeviceConfigSetting-createAlarmConfigPidList1-0.sanitized.json](evidence/management-DeviceConfigSetting-createAlarmConfigPidList1-0.sanitized.json) | `ev_355efa9086e1e68fcbd751f9a084ef4216780e51e6303c6d277eeacc56c5c8e8` | `0ab42b34625352d3c3eace88da7afcec232c5fba32c986cb9deed0af0548c5a4` |
| [management-DeviceConfigSetting-createDeviceXParamConfigPidList-0.sanitized.json](evidence/management-DeviceConfigSetting-createDeviceXParamConfigPidList-0.sanitized.json) | `ev_0105d103e6ae8d92b3804631027b4f97ca18436ddb80fa534f479a85522505e0` | `e9bf83bb62ce3f43d3fb250bebd039bae39ea316714f0f9af3edfb7e61c467c8` |
| [management-DeviceConfigSetting-createPTZXCruiseConfigPidList-0.sanitized.json](evidence/management-DeviceConfigSetting-createPTZXCruiseConfigPidList-0.sanitized.json) | `ev_2593c126de031aa0f0465f950a126248e28df65a92c041fed8046d780f1ac73d` | `d62787846a31efb94f1d4a2d4aec0d13007806c89c83d5a8f54284e021758aea` |
| [management-DeviceConfigSetting-getVersionConfigInfoNewPlatform-0.sanitized.json](evidence/management-DeviceConfigSetting-getVersionConfigInfoNewPlatform-0.sanitized.json) | `ev_b6b908a15fd9dc16c4613697f8ee72aa7614e75a4bc44c55ee6ea4ad7cae3233` | `750f98f7cab22c968b292f35425404d081dc92a52c6eb9ed9845bd878a1728cc` |
| [management-DeviceConfigSetting-p-0.sanitized.json](evidence/management-DeviceConfigSetting-p-0.sanitized.json) | `ev_dff13934cdcee3cc46d19230cf675e28bafaa4d276714173e44943c333143c7e` | `056a0402dfd092cd6bcaa3f36bc935ca91356dd8a7b46b4302e3092a73fc2e9b` |
| [management-DeviceConfigSettingHomePageFragment-getLightSensitivityResId-0.sanitized.json](evidence/management-DeviceConfigSettingHomePageFragment-getLightSensitivityResId-0.sanitized.json) | `ev_2e8e2ba53b70c33311ebec9d68102c8abacfa7a959cfbac9f43fc0bfa131db59` | `9a8d985431ecf37a712c9dd2c497383df7ba977e45ca9b42068a855079081eb5` |
| [management-DeviceConfigSettingWhileLightFragment-onBackPressed-0.sanitized.json](evidence/management-DeviceConfigSettingWhileLightFragment-onBackPressed-0.sanitized.json) | `ev_8692cca54e6f277838190fb8351c2c6aafc8b47df3d7a50ffef4562c993614e1` | `27ca786faaeb775f740f22e2f85c53ee861b758d87dfed9915e3809f2689af36` |
| [management-DeviceConfigSettingWhileLightFragment-onClicked-0.sanitized.json](evidence/management-DeviceConfigSettingWhileLightFragment-onClicked-0.sanitized.json) | `ev_2c51656665dba997e440c56cc5eecf193b00ca5abfa3d81bf024fbdec565ec5d` | `20cbdc41ec8600bcd5cfe4e8932aafbc2ad2ba44bc142ae3c2f53f9abef7f441` |
| [management-DeviceParamSetting-setDeviceParam-1.sanitized.json](evidence/management-DeviceParamSetting-setDeviceParam-1.sanitized.json) | `ev_5eff95e0000672529967ceb9b96af123e53f16a2b141a975bec9ec39f8892970` | `4eb5e7d3c5cdc453d9cfa93b5d3a1bbd627de6a0959aeeb7f345d3c110f7e001` |
| [management-IoTDevAbility-getPresetCount-1.sanitized.json](evidence/management-IoTDevAbility-getPresetCount-1.sanitized.json) | `ev_002e7ac47b310c483d08fd01248b72584c729e1faeffa099a93520f6d66ab7a5` | `6e3a9f2df46d4e53842be9740b9e516b8d98030e908b2572ffe61f1c46007543` |
| [management-IoTDevAbility-isSupportLightTimer-0.sanitized.json](evidence/management-IoTDevAbility-isSupportLightTimer-0.sanitized.json) | `ev_58341da563d160d41cb71e29ab173e05475571e1e28d7004771a9466acc33b22` | `c9d6129e9ef08e33674a38b7b7f40df1552704ff4bf2e94474f9edbe3ca7af34` |
| [management-IoTDevAbility-isSupportPTZXAutoCruise-1.sanitized.json](evidence/management-IoTDevAbility-isSupportPTZXAutoCruise-1.sanitized.json) | `ev_b4e7b87196d8801d77334ed6191975e3c0f3aa865ea9cd8704d4a4ea3ceefb40` | `d78d18a7968637025da4abe04897ae35c8bd8ee735c827521e6ac8f877e54289` |
| [management-IoTDevAbility-isSupportPtzCalibrate-1.sanitized.json](evidence/management-IoTDevAbility-isSupportPtzCalibrate-1.sanitized.json) | `ev_841f0961af9c901c039db9c5972d0a99e0567b24a5659d2d16db023182369c8b` | `0a3ca0bf91568728cd72cb4f3bf6374499da1b1ad3f3ee8d76c7f762c68a3aff` |
| [management-IoTDevAbility-isSupportRestart-0.sanitized.json](evidence/management-IoTDevAbility-isSupportRestart-0.sanitized.json) | `ev_9e37eea33b5325deaa8f0f36cc4045305abd7d188f2f1135dd41e5c163541cf7` | `78e5bad7657c734186e0050fda34fc19bf350a8b47ba23a31aeb1129361d17a4` |
| [management-LightTimerInfoJsonParse.sanitized.json](evidence/management-LightTimerInfoJsonParse.sanitized.json) | `ev_c465a7b7152277aadb29a550f0c9acb6566bcd5e13c99c94b157641d2a668a1d` | `cf0ff897f7af4fe5aee03c51eef9b29adae0c5c0832b43de62cb4fe7284db82a` |
| [management-PtzAutoRouteInfoBean.sanitized.json](evidence/management-PtzAutoRouteInfoBean.sanitized.json) | `ev_362bdb6fc2984d603700f9be25f885678e56b5d4870bf27f172ebdbe92f72282` | `3c77a15292a520418b393d293982981d6178a5cdbd7be2fd79b809b5291e72c6` |
| [management-PtzCruiseJsonParse.sanitized.json](evidence/management-PtzCruiseJsonParse.sanitized.json) | `ev_e9cdd66738b22bda677ca13a9d668e6e03bb55b75aeb25ec212049b219e3ced2` | `bbc5c0e2659c4624229b3658c6e9f57c6d8b45c2526ed9cb90cb3b9fd5f5e894` |
| [management-PtzPresetUseJsonParse.sanitized.json](evidence/management-PtzPresetUseJsonParse.sanitized.json) | `ev_5458365fe9fd1fc33152097791aa13307b6a39d03c5c2f3fb38c498bbe4f7905` | `d596d60c5cf06be34664b971ab94a2957fa3fea1919565a70307972fb978a0b2` |
| [management-TimeSchedule.sanitized.json](evidence/management-TimeSchedule.sanitized.json) | `ev_67df1a786ccc102c9965b3c2313ff576c344c7b6679fd6914b474e4bb4367f8c` | `4ef7b9e378507d6dfb44407473c066d687f7d2731a481c4c48bd48098aab8d32` |
| [management-ability.dex.txt](evidence/management-ability.dex.txt) | `supplementary static DEX/metadata` | `dae237dc3b9a27a49989d36013df6d5bd1267ca2fa03d75f010b6ef92bcaf2a0` |
| [management-ability.txt](evidence/management-ability.txt) | `supplementary static DEX/metadata` | `0f9d44b4abb1ab1ba87c13d7ccb9e2b08e91fd435a6c6e7d89d42ecea0a9f88a` |
| [management-json-fields.txt](evidence/management-json-fields.txt) | `supplementary static DEX/metadata` | `3ae164729c72c809cfffe635ed4e95098c16912735c28aa8ca89564f1bb8916d` |
| [management-light-schedule-wire.sanitized.json](evidence/management-light-schedule-wire.sanitized.json) | `ev_51d9b4e43ab895d1d713b1eec75f1e3d42145973e30e359bf36c484de124c6a7` | `0af2b32eb96dfc4f8a76ae84396c6616b40bf02749fde7e14f1a3fb8fc3479cb` |
| [management-property-ids.json](evidence/management-property-ids.json) | `supplementary static field evaluation` | `84f6c0aa9239f7fd7c254690a378796b9453b85738ba9b5fed3de63c502c2c34` |
| [management-protocol-clinit.dex.txt](evidence/management-protocol-clinit.dex.txt) | `supplementary static DEX/metadata` | `cdfc57d576fe2a3629ce76d17b0cb30d118b72f45590768c212d48e026d8ddf9` |
| [management-refs.txt](evidence/management-refs.txt) | `supplementary static DEX/metadata` | `ee676ebd6ca57c9de6955f9cd2c4a6cca2d3ef58253f641dfb4b390a6bfb4d25` |

## Alarm lanjutan0.4.1, 14 Oktober2026

[Bukti dan batas tahap2](alarm-stage2-wire.md). REA6.4.0/JADX0.7.1; APK dan SHA-256 sama dengan bagian manajemen0.4.0. Excerpt noncanonical mempertahankan source Evidence ID; canonical lokal berada di `analysis/references/alarm-stage2-canonical/` (diabaikan Git). DEX scan dan collector adalah bukti pelengkap statis, bukan runtime.

| Berkas | Source Evidence ID / jenis | SHA-256 berkas |
|---|---|---|
| [alarm-stage2-alarm-setting.sanitized.json](evidence/alarm-stage2-alarm-setting.sanitized.json) | `ev_022576ea2164f5601b6fdede567462f8d3662e75c84b1076737d93a434f5ae36` | `a0a8459e3d456208ef3b68e1a155274e4fa66dd43147b3698402254d92857d02` |
| [alarm-stage2-convert-get.sanitized.json](evidence/alarm-stage2-convert-get.sanitized.json) | `ev_f61d234bb3ae783bba2053419454dd1de4059e478f101346255c9d8f115dd2fb` | `42e948eb8c358a0421c870a147394463bdd706d0d4648de48f4acaf6b6d62ed6` |
| [alarm-stage2-convert-set.sanitized.json](evidence/alarm-stage2-convert-set.sanitized.json) | `ev_70eb2e049f97ca0395739a254f0a80f72c8fb292b44aaa9a2d8443af645196eb` | `c6551fca493a1443975c50956af5e0cb62df42b331dc75c4aba5a7719a9901d3` |
| [alarm-stage2-create-alarm.sanitized.json](evidence/alarm-stage2-create-alarm.sanitized.json) | `ev_f97a172762a1428fdecfeb92f90365e76041c6699579e8e744e88fec5de00a8b` | `1683f10efb0a57dd0e91cf340f7a983d2aba1d9ccfbf9b92c077869155ed3591` |
| [alarm-stage2-get-day.sanitized.json](evidence/alarm-stage2-get-day.sanitized.json) | `ev_7d4a23e939f43ab22169b17e7490170ce4fdaf5c0c5a49834bd12509c6b4c4f4` | `6d6bc384a417a3bccd74fba614c5c817a6ffd4364de302880241f87aab49d396` |
| [alarm-stage2-max-refs.sanitized.json](evidence/alarm-stage2-max-refs.sanitized.json) | `ev_541378811c2977b93cba23c33c89bd6a0dcb1c9ef25b5b3b4e7bf718af18868c` | `39fa6a65f91c2d4f98cbc4d182f258a47c7737c0a16c582914ed1f6b69b93971` |
| [alarm-stage2-motion-mask-helper.dex.txt](evidence/alarm-stage2-motion-mask-helper.dex.txt) | DEX statis / collector | `065d66d013d70b8b33087e89c92559ca755d1a3eace94ea52a57d81ec76e4461` |
| [alarm-stage2-motion-mask.dex.txt](evidence/alarm-stage2-motion-mask.dex.txt) | DEX statis / collector | `d339a775023529e74db39676c3185da8b0774b76317ba95d40010b48d8b8ad2b` |
| [alarm-stage2-motion-param.sanitized.json](evidence/alarm-stage2-motion-param.sanitized.json) | `ev_9bc9e5274d4fb7e026a2601b5f3c673bb6cf31aaaeed8dea5ce3feb0357cc88f` | `9a6c50f2a0235f0d61e182c3e4c25486c2cbcdf7b48794150b8eca049ea25c10` |
| [alarm-stage2-plan-empty.sanitized.json](evidence/alarm-stage2-plan-empty.sanitized.json) | `ev_1922c4eea4ad3992d3ef313d52da09f78150fe78f169ca4caad9bcdd4c257a19` | `82f0ec73ddfb22c88dc5e0798c3424489f702f4f463eb811f967c445efc87e6d` |
| [alarm-stage2-plan-is-empty.sanitized.json](evidence/alarm-stage2-plan-is-empty.sanitized.json) | `ev_bb624ba3c551f2019486c315cdca15e61f7237343218496592dee2f3aae8eabc` | `8d4988bd3faad2de053b040ce9f62ec5c335ec3cc1facf4436416d8ff5d2ffe1` |
| [alarm-stage2-plan-save.sanitized.json](evidence/alarm-stage2-plan-save.sanitized.json) | `ev_10afca6c35cdfbf5382457b31bf3305d78fbca460c73f286cbf392affe2ab971` | `e87c069e1b2762d96641dfc88ce96b1bc2117e7bcdb5d6553e597eef807fd7ac` |
| [alarm-stage2-reference-scan.java](evidence/alarm-stage2-reference-scan.java) | DEX statis / collector | `295af06064a11daf5280ada4a19cdb9cb92906814623bd0f503c3d76bd8c4ab4` |
| [alarm-stage2-sensitivity-refs.dex.txt](evidence/alarm-stage2-sensitivity-refs.dex.txt) | DEX statis / collector | `147f1d212d66a0a64a3f80d7a7e630a4d0ce98662832ff93091a497bfb45c3d5` |
| [alarm-stage2-timebar-mode.sanitized.json](evidence/alarm-stage2-timebar-mode.sanitized.json) | `ev_80e6b24ba81e32ce402efe445f2d165761353b1189719f03d62c2a21ae484d56` | `3ab5e3acd28443675e208db336bc13fb2ea3df773c4082e90e7cfbe45eb8e29e` |
| [alarm-stage2-timebar-try.sanitized.json](evidence/alarm-stage2-timebar-try.sanitized.json) | `ev_d5149ecd8bdae8fdc9a58c1b7d75d51a86c78b9d66dda3c29e554bd346238833` | `c98e7e213b6c4a959a334adcef0f5539092860bdaacb67b13e85f4b1b6f53078` |

## Editor area0.4.2, 14 Oktober2026

[Kontrak, kepemilikan gambar dan batas](area-stage3-wire.md). Excerpt REA6.4.0/JADX0.7.1 noncanonical mempertahankan Evidence ID dan identitas APK V3806.4.90; canonical lokal ada di `analysis/references/area-stage3-canonical/` (diabaikan Git). DEX parser memakai descriptor tepat karena fallback JADX menggabungkan overload. Dump diproduksi ulang menggunakan `ap-dex-instructions.java`; branch/ordinal terpilih adalah bukti statis pelengkap, bukan runtime.

| Berkas | Source Evidence ID / jenis | SHA-256 berkas |
|---|---|---|
| [area-stage3-adapter-view.sanitized.json](evidence/area-stage3-adapter-view.sanitized.json) | `ev_536d7b797d9523bd140401ce079c1fa31ac6d98e93b9621e9e157ecd9175b6fb` | `e8736cbb01ac3fb7481137c71da91121f49c653d6a2ef9cef2f2317093ca4f83` |
| [area-stage3-area-click.sanitized.json](evidence/area-stage3-area-click.sanitized.json) | `ev_3fa2014db9fd4d091004070a5f0d1274717e06addafc50727e1a90403b0b6031` | `5c6ce4dbc946d0a1b2f9c6647e2430b96dc985804527b7425cd51cf9af3d41d3` |
| [area-stage3-area-get-ui.sanitized.json](evidence/area-stage3-area-get-ui.sanitized.json) | `ev_4932aaa4dee96d1ce68f481e376e5f294e3c4c199da70b31bd202dc63e880eef` | `6ac22e9c935a51ed1f482029c8d0df96911046011926d083fcac5c0f00351c42` |
| [area-stage3-area-init.sanitized.json](evidence/area-stage3-area-init.sanitized.json) | `ev_9c40103efad7510781c5e9e622de833d84f049c2bfb99ebfd8b24c18eece8314` | `28b3c04e8081b81bf9768fdc48e37f0bd3d6b9810ab3d705bb6b32319123f591` |
| [area-stage3-area-open.sanitized.json](evidence/area-stage3-area-open.sanitized.json) | `ev_af3846e53b5cd977178da9565193114b6284bfa3b22498b12244255a57e69c38` | `ffbc13297a81975d8f372eea600a38840d4db213cac4407cd6c6a23bdd4aaa4e` |
| [area-stage3-area-player.sanitized.json](evidence/area-stage3-area-player.sanitized.json) | `ev_b38646460d86ae4785fcc9b669ceac485462cb7f58bfc8aca93bc8168c72925a` | `c34652c82c0456718ac6916d2dddb077c0c4b12c7250698b79793af758a03b14` |
| [area-stage3-area-save.sanitized.json](evidence/area-stage3-area-save.sanitized.json) | `ev_e358836ca044e33987caa11f99bb5c10132961ae32c9e167bd7fc2d8aab501cf` | `2bd6107cc5e318de1bf302816345be58d0d5ec4c0724a7763b2eeaab276fda13` |
| [area-stage3-area-set-ui.sanitized.json](evidence/area-stage3-area-set-ui.sanitized.json) | `ev_3096de04dd649c163dc977f9d13df21a3a5a043056b2ef1ba0ba76d2178edf83` | `81da81066d96ddaa4f43d526f2f10ccf3cc46a1fc706641f918b28e5a33d7594` |
| [area-stage3-cam-count.sanitized.json](evidence/area-stage3-cam-count.sanitized.json) | `ev_3079da4da696bd86f2c0dfe83684bf7d9038dacf6f7da750a6b48f45ed8f9f45` | `fa14fed01595586f7b6b80e36681e04ae4da8aceb3c75b19a0a9d14c6800222a` |
| [area-stage3-cam-type.sanitized.json](evidence/area-stage3-cam-type.sanitized.json) | `ev_f4662034bee1af20ce7ae49a55bc0baf3bca55319aa289078981bf8f0c68094a` | `1b6cd5776a63f3e4c294cf4bcca9ab443ef7b4eea2518f33c1c2eb3b72c32b1c` |
| [area-stage3-lens-ordinal.dex.txt](evidence/area-stage3-lens-ordinal.dex.txt) | DEX statis / descriptor terpilih | `bd6f173717ff929f9b8d2279684d203ae2ce404cd3de82cc6e83c15b8b0147ee` |
| [area-stage3-property-parser.dex.txt](evidence/area-stage3-property-parser.dex.txt) | DEX statis / descriptor terpilih | `88a6cc76bfaadec86f54d3a30d5c27a186d0cc828c1c07d47c49557288ac3d87` |

## Hapus rekaman dan format SD — 15 Oktober 2026

[Laporan dan batas](sd-maintenance-wire.md), [manifest SHA-256 input/alat/kolektor/hasil](evidence/sd-maintenance-static-summary.json). Bukti baru seluruhnya statis pelengkap, bukan canonical REA atau runtime kamera: scan5.429.555 instruksi DEX, excerpt parser format/ThreadSDCard, serta simbol ELF empat library arm64. String bebas/array DEX dikecualikan. Kontrak delete TF dan enum terminal format tetap belum terbukti; ketiadaan nama simbol bukan bukti semua firmware tidak mendukungnya. Source APK sama dengan investigasi sebelumnya, tidak dieksekusi atau dipaketkan ulang. Validasi aplikasi dicatat terpisah pada [hasil terbaru](../validation/sd-maintenance/results.json).

Tindak lanjut 16 Oktober: [manifest penelusuran penyelesaian format](evidence/sd-format-completion-summary.json), [kolektor REA/DEX](evidence/sd-format-completion-collect.py), dan [temuan beserta batas](sd-maintenance-wire.md#penelusuran-penyelesaian-format--16-oktober-2026). Tiga proyeksi REA noncanonical menyimpan Evidence ID kanonis; empat dump/excerpt DEX statis memeriksa thread/runnable hasil dan dua handler UI. Literal string/array dikecualikan. Return SDK `1001` memicu pesan sukses tanpa readback terminal pada cabang yang diperiksa; perubahan status model lokal tidak dipakai sebagai bukti kamera. Input APK/JAR sama, tidak dieksekusi. Aktivasi tetap terblokir sesuai pilihan bukti lengkap; [validasi offline](../validation/sd-format-completion/results.json) tidak mencakup kamera atau UI runtime.

## Talkback PCM IoT — 15 Oktober 2026

[Kontrak dan batas](talkback-wire.md), [manifest hash](evidence/talkback-static-summary.json). Tujuh excerpt Java REA mempertahankan Evidence ID sumber; bukan pengganti Evidence kanonis. Kolektor REA dan native diarsipkan. Instruksi DEX capability/codec dan bounded disassembly Capstone/ELF memakai APK yang sama, tanpa eksekusi target atau koneksi kamera/cloud. Gerbang implementasi hanya PCM100 mono16 pada8/16kHz; ADPCM/AAC tetap terkunci.

| Excerpt REA | Evidence ID sumber |
|---|---|
| [talkback-HSMediaPlayer-jniCallOnRecvMediaInfo.sanitized.json](evidence/talkback-HSMediaPlayer-jniCallOnRecvMediaInfo.sanitized.json) | `ev_b9212e73ebfd1595dcfc39a17cedb1ed56213565e35c76caa3aec143d54db9f9` |
| [talkback-HSMediaLibrary-sendSpeakAudioData.sanitized.json](evidence/talkback-HSMediaLibrary-sendSpeakAudioData.sanitized.json) | `ev_46f450393948b46b8c7514ffec41fc0e9e0fd85f292f6ca7e8e1f3f46eacc759` |
| [talkback-HSMediaPlayer-stopSpeak.sanitized.json](evidence/talkback-HSMediaPlayer-stopSpeak.sanitized.json) | `ev_3cbb7f821626f6839cc300af78d03afd623e0eb8f2b918305ca57c84016fbec5` |
| [talkback-HSMediaPlayer-startSpeak.sanitized.json](evidence/talkback-HSMediaPlayer-startSpeak.sanitized.json) | `ev_65980c3d6a3d47b3462be13a758c804ed79ade6ea356a8e60541f0ac7d106eaa` |
| [talkback-IoTDevAbility-isSupportSpeak.sanitized.json](evidence/talkback-IoTDevAbility-isSupportSpeak.sanitized.json) | `ev_d7aa554dc3139f5805efc4878057cb0838c865ad84bc39ecb5b63707999bd10c` |
| [talkback-MicrophoneRecorder-init.sanitized.json](evidence/talkback-MicrophoneRecorder-init.sanitized.json) | `ev_ec1fa2e8b959fe374850a3ddc81ffac8470dc79aaeda629644fdaddf86d923a9` |
| [talkback-MicrophoneRecorder-a.sanitized.json](evidence/talkback-MicrophoneRecorder-a.sanitized.json) | `ev_745ab5ace7cbe65a9c8c526b0c8929ec0c38ae4613813ea22773a5b9fddff9fe` |


## Offline provisioning, 15 October 2026

[Findings and implementation contract](ap-provisioning.md#offline-router-setup-investigation-and-implementation-15-october-2026). Same V380 6.4.90 APK SHA-256 as above. Eight REA 6.4.0/JADX 0.7.1 projections are noncanonical derivatives; canonical bundles are local and ignored. Four DEX dumps use the existing bounded collector, with arbitrary strings/arrays excluded. [Manifest and hashes](evidence/offline-pairing-summary.json), [REA collector](evidence/offline-pairing-collect.py). No runtime, camera or cloud evidence.

| Method | Source Evidence ID | Projection |
|---|---|---|
| `com.macrovideo.v380.fragments.AddDeviceApFragment.getBindToken` | `ev_fedbb07439008e10d6847f84481d31d147d7f1d34f3f94fe4c7156c414e8800d` | [offline-pairing-AddDeviceApFragment-getBindToken.sanitized.json](evidence/offline-pairing-AddDeviceApFragment-getBindToken.sanitized.json) |
| `com.macrovideo.v380.fragments.AddDeviceApFragment.sendPinCode` | `ev_fef8c8801abeef20057a01c208227d234b885b5297e0ab6b51f62fccba785713` | [offline-pairing-AddDeviceApFragment-sendPinCode.sanitized.json](evidence/offline-pairing-AddDeviceApFragment-sendPinCode.sanitized.json) |
| `com.macrovideo.v380.fragments.AddDeviceApFragment.broadcastPinCode` | `ev_c7e4b14ae1b81a0d0891c1295aa8bab12df6288c7845d062fce4e3c6a8c95d9c` | [offline-pairing-AddDeviceApFragment-broadcastPinCode.sanitized.json](evidence/offline-pairing-AddDeviceApFragment-broadcastPinCode.sanitized.json) |
| `com.macrovideo.sdk.tools.QRContentUtils.generateConfigNetQRContent` | `ev_cfd1b5ef92d4f6365893f9cda1ba29455508c7d7dcbcf6024ea6babac0e510eb` | [offline-pairing-QRContentUtils-generateConfigNetQRContent.sanitized.json](evidence/offline-pairing-QRContentUtils-generateConfigNetQRContent.sanitized.json) |
| `com.macrovideo.sdk.media.LoginHelper.requestBindIotDeviceByAP` | `ev_079137c315503980e410ae8558ff4902d1c9d1b6a025642f5e76d25853a647d7` | [offline-pairing-LoginHelper-requestBindIotDeviceByAP.sanitized.json](evidence/offline-pairing-LoginHelper-requestBindIotDeviceByAP.sanitized.json) |
| `com.macrovideo.sdk.media.LoginHelper.keyExchangeByLanSync` | `ev_2e4864dac1fa2f6138ea561a769478580b99dfdafa4c28174ad34f806a2f16ec` | [offline-pairing-LoginHelper-keyExchangeByLanSync.sanitized.json](evidence/offline-pairing-LoginHelper-keyExchangeByLanSync.sanitized.json) |
| `com.macrovideo.v380.fragments.AddDeviceApFragment.AnonymousClass35.run` | `ev_ee02d17bb9f0f378b95cf2e2c02daf308d7340d124e63d3d3b33f922b82db686` | [offline-pairing-BindSender-run.sanitized.json](evidence/offline-pairing-BindSender-run.sanitized.json) |
| `com.macrovideo.sdk.setting.DeviceConfigSetting.startActionNewPlatform` | `ev_ef28b3e53de8b9577999f59f6c094922a0ea512c85c7a0a3400fd47528bf49aa` | [offline-pairing-DeviceConfigSetting-startActionNewPlatform.sanitized.json](evidence/offline-pairing-DeviceConfigSetting-startActionNewPlatform.sanitized.json) |

## Talkback envelope follow-up — 15 Oktober 2026

[Receiver native tambahan](../validation/talkback-envelope/native-receiver.json) memperluas inspeksi statis ke0x506b8..0x515d8 untuk membedakan generasi thread dan SID header; input/library hash dan rentang instruksi ada pada artefak. [Observasi kamera dan hasil](../validation/talkback-envelope/results.json) adalah bukti runtime client independen yang diminta pengguna, terpisah dari evidence vendor statis: NOTIFY/CBC, SID mismatch (boolean saja), codec102/16000Hz/16-bit/mono. Tidak ada eksekusi APK/library vendor, mikrofon, PTZ atau write konfigurasi kamera.

## AAC talkback — 15 Oktober 2026

[Proyeksi jalur encoded-audio22](../validation/talkback-aac/native-encoded-audio.json), [factory codec](../validation/talkback-aac/native-factory.json) dan [encoder AAC](../validation/talkback-aac/native-encoder.json) merupakan instruksi statis tambahan dari APK yang sama. DEX konstanta102=ADTS AAC sudah ada di `evidence/talkback-capability.dex.txt`. Factory2001=ADPCM dan2002=AAC dibedakan melalui jump table, bukan disamakan. [Validasi](../validation/talkback-aac/results.json) memisahkan delapan tes encoder/wire Android sintetis dari satu observasi metadata kamera; tidak ada rekaman mikrofon atau pengujian speaker.

## Laporan decitan dan koreksi PCM21 — 15 Oktober 2026

Laporan pemilik membuktikan kegagalan AAC uplink sebelumnya pada speaker. [Native ADPCM](../validation/talkback-noise/native-adpcm.json) memulihkan encoder2001→GM_ima_adpcm_encode, blok256B untuk505 sampel PCM16. [Tabel](../validation/talkback-noise/adpcm-tables.json) dan [decoder independen](../validation/talkback-noise/independent-decode.json) memvalidasi format blok sintetis. [Hasil revisi](../validation/talkback-noise/results.json) membedakan123 tes JVM/host decoder dari acceptance hardware yang belum dilakukan. Tidak ada library vendor dijalankan dan tidak ada capture mikrofon/kontak kamera dalam koreksi ini.
