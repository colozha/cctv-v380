# Design documentation reconciliation

Disposition: complete for the authorized factual documentation refresh.

A generic task agent applied the Impeccable documenter role as the named-role fallback. This was an ordinary extension of the pinned native Android Material 3 world; no redesign, new token system or approved-comp comparison was required. The session had already attempted context loading and used the required direct-read fallback after a permission error. This pass did not rerun the launcher.

## Files written

- [PRODUCT.md](../PRODUCT.md): replaces the obsolete foundation-only wire gate with the implemented experimental debug Legacy V2 / IoT LAN paths, local/AP setup purpose, capability gating, diagnostics and the remaining owner-camera acceptance boundary.
- [DESIGN.md](../DESIGN.md): describes the current player, AP setup and diagnostics behavior, fixed video bounds, state-dependent quality/PTZ selection and the limits of existing captures. The entire incumbent YAML token block was preserved byte-for-byte.
- [.impeccable/design.json](../.impeccable/design.json): synchronizes the design narrative and current source/capture references. Disconnected previews now have no static selected HD chip and no obsolete blanket unsupported-profile gate. Their descriptions distinguish the captured disconnected state from source-driven live control behavior. Existing colors, typography, spacing, shapes, tonal metadata and CSS values remain unchanged.
- This validation record.

No application source, screenshots or other documentation was edited by this pass. [ui-review.md](ui-review.md) remains the historical review that requested these corrections; its persistence finding is resolved by the files above.

## Evidence checked

Source was read directly, including:

- [MainActivity.kt](../app/src/main/java/dev/cctv/v380/ui/MainActivity.kt): unchanged static green primary/secondary and surface overrides, inherited Material roles, navigation, profile editing, permissions and home entry points.
- [LivePlayerScreen.kt](../app/src/main/java/dev/cctv/v380/ui/LivePlayerScreen.kt): black video area outside weighted scrolling controls; height bounded by the smaller of width-derived 16:9 and half the available content height; centered 16:9 SurfaceView; connection overlays; quality/PTZ gating; noncancellable pointer cleanup containing repeat-job cancellation/join and STOP.
- [CameraViewModel.kt](../app/src/main/java/dev/cctv/v380/ui/CameraViewModel.kt) and [CameraSession.kt](../app/src/main/java/dev/cctv/v380/session/CameraSession.kt): experimental adapters require a debug build; real local-auth/media/control paths and bounded STOP cleanup exist; controls are tied to session/capability state.
- [VideoDecoder.kt](../app/src/main/java/dev/cctv/v380/media/VideoDecoder.kt): the first-frame notification comes from `setOnFrameRenderedListener`; the session uses it to enter `Streaming`.
- [ProvisioningScreen.kt](../app/src/main/java/dev/cctv/v380/ui/ProvisioningScreen.kt), [ProvisioningManager.kt](../app/src/main/java/dev/cctv/v380/provisioning/ProvisioningManager.kt) and [CameraModels.kt](../app/src/main/java/dev/cctv/v380/session/CameraModels.kt): separate hotspot/local-camera/home-Wi-Fi inputs, authenticated Wi-Fi configuration, direct AP option, same-device rediscovery/login completion, capability/state model and an explicit cloud-required fresh IoT PIN-binding branch. The two requested validation-copy spacing corrections are present.
- [DiagnosticsScreen.kt](../app/src/main/java/dev/cctv/v380/ui/DiagnosticsScreen.kt), [analysis/REPORT.md](../analysis/REPORT.md) and [ui-review.md](ui-review.md): sanitized diagnostic presentation, firmware-testing notice, experimental implementation status and the acceptance limits supplied by the review.

All seven existing API37 phone captures were visually inspected. The image viewer scaled their display; PNG metadata independently confirmed each original is 1080 × 2400:

- [Light home](../.impeccable/review/phone-light-v2.png).
- [Dark home at font scale 1.3](../.impeccable/review/phone-dark-large-v2.png).
- [Initial AP setup](../.impeccable/review/phone-ap-v2.png).
- [Diagnostics](../.impeccable/review/phone-diagnostics-v2.png).
- [Disconnected synthetic player](../.impeccable/review/phone-player-v2.png).
- [Scrolled disconnected PTZ controls](../.impeccable/review/phone-ptz-v2.png).
- [Synthetic profile editor](../.impeccable/review/phone-editor-v2.png).

The player/editor name is explicitly `Contoh lokal (sintetis)` and its address is a documentation address. The black player region is the intended disconnected SurfaceView ground with a visible status overlay. The PTZ capture shows the controls scrolled beneath fixed video bounds; its SHA256 is `d1e1a2da92315d16c38130044c785005c0c9a115e237e8a93263cd56b30beeec`.

## Validation performed

Documentation-only checks passed: JSON parsing, eight canonical DESIGN section headings in order, sidecar narrative/characteristics/do/don't synchronization with Markdown, valid source/capture paths, no selected quality in the disconnected HTML preview, PNG dimensions and the PTZ capture hash. Direct source checks confirmed the debug gate, render callback, PTZ cleanup and corrected copy. No HTML/CSS design detector ran; it is inapplicable to native Compose, and the sidecar snippets remain illustrative browser approximations rather than native runtime evidence.

## System preserved

Palette: deep/pale green primary accents, green-tinted light/dark surfaces, black/white video and inherited purple Material roles.

Type: existing Android system sans-serif Material roles and system font scaling.

Layout: shared 8/12/16/24dp rhythm, native minimum targets, fixed bounded video above weighted scrolling controls.

Material: flat tonal surfaces, native fields/chips/buttons/FAB/dialogs and inherited state layers/elevation.

Named rules: the Inherited Roles Rule and Platform Type Rule are unchanged.

## Remaining limitations

No emulator was launched, no network or connection test was run, and no new capture was produced. The seven supplied captures predate the latest PTZ cleanup and two copy corrections, so those edits are source-checked only. There is no owner-camera acceptance for discovery/authentication, decoded footage, SD/HD switching, PTZ movement/STOP, AP joining, credential delivery or post-setup LAN verification. Later AP phases and enabled live controls have source evidence only. Keyboard, landscape, larger screens, older APIs, screen-reader behavior, gestures and performance were not exercised by this pass.

No unverified camera success, new palette, control-state placeholder or new visual prohibition was canonized. The existing `ptz-direction-disabled` token key remains as the incumbent disabled-state size record; current prose makes clear that the same direction-button size applies when enabled. Native runtime and physical-camera acceptance remain separate outstanding evidence.
