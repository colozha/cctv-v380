# Local IoT / V3 protocol recovered from V380 6.4.90

The new-platform LAN exchange, common JSON packets, V3 media framing/cipher, capability query, and finite PTZ action are implemented independently in `IoTCommonProtocol`, `IoTKeyExchange`, `IoTMediaProtocol`, and `IoTControlProtocol`. This is a static reconstruction, not a successful camera test. No APK/native code was executed, no camera/cloud request was sent, and no account data or real credentials were read. APK SHA-256: `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`.

## Evidence and scope

- Existing REA `LoginHelper.keyExchangeByLanSync`: `ev_c43fdbdc570a5a9338d236fda296af170fd08058377511f101c14137adcba67a`, [method evidence](evidence/method-LoginHelper-keyExchangeByLanSync.json).
- [Sanitized Java contract excerpts](evidence/iot-java-contracts.sanitized.json) preserve each original REA Evidence ID. In particular: common JSON header `ev_644be793d3b5743a41efe7d682c3a0e7d188d5c0508331950e62b9d4f32b45ea`; response validation `ev_bac6b34a50acd0de8440a03fb7c43cb8c647d81fd45f23cde81643e682a921a5`; explicit shared-key helper `ev_db8cdb06603c1fe3b974955654ee10dff1a4a9ec3be086a72ee33b66ad57a5e4`; explicit public-key helper `ev_d82be349e4a743b3fc3f29a39a85bf523e9deb3a875e51cd8efa9870b2831aac`.
- [Bounded native instructions](evidence/iot-native-contracts.sanitized.json) are supplementary Capstone/readelf analysis of the extracted arm64 libraries. They link to REA extraction `ev_f2eaec0ee202bdfaab2d678c927f134eae42d7df9523d26a818a0c13cba2959d` and retain per-library SHA-256. These are not REA native decompilation results.
- [Bounded DEX instructions](evidence/iot-dex-contracts.sanitized.txt) corroborate capability indices/bitmasks, random ranges, and the `perm` field's `long` type. The class inventory source is REA `ev_2799c5a183b5fbc79ab95840e58c0717c5fb3496dac52825929ba0dfffc4b4bb`.

Saved projections exclude bootstrap constant values, credentials, runtime session fields, and diagnostic logging. The client includes the shipped bootstrap cipher constant because the actual interoperable wire format requires it. It is not an owner/default password, account credential, or recovered runtime secret.

## Common TCP packet

The direct LAN packet has a 16-byte header followed by exactly the declared body. Integers below are little endian.

| Offset | Field |
| --- | --- |
| 0 / u32 | Command |
| 4 / u8 | Version 1 |
| 5 / u8 | Security: 0 plaintext, 1 AES128 ECB, 2 AES128 CBC |
| 6 / u8 | Session ID |
| 7 / u8 | Magic byte |
| 8..11 | Reserved, zero in requests |
| 12 / u32 | Body byte length |

Common commands are IoT JSON `939722776`, binary media `939722784`, JSON notification `939722786`, and shutdown `199056`. Native `initIotcReq` at `0x93c38..0x93e24` always sends command IoT, including live-control requests. Binary media uses command MEDIA only on the incoming media branch.

The JSON envelope is `{id, method_id, chn, ts, params}`; `ts` is Unix seconds. JSON ciphers use the first 16 bytes of the 32-byte material as AES key and the final 16 bytes as CBC IV, with PKCS7 padding (`PKCS5Padding` in JCA). `Protocol.cipherJsonData` delegates to the native helper at `0x93e24`; `libHSMbedtls.mvt_cipher_start` at `0xe3f8` selects AES128 and default padding mode zero. No global crypto provider is registered.

Successful replies must match request ID, method ID, and channel (channel zero is also accepted). APK `isResultOk` accepts errors 1103/1104 even without transaction fields; the client propagates these only as failure. It strengthens the APK's unrelated plaintext-method shortcut by still requiring successful plaintext replies to match the method.

| Result | Client meaning |
| --- | --- |
| 1000 | Success |
| 1103 | Public-key/authentication failure |
| 1104 | Expired session; reconnect and perform a fresh LAN exchange |
| 1109 | Permission denied |
| 1119 | LAN password/value mismatch |
| Other | Device rejected, preserving the numeric code |

JSON bodies are bounded to 1 MiB and binary media to 4 MiB before allocation. These bounds are client resource limits, not inferred firmware limits. Truncated packets, unknown ciphers, invalid UTF-8, impossible lengths, and malformed transaction fields fail closed.

## LAN password exchange

`LoginHelper.keyExchangeByLanSync(ip,port,lanPassword)` opens a TCP socket and sends method `196870`, version 1, security 2, SID zero, channel one. Its random ID range is 10..1000008; magic is 1..127. The implementation retains those wire ranges but uses `SecureRandom`.

1. Generate a fresh 32-byte private scalar and its X25519 public key.
2. XOR every public-key byte with magic; Base64 without wrapping becomes `verify.cKM`.
3. SHA-256 over the masked public key followed by the supplied LAN password's UTF-8 bytes; Base64 becomes `verify.cS`.
4. Send `params:{act:1,verify:{cKM,cS,tz,ts}}`. Timezone is signed hours times 100 plus signed remaining minutes, using the default timezone raw offset (e.g. Jakarta 700, +05:30 530, -03:30 -330).
5. Parse method `196871`, result 1000 and `data.verify:{dK,dS,sid,perm,origin}`. `dK` is the unmasked device public key; `dS` is Base64 SHA-256 of the decoded `dK`.
6. Verify `dS` in constant time, reject low-order/all-zero agreements, and derive the raw 32-byte X25519 shared secret. No additional digest/KDF is applied by the recovered helper.

Despite its Java name, `ED25519Utils` uses Curve25519 key agreement, not Ed25519 signatures. Native `Curve25519_Calc` at `libed25519.so:0x7ba0` clamps the scalar (`low & 0xf8`, `high & 0x3f | 0x40`) and runs the Montgomery ladder. The public-key wrapper uses the standard base point 9, confirmed statically. JNI `CreateSharedKey` forwards raw scalar and peer public bytes. Thus lightweight Bouncy Castle `X25519` is used on Android 26; JCA handles AES/SHA-256/Base64.

`sid` and `origin` are integers, while **`perm` is a Java long**, preserved as Kotlin `Long`. Permission-bit and origin semantics were not established and are not invented. The vendor closes the exchange socket. Streaming/control use a fresh selected-network TCP connection with the resulting SID/key. In-memory material is wiped on close; no session material is serialized or logged. Reauthentication generates a fresh private scalar.

## V3 live stream and media

Live control request method `197122`, response `197123`; live-information notification `197124`. Request parameters are:

```json
{"up":{"quality":1,"video":1,"audio":0,"IDRreq":1}}
```

APK stream selector zero maps to quality one; selector one maps to quality two. Optional `vsspec` selects a view. Live quality changes use the same method with `up.quality`. Channel is the native zero-based channel plus one. The ordinary JSON request uses security 2, session SID, magic zero. Information notifications can arrive before the control response.

Notification `params.vattr` supplies `codec`, `width`, and `height`; codec 4 is AVC and 5 is HEVC. Optional `msvi` entries supply per-view `codec` and `res:[width,height]`. `parseLiveMediaInfo` at `0x940dc` also parses audio attributes, which this video client does not decode.

Binary frame bodies begin with a 32-byte inner header:

| Offset | Field |
| --- | --- |
| 0 / u8 | Opaque format field |
| 1 / u8 | Frame type: 1 keyframe, 2/3 delta video, 4 audio |
| 2 / u32 | Sequence |
| 6 / u32 | Compressed payload size |
| 10 / u64 | Timestamp in milliseconds |
| 18 / u32 | Initial plaintext payload byte count |
| 22 / u32 | Encrypted chunk size |
| 26 / u32 | Plaintext gap size |
| 30 / u8 | View channel |
| 31 | Reserved |

With security 1/2, the 32-byte header is decrypted first using AES ECB/CBC **NoPadding**. The same per-packet cipher state continues across encrypted payload chunks. For video, leave the prefix untouched, decrypt one chunk, skip a plaintext gap, repeat; a tail shorter than a chunk remains unchanged. The chunk and gap may exceed the video payload size; the loop skips an incomplete chunk and clamps the gap to remaining bytes. Prefix remains bounded by the actual payload for independent-client safety. CBC chaining does not advance over plaintext gaps. For audio, decrypt the declared initial chunk and leave its remaining tail untouched. Native sources: `iotc_ms_frame_cipher_init` at `0x93578` and `iotc_ms_frame_cipher` at `0x9394c`. This is distinct from legacy V2 sparse AES.

The field is identified as milliseconds in native code. The independent session conservatively uses relative monotonic receipt PTS until camera timing behavior is checked. Vendor code adds local timezone offset before its media callback; that offset is unnecessary for frame timing.

## Capabilities and PTZ

Ability request `197386` has empty `params`; response `197387` carries `data`. `data.ctrl[0]` uses bit zero for horizontal PTZ and bit one for vertical PTZ. With multi-view data, `data.msc[].ctrl` supplies view-specific masks. This is established by `IoTDevAbility.getPtzType`, ordinal-zero `MVS_DCTRL_ABLC_PT_CTRL`, and `BMPC(n)=1<<(n-1)`. Missing capability data stays explicitly unknown; the app must not infer PTZ from session permission values.

The V3 PTZ finite-step action uses method `197384`, action `393728` (`0x60200`), with `params:{daid:393728,no_rsp:true,in:{op,cnt}}`. Operation zero is stop, one up, two down, three left, four right. Count defaults to one. Optional view selection adds `vsbmp:1<<view`. Native `actionCtrlThreadFunc` at `0x4da54..0x4db20` proves `daid`, `op`, `cnt`, `no_rsp`, and the four boolean-to-operation mappings. Direction names are corroborated by the legacy packet labels in the historical reference; physical movement remains untested. `no_rsp:true` means the caller should not wait for an action reply.

The generic action envelope is `daid` plus optional positive `vsbmp`, optional true `no_rsp`, and optional `in`, confirmed by native `createActionJsonObj` at `0x95364` and the AP agent's Java evidence. AP Wi-Fi setup is documented separately by its owner.

## Verification and remaining limits

Synthetic JVM checks cover RFC7748 Alice/Bob agreement, request proof fields, peer digest mismatch, low-order public keys, single-use/wiped exchange lifecycle, bounded/truncated packets, request correlation, device errors, sparse CBC/ECB with plaintext gaps, negotiated codec, capability masks, and finite-step envelopes. They use no real credentials or camera addresses. The first targeted Gradle invocation reached protocol compilation but was blocked by the concurrent root UI model transition; the root agent owns the final build/test run.

No physical model/protocol generation is inferred from the HSC firmware string. Existing static evidence selects V3 when `protocolType==2`; `pver>=5` also has an APK LAN/MQ-dependent path. The independent local-password path is implemented, but a camera that requires that MQ path is not proven to accept it. There is no cloud fallback, token extraction, default-password guessing, or native vendor library dependency. Actual authentication, rendered frames, quality changes, PTZ direction, and session renewal on the owner's camera remain runtime verification tasks.

## Disconnect correction0.2.1

[Static receiver/feedback ledger](evidence/iot-disconnect-static.md) additionally proves notification197120 and the ordinary encrypted IoT feedback `{flag:2,tms1,decode_tms}`. The client echoes finite numeric tms1 only for COMMAND_NOTIFY; ordinary IoT replies do not trigger another response. It uses the native base/default decode_tms0, without claiming a measured HSLivePlayer decode deficit or inventing a periodic heartbeat. Unknown commands can be skipped only by the bounded stream reader; authentication transactions remain strict.

The user's supplied diagnostic now shows successful local authentication/capability and rendered frames in the independent client, followed by disconnects. This is partial user evidence, not a stable compatibility pass. The exact old failure cannot be recovered because schema1 labeled stream errors as AUTH and omitted error type. Version0.2.1 adds typed stage/reason/phase metadata. [Incident analysis and validation limits](stream-disconnect.md).
