# V3 receive-loop and timing-feedback evidence

Offline supplementary analysis of the shipped V380 6.4.90 arm64 library. No APK/library execution, camera connection, network probe, credentials, runtime session material, or footage was used. The exact cause of the reported disconnect remains unobserved.

Source REA extraction: `ev_f2eaec0ee202bdfaab2d678c927f134eae42d7df9523d26a818a0c13cba2959d`. APK SHA-256: `7364691ba08427fd95b391c2b398aae66cd80eeb19c5bfd7aeaaba405dd2cc0b`. Library `lib/arm64-v8a/libhsMediaLibrary.so` SHA-256: `1375485cd32a15cba93f8f5b9d9991eefaa6f1dd0fabe00aefd955b6808763bc`. Analysis tools: Capstone 5.0.6 and GNU readelf, reading matching extracted bytes and ELF relocations. Addresses below are ELF virtual addresses, not ASLR runtime addresses. Selected `.text` addresses equal file offsets. These observations are not REA native decompiler output; no native decompiler provider was configured.

## Sparse video pattern: chunk and gap may exceed payload size

`iotc_ms_frame_cipher` (`0x9394c`, size 564):

- `0x93974`: load declared encrypted chunk into `w20`; `0x9397c`: load remaining payload into `w23`.
- `0x93980`: a zero chunk goes directly to the unchanged-tail/finalization path.
- Video prefix advancement is `0x939e0..0x93a24`. It advances by the declared prefix without clamping. Retain a prefix-within-payload check for memory safety.
- `0x93a28 cmp w23,w20`; `0x93a2c b.hs 0x93ad0`. The cipher loop starts only when a complete chunk remains. A payload shorter than the configured chunk is left untouched by `0x93a30..0x93a7c`.
- After a chunk, `0x93b20` loads the configured plaintext gap; `0x93b34` compares it with remaining bytes; `0x93b40 csel w8,w23,w11,lo` chooses `min(remaining,gap)`.
- Encryption pattern construction at `0x936f4..0x9375c` depends on randomness, not compressed-frame size. It masks the chunk with `0x3f0` at `0x9373c`, the gap with `0x70` at `0x9374c`, and adds four to the prefix candidate at `0x93750`.

Therefore, requiring `chunk <= payloadSize` or `gap <= payloadSize` rejects patterns accepted by the vendor loop. This can plausibly surface on a small delta frame after larger frames have worked. That timing explanation is an inference, not an observation of the owner's camera. Keep nonnegative/resource bounds and cipher alignment. Audio uses a separate single-chunk branch at `0x93984..0x939dc`; its declared encrypted chunk is processed directly and still needs to fit the payload safely.

## Incoming commands, framing, and ignored packets

`HSLiveDataV3Transmitter::getDataFromDeviceOrServer` (`0x4f554`, size 19988):

- `0x50eb8..0x50ec8` loads command, version, security, and declared body size. Version is logged by `0x50ecc..0x50f34`; no receive-side `version == 1` comparison was found in this function.
- `0x50f40 cbz w23,0x51158` permits a zero body at the framing stage and skips its receive loop. This does **not** establish a zero-body heartbeat format.
- Command comparisons at `0x50958..0x5098c`, `0x50b24..0x50b30`, and `0x51170..0x5117c` select JSON `939722776` (`0x38030818`), media `939722784` (`0x38030820`), notification `939722786` (`0x38030822`), and shutdown `199056` (`0x30990`). No additional incoming command was found in this bounded receiver.
- Unknown-command and invalid/empty JSON paths report an error and continue at `0x511a0..0x511b4 -> 0x52ec4..0x52ed0 -> 0x506a8`. They do not inherently become authentication failure.
- Shutdown goes through `0x53cc4..0x53d0c`, sets channel status `-2`, and leaves the connection path.
- Notification method `197124` (`0x30204`) branches at `0x50d08..0x50d14` to `parseLiveMediaInfo`/media callback at `0x50df4..0x50e24`. Repeated information notifications are parsed by the same branch.

Rejecting an unknown packet, unexpected version, or invalid JSON may be a deliberate independent-client policy. Such failures should retain their packet/protocol diagnostic category rather than being labeled authentication failure. No evidence here authorizes treating all unknown input as valid or removing allocation limits.

## Notification-driven timing feedback, method 197120

The vendor has a feedback path in addition to live-control method 197122. This is concrete notification-driven behavior, not evidence for an invented periodic heartbeat.

1. Notification header method `197120` (`0x30200`) is selected at `0x50cf4..0x50d04` and handled at `0x51584`.
2. `0x51584..0x515b0` selects `params.tms1`, loads the cJSON numeric `double` at object offset `0x30`, and stores that number unchanged. The exact key addresses are `params` at `0x293a2`, `tms1` at `0x2cb7b`. This is a JSON number, not an established signed-32-bit field.
3. `0x515b4..0x515c8` calls handle vtable slot `+0x30` and saves its signed 64-bit return. When no handle exists, the stored default is retained.
4. The immediate feedback builder `0x51a4c..0x51af0` constructs `params:{flag:2,tms1:<copied number>,decode_tms:<callback value>}`. `flag` key is at `0x1da01`; `decode_tms` key is at `0x2adb3`. `0x51ae4..0x51aec` puts method `197120` into the ordinary `initIotcReq` call. Request arguments include version 1, security 2, current session, magic zero, channel native-index-plus-one, and a fresh random request ID. The send is at `0x51b64`.
5. An alternate pending-feedback path in `actionCtrlThreadFunc` builds the same parameters at `0x4ba18..0x4baa8`; `0x4bba4` clears its pending byte after a successful send. The immediate path is selected when that byte is zero at `0x515cc..0x515d0`.

The copied `tms1` value is opaque for independent implementation purposes: preserve its finite numeric value rather than assigning it a guessed unit or substituting local time. Static analysis does not establish whether a particular firmware requires this feedback to keep a stream alive.

Receive gating is directional: this handler is under incoming `COMMAND_NOTIFY` at `0x50c44`, while outgoing `initIotcReq` uses `COMMAND_IOT`. There is no `params.flag == 1` comparison in `0x51584..0x515d4`. The native code checks the existing live-request pointer at `0x51594..0x51598`, copies `tms1` only when present, and otherwise retains its prior/default value; it then either sends immediately or leaves the pending byte for the control thread. For an independent client, a narrower safe rule is to reply only while streaming to notification command plus method 197120 plus a finite numeric `params.tms1`, and to ignore ordinary IoT responses with that method. Requiring a flag-one challenge would be an invented additional constraint; replying to an ordinary IoT response would broaden the native receive behavior.

## What decode_tms means

The callback is **not a playback timestamp or monotonic receipt timestamp**.

- `_ZTV12HSLivePlayer` is at `0xb8fb8`, with its address point at `0xb8fc8`. The relocation at `0xb8ff8`, address point `+0x30`, targets `HSLivePlayer::getDecodeDiffTime` at `0x87f48`.
- `0x87f48 ldr x0,[x0,#0x590]; 0x87f4c ret` returns the first view's signed 64-bit timing accumulator. Base `HSBaseTransmitterHandle::getDecodeDiffTime` at `0x3c994` returns zero.
- `videoDecodeThreadFunc` (`0x8610c`, size 5396) initializes the per-view accumulator to zero at `0x8617c..0x86188`. The pointer `this + view*8 + 0x590` is saved at `0x861d8..0x861f0`.
- `0x86b64..0x86b74` computes the difference between current and prior compressed-frame timestamps. `0x86b80..0x86bd4` subtracts the prior decode cost, combines the result with the old accumulator, and stores zero when both the old accumulator and new difference are nonnegative; otherwise it stores their sum.
- The decoder duration is measured around `decodeFrameData`: `gettimeofday` at `0x86cf0`, decode call at `0x86d00`, `gettimeofday` at `0x86d10`. `0x86d14..0x86d50` calculates seconds difference times 1000 plus microseconds difference divided by 1000, yielding milliseconds.

Thus `decode_tms` is a signed millisecond accumulator of decode timing deficit relative to incoming frame cadence. Zero is a concrete vendor initial/base value. An implementation without this measurement can use the actual initial/default zero; substituting a monotonic clock or compressed-frame PTS has no basis in this callback. Reproducing the ongoing accumulator would require matching frame timestamps and measured decoder work, not merely tracking received frames.

## Limits

The bounded static receiver and control loop did not reveal an unconditional periodic heartbeat/renew-live timer. The receiver does resend live control after a conditional no-response interval (`0x508ec..0x50920` branches to `0x501ac` after elapsed time greater than four seconds, and leaves after greater than nine seconds); the condition is tied to response state. Control-loop timers at `0x4bbb4..0x4bc94` are gated requests/retries for capability/status/live changes. Their presence does not prove a required fixed keepalive interval.

Actual triggering packet type, camera-side disconnect cause, feedback requirement, and rendered timing behavior remain unknown without separately authorized runtime evidence. The independently established sparse-pattern rejection can be fixed and checked with synthetic small-frame vectors without camera tests.
