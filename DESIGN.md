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

The owner-supplied Home, Add Camera, AP Setup, Live and Settings references replace the former flat green Material composition on those surfaces. Mode: Operate. Retain Indonesian copy, Kotlin/Compose Material 3, native Android navigation, system-selected light/dark themes and font scaling. The accepted plan preserves all existing functions and validation. Recordings retains its own approved composition and palette. No new UI dependency is added.

## Visual system

Dark screens use a blue-black canvas, charcoal cards with subtle one-dp borders, mint primary actions and white text with cool supporting text. Camera badges and the add FAB use purple with white foregrounds; selected protocol choices use the matching tertiary container. The light counterpart uses a pale green canvas, white cards, deep green actions and dark text. Color pairs are explicit Material roles, rather than an incomplete override of primary alone.

Use the Android Material type scale in sp: bold headline roles for page headings, semibold titles for camera names and groups, and body/label roles for addresses, instructions and status. No custom font or fixed-height text boxes. Cards use 20dp corners, video 16dp, 16dp gutters and 12–20dp spacing. Buttons retain native ripple/focus/disabled feedback, pill shapes and at least 48dp interactive targets. Fields keep persistent labels and purpose-appropriate keyboards. Errors use a polite live region.

## Home and profile editor

Home leads with V380 LAN (LAN in mint), diagnostics/search, the Kamera Anda heading and an AP setup card. Saved camera cards show name, address, discovery evidence and a separate edit action. The discovered section carries the existing add/saved behavior and an overflow action for details/edit. Discovery is evidence of a response, not perpetual reachability: labels say Ditemukan or Belum diperiksa, never infer Offline from absence. Changing Wi-Fi clears old discovery results. The purple FAB has a visible Tambah kamera label and reserved list space.

Add/Edit puts camera fields and protocol radio choices in one bordered panel; Port and Device ID have equal width. Protocol choices wrap when necessary. Remember-password and its conditional password controls form a separate panel, followed by a full-width save action. Existing protocol defaults, conditional username, field validation, Keystore behavior, password replacement and delete confirmation remain. Nonsecret drafts survive recreation; passwords are cleared on background/disposal.

## Setup through the camera AP

The initial screen uses a centered camera/radio illustration, centered task title/instructions, a card for SSID and hotspot password with visibility control, Wi-Fi list action, then full-width connection and Android-settings actions and the already-connected option. The raster is decorative, never a feed or proof of a connection. Later phases use the same bordered panels and retain distinct local camera, AP and home-Wi-Fi credentials. API26 keeps the settings/current-hotspot routes; direct network requests remain API29+.

Authentication, send-once Wi-Fi writes, same-device rediscovery/login verification, direct AP use and pending recovery retain their existing contracts. Fresh cloud-dependent pairing is not advertised as supported. The existing authenticated hotspot editor in Settings remains a different task.

## Live and presets

Keep Live/Rekaman/Pengaturan navigation. A rounded native 16:9 SurfaceView stays attached outside the scrolling control column and underneath the modal sheet. Its height is bounded in landscape. Live uses connection, quality and camera-control cards, followed by a compact Preset & patroli entry. The D-pad has circular arrow targets and a central Stop action. Connection/authentication/video-wait/reconnect/error states remain explicit; black video without a rendered frame is not Streaming. No sample scene, invented timestamp, static 1080P label or Rec badge substitutes for telemetry.

Preset & patroli opens a scrollable Material bottom sheet with a close action, slot count/selection, read/save/recall/delete, and the existing inactive-route editor. Overwrite/delete confirmations and draft-discard protection remain. Closing the sheet does not detach video. Start/stop patrol remains unavailable until the motor-status contract is verified.

Manual PTZ no longer depends on opening the preset UI. The ViewModel schedules one read of the existing MOTOR group per authenticated Live session after Streaming, waiting for conflicting work to finish. Authentication revision survives quality changes and changes on reconnect. A failed read has an explicit retry on the control card, with no automatic retry loop. Cancelled reads can be queued again. Stale session/profile replies cannot populate current controls. Streaming, capability/axis checks, maintenance/area-capture guards, inactive-patrol configuration, watchdog renewal and cancellation/release STOP remain authoritative.

## Settings

Settings follows the owner's charcoal/mint card reference with the existing light counterpart. Use 16dp outer gutters, 12dp card spacing, 20dp bordered cards and circular mint section icons. Lead with Pengaturan kamera and its description. Cards appear in this order: Hotspot kamera, Jadwal lampu, Alarm dan deteksi gerakan, Preset dan patroli, Perangkat, Gambar, Lampu, Perekaman, Kartu SD. Only the header toggles expansion; multiple cards may remain open. Expansion starts closed and survives saved-state restoration per camera. Capture/edit of an alarm area forces that card open and disables collapsing.

Collapsed cards show actual summaries; group errors, progress, dirty state and pending recovery remain visible. The hotspot inspection action stays outside the collapsible form. Closing the hotspot form clears hotspot and local-login passwords plus visibility, while retaining the nonsecret SSID draft. Basic and management drafts remain in the ViewModel. Opening a card never reads or writes the camera.

Use pill outlined secondary actions, mint filled primary actions, wrapping radio pills for light/quality, native switches and time input, and 14dp outlined hotspot fields. Settings locally overrides error-container roles to burgundy/pink in dark mode and pale red/dark red in light mode; error notices include a warning icon and polite announcement. Device information uses label/value rows, stacking below 340dp available width or above 1.15× font scale. SD progress represents verified remaining capacity, never invented units. Load remains below the cards; basic Save appears there while drafts exist, independent of expansion. Existing capability/maintenance guards, confirmation, readback and send-once recovery remain authoritative; SD format and patrol activation stay gated.

Diagnostics displays events in reverse insertion order, newest first, while its bounded storage and JSON export remain chronological. No timestamp sorting is used, so events with the same elapsed time retain the intended reverse insertion order.

## Verification and evidence

Native fixtures label synthetic cameras and never imply owner-camera authentication or physical movement. Screenshots cover the four screens and preset sheet in light/dark, 1.3× type and landscape; evidence and test results belong in validation/camera-redesign/results.json. Existing captures in .impeccable/review/phone-*-v2.png predate this redesign and are historical only. Inspect the batched captures once, fix the resulting defects together, then perform at most one confirmation round. Native Compose does not use the HTML/CSS design detector.

Settings validation is recorded separately in validation/settings-redesign/results.json. After an API37 emulator boot ended with SIGSEGV before installation, all 11 selected SettingsUiTest, FeatureUiTest and CameraManagementUiTest cases passed on a physical Android API33 device; see native-final.log. These labelled synthetic fixtures cover disclosure, drafts, locks, secret disposal and diagnostic order. Captures in validation/settings-redesign/review-final comprise 45 matrix/diagnostic comparison images and three auxiliary closed-state images taken after actual taps; the latter may retain readable transient feedback. The matrix covers light/dark, injected 1.3× type, landscape and every section. A separate targeted recapture test passed in native-chrome-confirm.log, replacing dark-top with readable white system-bar icons. The final reviewer disposition is ship for the two scored evidence fixes: system-bar capture and 45+3 evidence precision, with no observed regressions from those fixes. Exact owner-image fidelity was not independently verified; owner-camera acceptance remains untested. Historical Recordings captures demonstrate palette lineage only.
