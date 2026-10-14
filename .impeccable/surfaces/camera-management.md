# Camera management extension

Mode: Operate. Target: Android camera detail Live/Settings.

Inherit PRODUCT.md and DESIGN.md Material3 roles, Indonesian language, 48dp touch targets, native time inputs, system theme and font scaling. Existing three-tab navigation and video composition remain authoritative. Expandable task groups keep new settings independent of unrelated read failures. Confirm overwrite/delete, calibration and restart; show acceptance/unknown status honestly. No concept or identity replacement is required by this extension.

Earlier-stage visual evidence: existing phone-player-v2.png is historical composition guidance. API35 and the single API26 fallback with KVM ended in emulator boot SIGSEGV; that stage produced no new image. CameraManagementUiTest uses labelled synthetic values and no camera connection; its later physical-device run is recorded below.

Stage2 alarm form: one selected day, native time inputs, draft add/remove and explicit Save/Discard. Preserve existing event metadata; show time/overlap issues near intervals and gate Save. New intervals require supported motion detection. Current UI fixture runtime and visual matrix remain unverified.

Stage3 area editor preserves the Operate identity: native temporary preview, image-local grid, clear selected-cell marks, accessible 48dp row/column/toggle controls, local Use/Cancel and final alarm Save. No image is persisted. PixelCopy and synthetic UI tests compile only; unchanged emulator infrastructure was not retried. No new visual QA result is claimed.

## Settings reference redesign

The owner-approved Settings reference replaces flat settings groups with bordered 20dp charcoal cards, circular mint icons, 16dp outer gutters and 12dp spacing; inherit the existing light theme. Header-only expansion starts closed, permits multiple open cards and is saveable per camera. Keep group errors, busy progress, unsaved drafts and pending restart/calibration visible when closed. Alarm capture/edit prevents collapse. Opening a card is a presentation action, never a camera request.

Native radio pills wrap, device label/value rows stack below 340dp available width or above 1.15× font scale, and all actions retain 48dp targets. The global basic Load action sits below the nine cards, with Save when drafts exist. Basic cards exist without data and show read instructions when expanded. SD progress is verified remaining percentage. Preserve ViewModel drafts, capability guards, confirmations, readback and recovery. Live's preset sheet stays outside this replacement composition.

Direction: Operate, precisely specified owner reference; no concept tournament or generated raster is needed. The memorable interaction is independent disclosure with visible state, not animation. Finish evidence: validation/settings-redesign/results.json and ui-review.md. API37 boot failed with SIGSEGV; a later physical API33 run passed all 11 selected native UI tests with labelled synthetic fixtures, including three CameraManagementUiTest cases. One separate targeted system-bar recapture test also passed. In review-final, 45 matrix/diagnostic comparison captures cover all Settings sections, themes, injected 1.3× type and landscape; three auxiliary closed-state captures may retain readable tap feedback. The final reviewer disposition is ship for the two scored evidence fixes (readable dark-top system-bar icons and 45+3 precision), with no observed regressions from those fixes. Exact owner-image fidelity was not independently verified; comprehensive accessibility and owner-camera acceptance remain unverified.
