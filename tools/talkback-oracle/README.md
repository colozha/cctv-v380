# Offline talkback oracle

Standalone Android tool, never a dependency of `:app`. Requires ARM64 Android API26+, NDK29.0.14206865 and the exact local V3806.4.90 reference APK. The APK/libraries are not committed or downloaded by this project. `prepare.py` verifies hashes before extracting libraries into ignored build output.

The tool has **zero permissions**, a separate package/UID, no Activity, no shared storage, no player, no account/profile input, and no vendor Java/JNI initialization. It calls the exported encoder2001 and media cipher/envelope helpers through a small JNI shim. All input is generated synthetically, including the public key bytes0..31 and session17. This is not an observation of which transport/codec a live official-app session selects.

From the repository root:

```sh
./gradlew -p tools/talkback-oracle assembleDebug -PvendorApk=/path/to/v380-6-4-90.apk
adb install -r tools/talkback-oracle/build/outputs/apk/debug/TalkbackOracle-debug.apk
adb shell am instrument -w dev.cctv.v380.oracle/.OracleRunner
adb exec-out run-as dev.cctv.v380.oracle cat files/vectors.json
```

Check instrumentation reports `oracle=synthetic vectors completed` before exporting. Native crashes, missing symbols, dependency errors and nonzero permission counts are failures, not empty passing fixtures. Capture output only from this package; never export the production app's files, raw network captures or microphone audio.

Vectors contain silence, ramp, tone, constant, consecutive blocks, encoder restart and silence after signal. Each includes synthetic PCM, native ADPCM, and complete PCM/ADPCM media packets. Sequence is vector index+1; timestamp is1000+index×32ms. The timing value is a deterministic cipher input, not a pacing recommendation. Compare bytes directly with `TalkbackVendorVectorTest`. The native factory retains a process-global ADPCM step index after release/init; the continuous-state comparison intentionally reproduces that sequence. A separate test checks that a fresh client attempt starts at index0 and matches a native index0 block. This known state-ownership difference is not silently counted as byte equality or as the physical fault. Runtime vendor results do not establish camera speaker compatibility.

For physical acceptance, ordinary codec102 talkback remains blocked. Do not use this offline APK as a camera/audio test tool or relax its permissions. A bounded camera validation build may be prepared only after packet and transport evidence passes. It must require a user tap, send at most15 silence frames/500ms first, have an independent cancellation deadline, and stop on the first reported noise. Voice testing is a separate user action limited to2s. No codec sweep or automatic retry is permitted.


Current recorded run: Android API35 x86_64 with ARM64 native bridge,7 vectors/14 complete packets, repeated identically from a fresh process. See `validation/talkback-oracle/results.json`. On this host SwiftShader crashed during boot; `-gpu host -feature -Vulkan` allowed the task-owned API35 emulator to boot. No physical ARM64 run or camera/microphone acceptance is claimed.
