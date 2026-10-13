## verdict

Scoped verdict pass only, performed by a generic task-agent using the degraded finish-reviewer contract. The latest user instruction excludes connection testing; this pass inspected the listed source/documentation fixes without launching an emulator, running tests, contacting a camera/network, or producing captures. No native detector ran.

| Prior material fix | Score | Evidence and limit |
|---|---|---|
| PTZ pointer-cancellation cleanup | resolved in source | [LivePlayerScreen.kt:161](../app/src/main/java/dev/cctv/v380/ui/LivePlayerScreen.kt#L161) now wraps both `repeat?.cancelAndJoin()` and `model.stopPtz()` in `withContext(NonCancellable)`, so cancellation of the pointer coroutine no longer aborts the cleanup at the join before STOP is invoked. [CameraViewModel.kt:146](../app/src/main/java/dev/cctv/v380/ui/CameraViewModel.kt#L146) retains its bounded noncancellable STOP. The recommended active-press cancellation test and physical STOP/motor acceptance are deferred by the user; neither is claimed complete. |
| PRODUCT/DESIGN factual drift | resolved | [PRODUCT.md](../PRODUCT.md), [DESIGN.md](../DESIGN.md), and [.impeccable/design.json](../.impeccable/design.json) describe experimental debug adapters, state/capability-gated quality/PTZ, fixed video bounds, AP setup, and diagnostics. The obsolete blanket implementation gate and static HD placeholder are removed. The existing Material world and token values are preserved. Owner-camera acceptance remains explicitly unverified, and fresh new-platform PIN binding remains cloud-required. [design-documentation.md](design-documentation.md) records the documentation scope and evidence limits. |
| Indonesian status/validation spacing | resolved in source | [ProvisioningScreen.kt:95](../app/src/main/java/dev/cctv/v380/ui/ProvisioningScreen.kt#L95) now reads `memerlukan 8–63 karakter`; [ProvisioningScreen.kt:172](../app/src/main/java/dev/cctv/v380/ui/ProvisioningScreen.kt#L172) now reads `maksimal 120 detik`. Meaning and recovery guidance are preserved. These branch strings have no post-fix rendered capture. |

No regressions were identified in the inspected fix changes. This source/documentation scoring does not establish a new visual or runtime regression result.

## remaining

Clear for the three scored source/documentation fixes within the user's revised scope. Runtime cancellation/release behavior and physical motor STOP remain deferred, together with camera discovery/authentication, decoded live footage, SD/HD switching, AP join, Wi-Fi credential delivery, and post-setup LAN verification. No full camera approval is given.

The seven existing 1080 × 2400 API37 phone captures remain prior visual evidence only: [light home](../.impeccable/review/phone-light-v2.png), [dark home at font scale 1.3](../.impeccable/review/phone-dark-large-v2.png), [initial AP setup](../.impeccable/review/phone-ap-v2.png), [diagnostics](../.impeccable/review/phone-diagnostics-v2.png), [disconnected synthetic player](../.impeccable/review/phone-player-v2.png), [scrolled disconnected PTZ](../.impeccable/review/phone-ptz-v2.png), and [synthetic editor](../.impeccable/review/phone-editor-v2.png). They predate the cleanup and copy fixes and were not recaptured. The PTZ capture hash is unchanged: `d1e1a2da92315d16c38130044c785005c0c9a115e237e8a93263cd56b30beeec`. The prior review did not reproduce a Surface overlap with the app/system bars.

Keyboard, landscape, tablets, older APIs, screen-reader behavior, gestures, and performance also remain outside the reviewed evidence. Native Material 3, Indonesian labels, green primary with inherited roles, honest synthetic/disconnected states, and capability gating remain the pinned world.

This ship covers the scored fixes, not the whole surface.

disposition: ship
