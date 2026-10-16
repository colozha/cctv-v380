# Camera management extension

Mode: Operate. Target: Android camera detail Live/Settings.

Inherit PRODUCT.md and DESIGN.md Material3 roles, Indonesian language, 48dp touch targets, native time inputs, system theme and font scaling. Existing three-tab navigation and video composition remain authoritative. Expandable task groups keep new settings independent of unrelated read failures. Confirm overwrite/delete, calibration and restart; show acceptance/unknown status honestly. No concept or identity replacement is required by this extension.

Earlier-stage visual evidence: existing phone-player-v2.png is historical composition guidance. API35 and the single API26 fallback with KVM ended in emulator boot SIGSEGV; that stage produced no new image. CameraManagementUiTest uses labelled synthetic values and no camera connection; its later physical-device run is recorded below.

Stage2 alarm form: one selected day, native time inputs, draft add/remove and explicit Save/Discard. Preserve existing event metadata; show time/overlap issues near intervals and gate Save. New intervals require supported motion detection. Current UI fixture runtime and visual matrix remain unverified.

Stage3 area editor preserves the Operate identity: native temporary preview, image-local grid, clear selected-cell marks, accessible 48dp row/column/toggle controls, local Use/Cancel and final alarm Save. No image is persisted. PixelCopy and synthetic UI tests compile only; unchanged emulator infrastructure was not retried. No new visual QA result is claimed.

## Settings simplification, 16 October 2026

The approved simplification replaces nine individual cards with three initially closed task cards: image/lights, recording/SD card, and connection/device. Internal subheadings/dividers keep the established mint/charcoal and light theme without nested cards. A single bottom panel contains independent rows for light schedules, alarm/motion and presets/patrol, each with Material Build and Belum berfungsi / Not working yet. This designation follows the owner report and leaves supported controls and guards accessible. Live's preset sheet is unchanged.

Keep errors, progress, drafts and pending AP/restart/calibration/SD recovery visible while closed. Alarm capture/edit prevents collapse. Basic Save appears before the unfinished panel; opening groups performs no camera I/O. Connection disclosure is saveable per camera. Its collapsed content clears passwords but preserves the SSID; inspection/permissions appear only while expanded. Current synthetic validation and limits: validation/settings-simplified/results.json. Previous nine-card/API33 captures in validation/settings-redesign are historical source-specific evidence.
