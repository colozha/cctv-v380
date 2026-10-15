# Agent Guide

## Project at a glance

V380 LAN is an independent Android client for V380 cameras over a local network or a direct camera access point. It is a single-module Kotlin application built with Jetpack Compose Material 3, Kotlin Coroutines, and Android `MediaCodec` for video decoding. Experimental camera protocol adapters are enabled in debug builds; camera and firmware compatibility must not be described as verified unless the repository contains current evidence for that exact claim.

## Stack and build configuration

- Gradle project: one Android application module, `:app`.
- Android Gradle Plugin: `9.1.1`; Kotlin and Compose compiler plugin: `2.2.10`.
- Java compatibility: 17; Android `minSdk`: 26; `targetSdk`: 37; `compileSdk`: 37.
- UI: Jetpack Compose Material 3 and Android lifecycle/ViewModel integration.
- Async work: Kotlin Coroutines; video decode/render: `MediaCodec` and Android `SurfaceView`.
- Protocol crypto includes Bouncy Castle. See `app/build.gradle.kts` for the authoritative dependency list and versions.

## Repository map

- `app/src/main/java/dev/cctv/v380/` — application source, grouped by responsibility below.
- `app/src/test/java/` — JVM unit tests for protocol, session, transport, diagnostics, storage, and media behavior.
- `app/src/androidTest/java/` — Android instrumentation and UI tests, including synthetic video and storage fixtures.
- `app/src/main/res/` — Android resources and manifest.
- `analysis/` — protocol research, wire specifications, evidence index, implementation plans, and known protocol limits.
- `validation/` — build/test logs, machine-readable results, UI reviews, screenshots, and runtime evidence. Treat these as dated records, not proof that the current source passed unless the result identifies the matching source/revision.
- `tools/` — validation helper scripts.
- `readme.md`, `PRODUCT.md`, `DESIGN.md`, `IMPLEMENTATION.md` — usage, product scope, UI design, and implementation status.

## Feature map

| Area | Main source locations | Responsibility |
|---|---|---|
| App entry, navigation, and camera list | `ui/MainActivity.kt`, `ui/CameraViewModel.kt` | Compose app entry, screen routing, permission flow, camera profile and feature state coordination. |
| Profiles and secrets | `ui/ProfileStore.kt`, `ui/SecretStore.kt` | Persist camera profiles and optionally protect local camera credentials with Android Keystore. |
| LAN and Wi-Fi | `transport/WifiNetwork.kt`, `transport/TcpChannel.kt`, `transport/ExactReader.kt`, `discovery/LanDiscovery.kt`, `discovery/DiscoveryParser.kt` | Select/bind Android networks, perform bounded TCP I/O, scan the LAN, and parse discovery responses. |
| Camera session and diagnostics | `session/CameraSession.kt`, `session/CameraModels.kt`, `session/Diagnostics.kt` | Authentication/session lifecycle, capabilities, media state, and sanitized diagnostics. |
| Protocol adapters | `protocol/` | Legacy LAN, IoT key exchange/control/media, camera settings/management, recordings, AP setup, and area commands. Consult the matching `analysis/*-wire.md` specification before changing wire behavior. |
| Live video and PTZ | `media/VideoDecoder.kt`, `media/AnnexB.kt`, `control/PtzWatchdog.kt`, `ui/LivePlayerScreen.kt` | AVC/HEVC frame handling, Android decoding/rendering, and capability/session-gated directional movement with stop handling. |
| AP provisioning and hotspot editing | `provisioning/ProvisioningManager.kt`, `ui/ProvisioningScreen.kt`, `ui/ApHotspotSettings.kt`, `protocol/ApProtocol.kt`, `protocol/ApHotspotProtocol.kt` | Direct AP setup, authenticated Wi-Fi configuration, and separately gated hotspot edits with recovery and verification. |
| Recordings and export | `session/RecordingReplay.kt`, `protocol/RecordingProtocol.kt`, `media/RecordingExporter.kt`, `ui/RecordingsScreen.kt`, `ui/RecordingPresentation.kt` | IoT recording listing/playback and single-segment video-only export to Android storage. |
| Camera settings and management | `protocol/CameraSettingsProtocol.kt`, `protocol/CameraManagementProtocol.kt`, `protocol/CameraAreaProtocol.kt`, `ui/CameraSettingsScreen.kt`, `ui/CameraManagementControls.kt`, `ui/CameraAreaEditor.kt` | Supported settings, alarm/light schedules, presets/patrol configuration, device actions, and the confirmed flat-area editor. |
| Diagnostics UI | `ui/DiagnosticsScreen.kt` | Display and export sanitized LAN diagnostics. |

## Source-of-truth documentation

Read the relevant documents before changing behavior:

- [`readme.md`](readme.md) — user workflows, build/install instructions, and feature limits.
- [`PRODUCT.md`](PRODUCT.md) — platform, audience, capabilities, and product constraints.
- [`IMPLEMENTATION.md`](IMPLEMENTATION.md) — implementation status, verification evidence, and unfinished work.
- [`DESIGN.md`](DESIGN.md) — approved UI behavior and visual/accessibility guidance.
- [`analysis/REPORT.md`](analysis/REPORT.md) and [`analysis/evidence-index.md`](analysis/evidence-index.md) — protocol research and evidence provenance.
- Relevant wire specs include [`analysis/legacy-wire-spec.md`](analysis/legacy-wire-spec.md), [`analysis/iot-lan-protocol.md`](analysis/iot-lan-protocol.md), [`analysis/ap-provisioning.md`](analysis/ap-provisioning.md), [`analysis/recordings-settings-wire.md`](analysis/recordings-settings-wire.md), [`analysis/camera-management-wire.md`](analysis/camera-management-wire.md), and [`analysis/sd-maintenance-wire.md`](analysis/sd-maintenance-wire.md).

Some documents include historical results and dated constraints. Check their dates and source/revision scope before relying on a validation result. Do not turn static protocol evidence, synthetic fixtures, successful build output, or an ACK into a claim of owner-camera compatibility or completed physical action.

## Working rules

- Keep protocol families and their framing, authentication, crypto, and media rules separate. Do not generalize a behavior from one firmware family without evidence.
- Preserve capability checks, session/device identity guards, readback, send-once recovery, and disabled states where protocol outcomes remain uncertain. In particular, SD format/delete and patrol start/stop are gated; consult `IMPLEMENTATION.md` and their wire specs before touching them.
- Keep camera login, camera AP, and home Wi-Fi credentials distinct. Do not log, commit, export, or include credentials, auth payloads, keys, session identifiers, or video footage in diagnostics or fixtures. Maintain the existing sanitized diagnostic allowlist.
- Use synthetic fixtures for automated protocol/UI coverage. Do not contact a physical camera or perform a camera write, restart, format, or delete operation as part of routine validation unless the task explicitly requires it.
- Preserve Indonesian UI copy, system light/dark theming, accessibility semantics, and existing user-facing flows unless the task asks to change them.
- Update the relevant status/specification documentation when implementation or verification status changes. Report only checks actually run and state when a result is historical, synthetic, or still unverified.

## Build and validation

Use the Gradle wrapper from the repository root. A standard local build and static check is:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Run JVM unit tests when the change calls for them:

```bash
./gradlew :app:testDebugUnitTest
```

For the project's selected offline regression suite, inspect [`validation/offline-build-command.txt`](validation/offline-build-command.txt); it records the exact filters and environment used for that validation. Android instrumentation requires a compatible emulator or attached device and should only be reported as passed when it actually ran. The connection-validation script can contact network devices; inspect `tools/run-android-validation.sh` and its requirements before using it.
