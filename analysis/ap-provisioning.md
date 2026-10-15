# AP provisioning and local pairing evidence

The implemented AP layer can send authenticated WiFi changes over the recovered legacy setting protocol and the direct common-IoT protocol. It also serializes the observed AP binding acknowledgement and LAN password change operations. An acknowledgement is followed by phone network migration and rediscovery of the same device ID; it does not establish that the camera joined the new network.

Fresh modern account binding remains incomplete under this investigation's no-cloud constraint. The APK's observed PIN pairing flow obtains a server `bind_token`, embeds it in its configuration content, sends the content to the AP, and then polls server binding status. No token, owner credential, initial camera password, or successful local substitute was invented.

Target: V380 6.4.90, `/home/husain/Downloads/v380-6-4-90.apk`, SHA-256 `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. All observations below are static REA/JADX or supplementary offline DEX instruction observations. The APK was not executed. No camera configuration/reset/pairing or cloud API request was performed.

The `ap-*.sanitized.json` files are deliberately **noncanonical derivatives** retaining source Evidence IDs. Unknown string literals were removed before persistence, except JSON field names and selected public encoding/algorithm constants. They are not original authenticated REA bundles. Supplementary DEX dumps exclude string/array data except selected public encoding constants.

## Recovered call chains

| Question | Observation and evidence | Coverage |
|---|---|---|
| Legacy AP authentication | `LoginHelper.getLoginHandleByAP` transforms the caller's password and calls legacy `b`; it tries `a` on protocol fallback. Success copies the supplied device identity/credentials into the handle. This is not the IoT LAN key exchange. [Source](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-getLoginHandleByAP.sanitized.json), `ev_57dfbca9884e36c795a18f92e5ed74b5ea17c721f88bbe44ddd7b84e5e3dc27a`. | Answered; initial credentials remain caller supplied. |
| Existing authenticated AP → WiFi | `AddDeviceApFragment.ConfigHotspotToWifiThread.run` calls `lanLogin(device,2)`, then `DeviceNetworkSetting.setNetworkConfig(login,device,1002,ssid,password)`. Only after result 256 does it switch the phone network and wait for LAN discovery of the same device ID. [Source](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_ConfigHotspotToWifiThread-run.sanitized.json), `ev_d127465d96c7d6f49403ea0be32d0ea600d3429a1344c8a0f5e04529cf879746`. | Answered for request/ACK/rediscovery sequence; no live confirmation. |
| Legacy account password UI | `AddDeviceSetPwdFragment.DevUserPwdConfigThread.run` first needs a valid setting login, then invokes `DeviceAccountSetting.setAccountConfig`. It updates stored credentials after confirmed success. [Source](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceSetPwdFragment_DevUserPwdConfigThread-run.sanitized.json), `ev_1a0c18d6c9837aa8f5775b3c906ca76a85032d3ec7299937b7fb0894523599a3`. | Call chain answered; account-config wire implementation is outside this AP layer. |
| Modern LAN password UI | `LanDeviceSetPasswordFragment.SetLanPasswordThread.run` calls `changeLanPassSync(old,new)`. On 1104 it attempts key exchange with the old password before retry. It persists the new password only after success 256. The decompiled retry checks inconsistent 1000/256 success conventions, so this was not copied blindly. [Source](evidence/ap-method-com_macrovideo_v380_fragments_LanDeviceSetPasswordFragment_SetLanPasswordThread-run.sanitized.json), `ev_b4cc8d693ccaf8701361daf9ac5d3891cfc3ec2efcb97764439baf6468fc4a15`. | Answered; caller owns reauthentication/retry policy. |
| Fresh modern PIN pairing | `getBindToken` calls `OkHttpUtil.getAuthorizeToken`, reads `bind_token`, and stores it. `sendPinCode` requires a four-digit nonzero PIN unless fast-PIN is supplied, and calls `generateConfigNetQRContent(...,mBindToken)`. `AnonymousClass35.run` sends bind action 2 and treats 1000/1107 as a transition to phone network migration followed by **server binding-status query**, not final success. [Token source](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-getBindToken.sanitized.json), `ev_9101c3964b257c037af9147fbd4d0ae3b379126e2d490d34597b2260bca6e1d4`; [PIN source](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment-sendPinCode.sanitized.json), `ev_b66a1c91dc8f24aedc81c01f6712b739cc5c6f55fc9ad15bd8b3e62b292fab40`; [send/result source](evidence/ap-method-com_macrovideo_v380_fragments_AddDeviceApFragment_AnonymousClass35-run.sanitized.json), `ev_805eccc912cc9ccb06d96adbcc241b7a62c83045cc84852fc10ec1a1b270012a`. | Partially answered; no supported cloudless account-binding completion proved. |
| Native smart connection boundary | `IoTManagerNative` inventories `Scan`, `StartSmartConnection`, `StopSmartConnection`, and version query. `StartSmartConnection` exists but has no Java body; its radio encoding was not reconstructed. The authenticated AP WiFi path above uses Java setting calls. [Inventory](evidence/ap-class-com_macrovideo_sdk_smartlink_IoTManagerNative.sanitized.json), `ev_0f1d4594fe42e90597cb72785444f07b9db7292f7953b41db129abab1bd9ee4a`; [native boundary](evidence/ap-method-com_macrovideo_sdk_smartlink_IoTManagerNative-StartSmartConnection.sanitized.json), `ev_b928ed86be0dfcbca16c2c7233ebfe59a5b2b64d137310db97376990e4b1695f`. | Native SmartLink remains unresolved; no guessed implementation. |

## Implemented wire contracts

`ApProtocol` contains pure serializers/parsers and suspend transactions accepting `write(ByteArray)` and `readExact(Int)` callbacks. The Android caller selects/binds the network, opens/closes the socket, handles cancellation/timeouts, and verifies rediscovery. Buffers containing request/password material are cleared after use. No method logs credentials or payloads.

### Legacy WiFi setting, version ≥ 2

`DeviceNetworkSetting.setNetworkConfig` chooses the authenticated legacy `a` overload when the login setting version is at least `Functions.SETTING_VERSION`, initialized to 2. The 256-byte request contains:

| Offset | Representation | Meaning |
|---:|---|---|
| 0 | uint32 LE | command 320 |
| 4 | uint32 LE | device ID |
| 8 | byte | 1 |
| 9 | uint32 LE | authenticated login handle |
| 13 | uint32 LE | network mode 1002 |
| 17 | fixed slot 64 bytes | SSID UTF-8 |
| 81 | fixed slot 32 bytes | WiFi password GBK |
| Remaining | zero | reserved |

The implementation applies the actual WiFi SSID limit of 32 UTF-8 bytes and the recovered password slot limit of 32 GBK bytes. It rejects unmappable GBK input and overflow instead of corrupting adjacent fields.

The reply is exactly 32 bytes, with command 420 at offset 0, status at 4, and reason at 8. **Only command 420 and status 1001 acknowledge the change.** Other statuses preserve the raw reason; malformed commands/lengths fail. This condition was confirmed from DEX instructions because JADX inverted/duplicated the response branches: [Java observation](evidence/ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-a.sanitized.json), `ev_391848c6ac7b36fb4614b3971ac7d4d4cea1eeafa7cd7e570ec177caa9c187f4`; [DEX](evidence/ap-network-a2.dex.txt), code item 8535320, code units `0x13e..0x147`. GBK is explicit at code unit `0x9c`, UTF-8 at `0x83`; the 256-byte write/32-byte read occur at `0xb7`/`0xf0`.

The older credential-bearing command-120/412-byte path is visible in `setNetworkConfig`, but this implementation does not expose it because the supported legacy authentication profile already requires setting version ≥ 2. Its obsolete authentication/bootstrap prerequisites were not bypassed.

### Modern authenticated WiFi setting

`DeviceNetworkSetting.setNetworkConfig` constructs action input `{mode:2, ssid:Base64(UTF8(ssid)), pwd:Base64(UTF8(password))}` and invokes `startActionNewPlatform` with `DAID_NET_CONFIG = 262656`. The direct common-IoT branch uses:

- command 939722776, version 1, secure type 2, authenticated SID/key, magic 0, channel 1;
- request method 197384, response method 197385;
- params `{daid:262656, in:<the input object>}`. Zero `vsbmp` and false `no_rsp` are omitted.

[WiFi source](evidence/ap-method-com_macrovideo_sdk_setting_DeviceNetworkSetting-setNetworkConfig.sanitized.json), `ev_81819eabb8246b14ba99360e43caa3932575a4467bcc590bbacd6bd775cf3bf7`; [action shape](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-createActionJsonObj-overload0.sanitized.json), `ev_d6df9a846d6f1ddcf29cad77256e1d4bb981fca9cb0948b1b6f6e36786dba9a5`; [direct action transport](evidence/ap-method-com_macrovideo_sdk_setting_DeviceConfigSetting-b-overload1.sanitized.json), `ev_ee0a41072a851dc7ad0aec02c3eecf8b7d9f0272a7766bb2516db3e6a170b9db`; [public constant provenance](evidence/ap-public-constants.json).

The APK prefers this common direct branch for `pver < 5`; higher versions can choose an MQ path. MQ/cloud fallback is not implemented here. A camera response must match the request ID, channel and response method. An encrypted operation cannot be acknowledged by an unauthenticated plaintext success. Expired/denied device replies remain failures for the caller to handle.

### AP binding and LAN password changes

`bindInfo(action,fastPin,pinCode,bindInfo,clientCode,timeout)` preserves the recovered fields: `action`, optional `fastpin`, `pincode`, `bindinfo`, `clicode`, and `timeout`. `acknowledgeBinding` sends plain common-IoT method 196864 and expects 196865. All binding data must be supplied by an authorized flow; this function does not obtain or manufacture a server token. [Schema](evidence/ap-method-com_macrovideo_sdk_defines_Protocol-createBindInfoJsonObj.sanitized.json), `ev_05dea14d194ffae9c665c7b7d91b5c7f1c56c64c31c4de8345cea9580dbc9369`; [transaction](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-requestBindIotDeviceByAP.sanitized.json), `ev_950da6c5fec6fe1740fe9e9bf718260fbabd3acb1c6f118dc0184d363127a740`.

`changeLanPassword` uses authenticated secure type 2, method 196870/196871, and params:

```text
act = 2
change.lprC = Base64(XOR(newPasswordUTF8, magicByte))
change.lprS = Base64(SHA256(maskedNewPassword || oldPasswordUTF8))
```

A wire code of 1000 confirms the operation; the caller updates stored credentials afterwards. [Source](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-changeLanPassSync.sanitized.json), `ev_2e6efdf193d4d7b88ff720b5f848d365bdc8671b38d2ac36044e2254aed6f0a2`.

Deprecated `resetLanPassSync` uses act 3 to obtain `RDHC`/`RCPKC`, then act 4 with caller proof `reset.RDSC`. Its proof-generation/authorization contract is not reconstructed, so no reset transaction was exposed. [Source](evidence/ap-method-com_macrovideo_sdk_media_LoginHelper-resetLanPassSync.sanitized.json), `ev_8c5bb184f6d40eb5b8302c9ad8b3774a8858f226106905b38485c8acc1455016`.

## Discovery metadata needed for safe selection

Caret field 13 is **config ID**, not device type. Field 14 is **alarm state**, not platform: values 1000/1001 map to 2/1, values 1/2 pass through, other parsed values map to 0. Field 15 is bind state, 16 configuration ability, 17 special device type. This was checked through the `DeviceAsSearch` constructor rather than inferred from variable names. [ParseDevice](evidence/ap-method-com_macrovideo_sdk_tools_Functions-ParseDevice.sanitized.json), `ev_0993497fdfe6cb73c7278bea0028ffeba8726020a77698c5a0cf00d2be72973d`; [constructor](evidence/ap-method-com_macrovideo_sdk_objects_DeviceAsSearch-_init_.sanitized.json), `ev_e129da87fa3487289f709dfe60608c74bbc20b0e0da5740a4b91a60ed425cc28`.

Platform comes from the scanner's exact text prefix: generations 100/200 select old platform 1; generation 300 selects new platform 2 when new support is enabled. `DEVICE_PLATFORM_TYPE_NEW` becomes sentinel 999999 when both new platform features are disabled. [Existing receiver evidence](evidence/discovery-Receiver-run.sanitized.json), `ev_58da43d3c464389f6d5cd3ab537e9075116ecb2df136651f276a3ebe6aa59d75`; [constant DEX](evidence/ap-defines-platform.dex.txt).

The old-platform binary CMD1280 layout is byte4 config ID, byte5 device type, uint32 LE device ID at6, NUL-terminated IP in bytes10..41, uint32 LE port at42, byte46 alarm state, byte62 child count, and uint32 LE child IDs at63+4i. `parseBinaryDiscovery` checks the command, actual datagram bounds, IP/source agreement, valid port and bounded child count. The APK's receiver uses a padded backing array; this implementation deliberately validates the actual packet length. [Source](evidence/ap-method-com_macrovideo_sdk_tools_Functions-parseResult.sanitized.json), `ev_b8913b31f95a69f9bcc4db6cb054bfb91bdb8d5860f48d7e6899e0f0da989632`.

Firmware branding, an HSC SSID, or an ONVIF toggle outcome does not establish the protocol profile or ownership requirements. The current physical camera's generation and AP contract remain unverified.

## Validation and remaining work

`ApProtocolTest` uses synthetic values and in-memory transport callbacks. It checks exact binding/password field schemas, legacy offsets and fixed-size failures, modern action shape with independent JCE AES-CBC decoding, actual correlated acknowledgements, rejection of unrelated/plaintext-success responses, metadata distinctions, and binary packet truncation/source/identity bounds. No live camera or account fixture is used.

Focused isolated JVM compilation and JUnit execution **passed all 6 tests** against the actual AP/common-IoT sources. [Validation record](evidence/ap-validation.json). Full app Gradle execution initially encountered the concurrent root-owned String device-ID UI migration; final Android integration validation remains with the root task.


## Offline router setup investigation and implementation, 15 October 2026

A bounded new pass used REA 6.4.0/JADX 0.7.1 against the same V380 6.4.90 APK identified above. Eight method projections and four supplementary DEX dumps are listed with hashes in [offline-pairing-summary.json](evidence/offline-pairing-summary.json). The [collector](evidence/offline-pairing-collect.py) preserves canonical bundles only in ignored local references and exports projections with Java string literals removed except selected field names. DEX output excludes arbitrary string/array contents. No vendor APK execution, account request, camera connection or physical Wi-Fi change was performed.

### Bootstrap findings

| Boundary | Static observation | What remains unproved |
|---|---|---|
| Token acquisition | `getBindToken` obtains an authorization response and assigns `bind_token` before proceeding to AP selection. | No local token issuer or supported offline substitute was found in this chain. |
| PIN request | `sendPinCode` passes `mBindToken` to `generateConfigNetQRContent`; DEX code units `0x78..0x7a` confirm the field and call. | A PIN alone does not establish local login or owner binding. |
| Empty-token branch | The QR serializer tests token emptiness; DEX `0x34..0x59` confirms a serialization branch without a nonempty token. | Serializer permissiveness is not proof that firmware accepts an empty token or completes setup without cloud. No writer for that branch was added. |
| AP binding transport | `broadcastPinCode` uses action 1; `AnonymousClass35.run` sends action 2. Results 1000/1107 lead to Wi-Fi migration and a server binding-status query using `mBindToken`. | Action 1 is not account binding, and an action-2 ACK is not completion. |
| LAN password | `keyExchangeByLanSync` rejects null password but can serialize an explicitly supplied empty password. It computes verification material using that supplied password. | This does not prove a fresh camera permits empty-password authentication. The user must choose the no-local-password option explicitly. |
| Action routing | `startActionNewPlatform` overload 0 selects the direct common branch for `pver < 5` or the selected MQ-offline/P2P condition; other branches and renewal can use MQ/HTTPS. DEX `0xbc..0xc5` confirms the pver comparison. | Direct action support is not universal across firmware. The client does not copy vendor cloud fallback or automatic write retries. |

The gate for an additional bootstrap writer remains unmet: no complete cloudless authorization/credential initialization contract and firmware acceptance evidence were established. This is a bounded finding, not a claim that all V380 firmware requires internet. SmartLink radio encoding remains outside this recovered Java AP path.

### Local implementation contract

- Reuse command 320/mode 1002 for authenticated Legacy and action 262656/mode 2 for authenticated direct IoT. Mode 1 remains the separate hotspot editor.
- Validate input before opening the configuration transaction or saving pending metadata. Legacy password GBK encoding is strict and bounded to 32 bytes; modern password is bounded to 63 UTF-8 bytes. Empty password remains explicit in the UI.
- The Wi-Fi writer requires the expected profile ID, device ID, authentication revision, active Ready session and the network captured during AP authentication. Guards run before transport creation and immediately before the sole write, including after the pending callback.
- A callback atomically saves the existing version-1 pending record before write. Pre-write failure or a correlated explicit rejection clears it. Any other error after write may have started retains an unknown result. Cancellation closes the channel; restore/retry never invokes the writer.
- IoT Wi-Fi replies require encryption, the matching transaction/method/channel and SID zero or the expected SID. An unauthenticated or unrelated refusal cannot clear an uncertain result. Correlated encrypted refusals preserve typed authentication/session/permission causes while clearing the pending write. This stricter Wi-Fi rule does not change other AP binding/password transactions.
- After sending, release the AP request. Router selection uses Android Wi-Fi Settings. Internet/VALIDATED capability is not required, and sockets remain bound to the selected Wi-Fi Network.
- Verification rejects a readable SSID differing from the requested SSID and the original AP Network. When SSID is unavailable, an explicit confirmation is tied to that Network only. The app then requires same-ID rediscovery and local login; network checks repeat after discovery/authentication and during credential re-entry. Confirmation does not prove the router's SSID.
- Pending restoration keeps no router/AP credentials and repeats discovery/login only. Discarding progress clears local metadata and does not undo a camera change. Optional LAN-password changes preserve the user's remember-password choice.

Current build/test results and their source manifest are in [offline provisioning validation](../validation/offline-provisioning/results.json). Native UI/radio and owner-camera acceptance remain unverified. No additional fresh-binding capability is advertised.
