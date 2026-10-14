# AP hotspot design documentation

## Outcome

Generic-agent substitution for the Impeccable documenter role. This is a source-only documentation check for an ordinary extension of the incumbent native Android Material 3 world.

No changes to `DESIGN.md` or `.impeccable/design.json`: the implemented surface introduces no durable palette, type, radius, elevation, motion or asset rule. Existing settings, fields, actions, status and state-gating guidance still applies. Task-specific composition remains in `.impeccable/surfaces/ap-hotspot.md`; product capability and verification limits remain in `PRODUCT.md`.

## Evidence checked

- `DESIGN.md` and `.impeccable/design.json`, including the inherited Material role warning and historical capture limitations.
- `.impeccable/surfaces/ap-hotspot.md` and `PRODUCT.md`.
- `app/src/main/java/dev/cctv/v380/ui/MainActivity.kt`: system-selected light/dark themes, unchanged green primary/secondary and surface overrides, inherited Material typography and remaining color roles.
- `app/src/main/java/dev/cctv/v380/ui/ApHotspotSettings.kt`: native outlined fields, titleLarge/bodyMedium/bodySmall roles, full-width actions with 48dp/52dp minimums, polite status/validation announcements, field error semantics and theme-derived progress.
- `app/src/main/java/dev/cctv/v380/ui/CameraSettingsScreen.kt`: hotspot section follows local camera access in the scrollable settings column; 24dp gutters, 16dp spacing and keyboard insets remain.
- `app/src/main/java/dev/cctv/v380/ui/RecordingsScreen.kt`: existing tab treatment and feedback retain native components; pending hotspot recovery gates conflicting recording actions.

The source supports the direction contract: access/capability/network gates precede editing; hotspot and camera-login credentials have distinct labels; pending recovery replaces the editor with destination and verification guidance; the write is described as a one-time action. Password visibility and secret clearing follow native form behavior. These are descriptions of implemented UI behavior, not confirmation that a camera accepts the operation.

## System summary

1. Palette: incumbent green primary/secondary over pale light or near-black dark surfaces; inherited Material roles remain authoritative.
2. Type: Android system sans-serif through Material typography; hotspot headings use titleLarge and supporting copy uses bodyMedium/bodySmall.
3. Layout: scrollable settings, 24dp gutters, 16dp related spacing, native fields and full-width principal actions.
4. Controls: 48dp minimum hotspot secondary targets and 52dp save/verification actions; role-based errors, disabled states and polite progress/status semantics.
5. Named rules: preserve the Inherited Roles Rule and Platform Type Rule; retain flat native grouping and explicit state/capability gates.

## Limits and drift

No fresh native rendering or runtime accessibility approval was available: the API30 emulator attempt crashed during startup (reported SIGSEGV; `/tmp/ap-hotspot-emulator.log`). No owner-camera test was performed. The separate finish review resolving five material source findings does not establish rendered layout, keyboard behavior, large-text fit, TalkBack operation or camera interoperability. Existing historical captures do not depict this hotspot editor.

Pre-existing inherited Material colors, including purple container/onPrimary roles, are neither repaired nor promoted into a new green tonal palette. Sidecar HTML/CSS previews and synthesized ramps remain illustrative metadata, not native rendering evidence. No detector, new tokens or raster assets were introduced or canonized by this pass.
