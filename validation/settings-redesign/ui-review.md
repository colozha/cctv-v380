# Settings redesign verification

Native Android / Operate. The supplied owner reference governs the nine-card charcoal/mint layout and its system-selected light counterpart. Ponytail reuses Compose Material 3, CameraPanel, SectionIcon and existing camera callbacks; no dependency or wire change.

## Software and native checks

83 JVM tests pass. Debug APK, Android test APK and lint pass (18 existing warnings, one hint). Logs: build-final.log and test-fixes-build.log. The final physical Android API33 run passes all 11 selected SettingsUiTest, FeatureUiTest and CameraManagementUiTest cases (native-final.log). A separate one-test dark-top capture confirmation also passes (native-chrome-confirm.log).

Fixtures are labelled synthetic and cannot open camera transport. Checks cover independent expansion, saved-state restoration, retained drafts, radio/switch updates, collapsed errors and progress/recovery, unauthenticated locks and stale-value invalidation, hotspot validation/password disposal, alarm intervals, canceling destructive confirmation, and diagnostic newest-first display with chronological JSON export. No owner-camera login, settings write, video or physical movement is claimed.

APK installation used adb install -r, without resetting data. The OEM rejected background ActivityScenario launches; direct MAIN/LAUNCHER shell foreground launches allowed tests to start. No security or permission settings were weakened. Font scaling was injected inside fixture composition; system font_scale stayed 0.86. Requested orientation was restored. API37 emulator boot failed first with SIGSEGV139; physical-device verification subsequently succeeded.

Initial failures were test expectations/setup, retained in native-settings.log and native-first-full.log: text-field supporting copy made full text equality unsuitable; MainActivity already had content before StateRestorationTester; disconnect intentionally clears stale settings/drafts; an existing Live test assumed a status label was unique. The corrected native suite passes.

## Visual evidence and independent review

review-final contains 48 actual-device PNGs: 45 settled comparison captures across light, dark, 1.3x font and landscape, plus diagnostics; three auxiliary closed-state captures demonstrate draft/progress/restart status after real taps and may retain readable transient native feedback. All nine expanded sections and necessary landscape control continuations are represented. Initial ripple-affected comparison captures were discarded; matrix screenshots use semantic header actions while behavior tests continue to use real taps.

The independent reviewer used a generic agent with the Impeccable review contract because this harness cannot select the shipped reviewer type. No web design detector ran for native Compose. All 48 files were inventoried and inspected in batches, with enlarged primary and landscape views. The reviewer found the text-pinned native reference criteria met and no concrete production-source blocker. Palette samples match DESIGN.md. The original owner image remained in the parent conversation, so exact image fidelity was not independently scored; no separate QUALITY BAR card was supplied.

Full review disposition: **fix**, for two evidence findings. Final verdict: **ship for the two scored fixes within the synthetic UI scope**, both resolved:

1. dark-top.png was recaptured after fixture window appearance settled; its clock/status icons are now readable white on the dark canvas. The targeted native check passes. No production change was required.
2. PRODUCT.md, DESIGN.md and both affected surface briefs distinguish 45 comparison captures from three auxiliary tap-driven captures.

No regressions were observed from those two fixes. This verdict does not establish owner-camera acceptance or an independent pixel match to the original image.

## Documentation

DESIGN.md, PRODUCT.md, camera-management and ap-hotspot surface briefs describe the final cards, explicit reads, capability/maintenance safeguards, draft handling and diagnostic order. Preexisting design.json/schema drift was reported without repair; this extension preserves the established visual system.
