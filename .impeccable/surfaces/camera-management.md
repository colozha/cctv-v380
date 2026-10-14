# Camera management extension

Mode: Operate. Target: Android camera detail Live/Settings.

Inherit PRODUCT.md and DESIGN.md Material3 roles, Indonesian language, 48dp touch targets, native time inputs, system theme and font scaling. Existing three-tab navigation and video composition remain authoritative. Expandable task groups keep new settings independent of unrelated read failures. Confirm overwrite/delete, calibration and restart; show acceptance/unknown status honestly. No concept or identity replacement is required by this extension.

Visual evidence: existing phone-player-v2.png is historical composition guidance. API35 and the single API26 fallback with KVM ended in emulator boot SIGSEGV; no new image is claimed. CameraManagementUiTest uses labelled synthetic values and no camera connection, but has only been compiled.

Stage2 alarm form: one selected day, native time inputs, draft add/remove and explicit Save/Discard. Preserve existing event metadata; show time/overlap issues near intervals and gate Save. New intervals require supported motion detection. Current UI fixture runtime and visual matrix remain unverified.

Stage3 area editor preserves the Operate identity: native temporary preview, image-local grid, clear selected-cell marks, accessible 48dp row/column/toggle controls, local Use/Cancel and final alarm Save. No image is persisted. PixelCopy and synthetic UI tests compile only; unchanged emulator infrastructure was not retried. No new visual QA result is claimed.
