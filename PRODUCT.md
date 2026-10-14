# V380 LAN

<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
Confirmed: Kotlin, Jetpack Compose Material 3, coroutines, MediaCodec; one app module.

## Users and purpose
Camera owner setting up local access, viewing one V380 live stream, browsing SD recordings, saving video and changing supported camera settings, and controlling supported PTZ on the same Wi-Fi LAN or a direct camera AP connection.

## Capabilities and constraints
Multiple saved profiles; one foreground stream. The debug build contains experimental Legacy V2 and IoT LAN adapters for discovery, local authentication, video and PTZ, plus manual profile entry and sanitized diagnostics/export. SD/HD and directional PTZ/stop depend on the session state and camera capabilities; controls enable only after video reaches Streaming. MediaCodec renders video to a SurfaceView. Release builds do not enable these experimental adapters.

AP setup separates the camera hotspot, local camera access and home-Wi-Fi credentials. It authenticates the camera before sending Wi-Fi configuration, supports direct AP use, and requires rediscovery of the same device and successful local login before completing home-Wi-Fi setup. The observed fresh new-platform PIN-binding path requires a cloud-issued owner binding token and is not implemented by this LAN client; authenticated LAN password changes are a separate path. Optional password storage uses Android Keystore and is off by default. Indonesian UI and system light/dark theme remain. IoT recording list/playback and single-segment video-only MP4 export are experimental debug features. Camera settings read actual property values and require write readback; supported controls cover orientation, lights and recording. SD status/capacity are readable; format remains disabled until terminal status semantics are verified. Legacy recording/settings, audio export, remote/cloud/P2P, grids and presets are deferred. Native tests for these new screens/export compile but could not run because the emulator crashed during boot; camera tests remain deferred.

Experimental debug AP editing is available in camera settings after successful local authentication, explicit SoftAP capability, direct AP network/identity checks and readback. A single write is followed by reconnect and verification; durable nonsecret recovery never replays the write. AP credentials remain separate from camera login and are not persisted. Legacy/unknown profiles remain gated. Native runtime and owner-camera acceptance remain unverified.

## Evidence and success
analysis/REPORT.md and the linked wire/provisioning specifications own protocol findings and unknowns. Experimental wire paths are implemented in the debug build; owner-camera interoperability has not passed acceptance. Success still requires the owner camera to complete discovery/authentication, decoded live video, supported SD/HD switching and PTZ, and any requested AP-to-LAN setup. Synthetic tests and emulator screenshots demonstrate only their stated software/UI scope. The captured profile is explicitly synthetic; no live camera success is implied. No APK modification, account-token extraction, credential logging, or proprietary native library reuse.

## Brand commitments
Material 3 platform conventions and accessibility; a compact camera list and foreground player.
