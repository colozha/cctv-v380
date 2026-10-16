---
name: V380 LAN
description: Native Android camera UI matching the supplied mint and charcoal references.
colors:
  dark-background: "#080E10"
  dark-card: "#131B20"
  dark-primary: "#82DEB8"
  dark-on-primary: "#06291D"
  dark-text: "#F3F4F8"
  dark-supporting: "#B9BBCB"
  dark-border: "#2E3842"
  light-background: "#F5F8F6"
  light-card: "#FFFFFF"
  light-primary: "#185C43"
  light-text: "#17231E"
  camera-purple: "#6531CF"
  camera-on-purple: "#FFFFFF"
rounded:
  card: "20dp"
  video: "16dp"
  protocol: "14dp"
spacing:
  gutter: "16dp"
  related: "12dp"
  group: "20dp"
---

# Design System: V380 LAN

## Authority and platform

The owner-supplied Home, Add Camera, AP Setup, Live and Settings references replace the former flat green Material composition on those surfaces. Mode: Operate. Use resource-backed Indonesian and English copy, Kotlin/Compose Material 3, native Android navigation, app-selected light/dark themes (system theme until a choice is saved), and font scaling. The accepted plan preserves all existing functions and validation. Recordings retains its own approved composition and palette. No new UI dependency is added.

## Visual system

Dark screens use a blue-black canvas, charcoal cards with subtle one-dp borders, mint primary actions and white text with cool supporting text. Camera badges and the add FAB use purple with white foregrounds; selected protocol choices use the matching tertiary container. The light counterpart uses a pale green canvas, white cards, deep green actions and dark text. Color pairs are explicit Material roles, rather than an incomplete override of primary alone.

Use the Android Material type scale in sp: bold headline roles for page headings, semibold titles for camera names and groups, and body/label roles for addresses, instructions and status. No custom font or fixed-height text boxes. Cards use 20dp corners, video 16dp, 16dp gutters and 12–20dp spacing. Buttons retain native ripple/focus/disabled feedback, pill shapes and at least 48dp interactive targets. Fields keep persistent labels and purpose-appropriate keyboards. Errors use a polite live region.

## Home and profile editor

Home leads with V380 LAN (LAN in mint), app Settings and diagnostics/search actions, the Kamera Anda heading and an AP setup card. Saved camera cards show name, address, discovery evidence and a separate edit action. The discovered section carries the existing add/saved behavior and an overflow action for details/edit. Discovery is evidence of a response, not perpetual reachability: labels say Ditemukan or Belum diperiksa, never infer Offline from absence. Changing Wi-Fi clears old discovery results. The purple FAB has a visible Tambah kamera label and reserved list space.

Add/Edit puts camera fields and protocol radio choices in one bordered panel; Port and Device ID have equal width. Protocol choices wrap when necessary. Remember-password and its conditional password controls form a separate panel, followed by a full-width save action. Existing protocol defaults, conditional username, field validation, Keystore behavior, password replacement and delete confirmation remain. Nonsecret drafts survive recreation; passwords are cleared on background/disposal.

## Setup through the camera AP

The initial screen uses a centered camera/radio illustration, centered task title/instructions, a card for SSID and hotspot password with visibility control, Wi-Fi list action, then full-width connection and Android-settings actions and the already-connected option. The raster is decorative, never a feed or proof of a connection. Later phases use the same bordered panels and retain distinct local camera, AP and home-Wi-Fi credentials. API26 keeps the settings/current-hotspot routes; direct network requests remain API29+.

Authentication, send-once Wi-Fi writes, same-device rediscovery/login verification, direct AP use and pending recovery retain their existing contracts. Fresh cloud-dependent pairing is not advertised as supported. The existing authenticated hotspot editor in Settings remains a different task.

## Live and presets

Insert a Bicara ke kamera panel after video quality and before directional camera controls, using the existing card, type and button system. Expose microphone off/starting/active/stopping, readable errors and capability/format gates. Mulai bicara requests microphone permission if necessary; a fresh tap after permission is required. Starting offers cancellation and active capture always exposes Berhenti bicara even while other controls are busy. Native pause and rotation stop capture; do not restore microphone state. Status uses a polite live region and controls retain48dp targets, Indonesian/English resources, large type and both themes. Mikrofon aktif describes local capture/transmission, never a verified camera speaker outcome. No waveform or audio visualization is derived from real microphone content. Synthetic panel captures and behavior evidence belong in validation/talkback/.

Keep Live/Rekaman/Pengaturan navigation. A rounded native 16:9 SurfaceView stays attached outside the scrolling control column and underneath the modal sheet. Its height is bounded in landscape. Live uses connection, quality and camera-control cards, followed by a compact Preset & patroli entry. The D-pad has circular arrow targets and a central Stop action. Connection/authentication/video-wait/reconnect/error states remain explicit; black video without a rendered frame is not Streaming. No sample scene, invented timestamp, static 1080P label or Rec badge substitutes for telemetry.

Entering Live, Rekaman or Pengaturan automatically connects with the saved local camera password after any tab transition finishes. Cancelled transitions and transitions for a different profile cannot trigger a connection. Leaving Live or Rekaman closes the camera session, including a switch between the two; returning from a recording player to its list stays in Rekaman. If no password is saved, show the credential form immediately; otherwise show Sambungkan whenever disconnected and no connection attempt is active. The connection spinner represents Connecting, Authenticating or Reconnecting only; Idle never implies an active connection.

Preset & patroli opens a scrollable Material bottom sheet with a close action, slot count/selection, read/save/recall/delete, and the existing inactive-route editor. Overwrite/delete confirmations and draft-discard protection remain. Closing the sheet does not detach video. Start/stop patrol remains unavailable until the motor-status contract is verified.

Manual PTZ no longer depends on opening the preset UI. The ViewModel schedules one read of the existing MOTOR group per authenticated Live session after Streaming, waiting for conflicting work to finish. Authentication revision survives quality changes and changes on reconnect. A failed read has an explicit retry on the control card, with no automatic retry loop. Cancelled reads can be queued again. Stale session/profile replies cannot populate current controls. Streaming, capability/axis checks, maintenance/area-capture guards, inactive-patrol configuration, watchdog renewal and cancellation/release STOP remain authoritative.

## Settings

The Settings tab uses three initially closed task cards, in order: Gambar & lampu (orientation, white light and indicator), Rekaman & kartu SD (recording controls, SD status/capacity and gated format), and Koneksi & perangkat (hotspot inspection/editing, device information and restart). Use simple subheadings and dividers inside each card, without nested cards. Keep the established mint/charcoal and light counterpart, 16dp gutters, 12dp spacing, native forms and 48dp interactive targets. Header, connection status and Muat ulang precede notices and cards; remove the redundant introduction. Basic settings retain automatic loading on entering Settings for the same authenticated camera and no automatic retries.

Below the main cards and conditional basic Save/cancel actions, one panel under Belum berfungsi contains three independently expandable rows: Jadwal lampu, Alarm dan deteksi gerakan, and Preset dan patroli. Each row has a Material Build icon and a persistent Belum berfungsi label. This classification is the owner's report, not an inferred firmware capability or a synonym for Belum diperiksa. Controls remain accessible with existing capability/maintenance gates. Live's preset sheet remains unchanged.

Errors, progress, unsaved drafts and pending AP/restart/calibration/SD recovery remain visible when their group is closed. Opening a disclosure never reads or writes the camera. Disclosure state is saveable per profile; basic and management drafts stay in the ViewModel. Alarm capture/edit prevents collapse. Hotspot inspection/permission actions and inputs appear only inside the expanded connection group; closing it clears hotspot/local-login passwords and visibility while preserving the nonsecret SSID draft. Recovery notices and cancellation remain visible when closed.

Basic Save remains outside the groups while drafts exist, before the unfinished panel. Existing confirmation, authentication, capability checks, readback and send-once recovery remain authoritative. Format SD and patrol activation stay gated. SD progress represents verified remaining percentage; device values stack at narrow widths or large font scales. New titles and labels use Indonesian/English resources. Settings locally preserves its burgundy/pink error roles and polite announcements. Current validation and runtime limits are recorded in validation/settings-simplified/results.json; earlier settings-redesign captures show the previous nine-card layout.

Diagnostics retains newest-first display and chronological bounded storage/JSON export.

## Recording player controls

The focused recording player uses one icon-only row in this order: previous, play/pause, stop, next, download. Play/pause is a filled Material icon button; the other four are outlined. Icons are 24dp with 48dp touch targets and at least 8dp spacing at phone widths of 320dp and above. Only the player's information panel reduces horizontal padding from 20dp to 8dp when its available width is below 312dp. Preserve the existing Recordings palette and light/dark themes. Each icon has an Indonesian/English action description; play/pause updates its description with the playback state.

Previous/next follow the displayed list order and active date/filter, request playback from the target segment's start even after pause, and retain authentication/busy/export/hotspot gates. Disable missing neighbors, including both when the selected segment is absent from the loaded list. Do not wrap, cross dates, or advance automatically at segment completion. Stop, seek, export and the attached video Surface retain their existing flows. Current offline evidence and runtime limits are in validation/recording-controls/results.json; earlier screenshots show the previous labelled controls.

## Verification and evidence

SD maintenance recovery keeps its status notice visible when the Kartu SD section is collapsed. When expanded, a pending note exposes Periksa status kartu and Tutup catatan format even before settings are loaded; reading requires the matching authenticated camera. Closing asks for confirmation and states that the outcome is still unknown and the camera operation is not cancelled. Format remains disabled, including during export; per-recording delete explains that the app does not yet support it. New UI fixtures cover light/dark, large type, landscape and confirmation, but are not visually verified because the API37 emulator failed boot. Current evidence is in validation/sd-maintenance/results.json.

Native fixtures label synthetic cameras and never imply owner-camera authentication or physical movement. Screenshots cover the four screens and preset sheet in light/dark, 1.3× type and landscape; evidence and test results belong in validation/camera-redesign/results.json. Existing captures in .impeccable/review/phone-*-v2.png predate this redesign and are historical only. Inspect the batched captures once, fix the resulting defects together, then perform at most one confirmation round. Native Compose does not use the HTML/CSS design detector.

Historical validation of the previous nine-card Settings layout is recorded in validation/settings-redesign/results.json; current layout evidence is in validation/settings-simplified/results.json. After an API37 emulator boot ended with SIGSEGV before installation, all 11 selected SettingsUiTest, FeatureUiTest and CameraManagementUiTest cases passed on a physical Android API33 device; see native-final.log. These labelled synthetic fixtures cover disclosure, drafts, locks, secret disposal and diagnostic order. Captures in validation/settings-redesign/review-final comprise 45 matrix/diagnostic comparison images and three auxiliary closed-state images taken after actual taps; the latter may retain readable transient feedback. The matrix covers light/dark, injected 1.3× type, landscape and every section. A separate targeted recapture test passed in native-chrome-confirm.log, replacing dark-top with readable white system-bar icons. The final reviewer disposition is ship for the two scored evidence fixes: system-bar capture and 45+3 evidence precision, with no observed regressions from those fixes. Exact owner-image fidelity was not independently verified; owner-camera acceptance remains untested. Historical Recordings captures demonstrate palette lineage only.


## Offline AP setup refinement, 15 October 2026

Keep the approved AP setup illustration, native panels, mint/charcoal palette, theme behavior and 48dp targets. Show four numbered task stages: hotspot, local access, router selection, verification. Explain internet-free router use and name the three credential purposes consistently. Keep one primary action per phase; direct AP remains secondary, while optional LAN-password changes sit behind Local access options and preserve the remember choice. Put errors and recovery beside the current task. Show the cloud-binding limitation inline without promising fresh offline binding.

Recovery exposes Android Wi-Fi Settings, camera verification, and clear-local-progress actions. A readable wrong SSID instructs the user to choose the target router. An unreadable SSID exposes a fresh, non-persisted confirmation checkbox; verification stays disabled until checked and still requires matching camera identity plus login. Clear the checkbox after submission/background. Explain that clearing progress does not undo camera settings. Success describes identity/login only; Live owns rendered-video status. Copy and accessibility text use Indonesian/English resources. Native synthetic fixtures cover recovery and permission/secret behavior; runtime captures are not verified in this task because no Android device is attached. Evidence is in validation/offline-provisioning/results.json.
