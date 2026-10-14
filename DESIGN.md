---
name: V380 LAN
description: Native Android Material 3 camera interface with explicit connection status.
colors:
  light-primary: "#185C43"
  light-secondary: "#496453"
  light-surface: "#F8FAF6"
  dark-primary: "#95D6B1"
  dark-secondary: "#B4CCBD"
  dark-surface: "#101511"
  video-background: "#000000"
  video-text: "#FFFFFF"
typography:
  headlineMedium: {fontFamily: "sans-serif", fontSize: "28sp", fontWeight: 400, lineHeight: "36sp"}
  headlineSmall: {fontFamily: "sans-serif", fontSize: "24sp", fontWeight: 400, lineHeight: "32sp"}
  titleLarge: {fontFamily: "sans-serif", fontSize: "22sp", fontWeight: 400, lineHeight: "28sp"}
  titleMedium: {fontFamily: "sans-serif", fontSize: "16sp", fontWeight: 500, lineHeight: "24sp"}
  titleSmall: {fontFamily: "sans-serif", fontSize: "14sp", fontWeight: 500, lineHeight: "20sp"}
  bodyLarge: {fontFamily: "sans-serif", fontSize: "16sp", fontWeight: 400, lineHeight: "24sp"}
  bodyMedium: {fontFamily: "sans-serif", fontSize: "14sp", fontWeight: 400, lineHeight: "20sp"}
  bodySmall: {fontFamily: "sans-serif", fontSize: "12sp", fontWeight: 400, lineHeight: "16sp"}
  labelLarge: {fontFamily: "sans-serif", fontSize: "14sp", fontWeight: 500, lineHeight: "20sp"}
rounded:
  inset-surface: "12dp"
spacing:
  compact: "8dp"
  supporting: "12dp"
  related: "16dp"
  group: "24dp"
components:
  button-primary: {backgroundColor: "{colors.light-primary}", typography: "{typography.labelLarge}", height: "48dp"}
  button-primary-dark: {backgroundColor: "{colors.dark-primary}", typography: "{typography.labelLarge}", height: "48dp"}
  button-form: {backgroundColor: "{colors.light-primary}", typography: "{typography.labelLarge}", height: "52dp"}
  button-text: {textColor: "{colors.light-primary}", typography: "{typography.labelLarge}", height: "48dp"}
  error-notice: {typography: "{typography.bodyMedium}", rounded: "{rounded.inset-surface}", padding: "16dp"}
  camera-icon-container: {rounded: "{rounded.inset-surface}", padding: "12dp", size: "48dp"}
  ptz-direction-disabled: {size: "56dp"}
  video-surface: {backgroundColor: "{colors.video-background}", textColor: "{colors.video-text}"}
---

# Design System: V380 LAN

## Overview

**Creative North Star: "Native Material 3 camera interface"**

The confirmed direction uses Android platform conventions, Indonesian copy, a compact camera list and one foreground player. Clear status, readable controls and familiar form behavior define the interface.

This record describes the current Android interface in `app/src/main/java/dev/cctv/v380/ui`, including the experimental debug player, local recordings/settings tabs, AP setup and diagnostics. It preserves the user-approved Material direction; it does not certify camera connectivity. Tokens use Android `dp` and `sp`; component heights are minimums where the source uses `heightIn`.

**Key Characteristics:**

- Native Compose Material 3 components and system typography.
- System-selected light and dark themes with green primary accents.
- Scrollable phone layouts, explicit connection status and controls gated by streaming state and camera capabilities.

## Colors

The app overrides primary, secondary, surface and background in the static Material schemes; background equals surface in each mode. The frontmatter records these overrides and the black video surface with white status text.

### Primary

Light primary is deep green for primary actions and the empty-state camera icon. Dark primary is pale green serving the same roles.

### Secondary

The secondary roles use muted green companions. Components consume the Material scheme rather than a separate custom color API.

### Neutral

Light surface is a pale green-tinted canvas; dark surface is a near-black green canvas. Video remains black in either mode. Supporting text, dividers, field outlines and error surfaces use inherited Material roles.

**The Inherited Roles Rule.** Overriding primary does not regenerate the Material palette. `onPrimary`, `primaryContainer`, `onPrimaryContainer` and error roles retain library defaults; the visible FAB and camera-row icon container can therefore be purple. Do not describe the current build as a complete green tonal palette.

## Typography

Use `MaterialTheme.typography` with Android system sans-serif and font scaling. The frontmatter records the used base roles inherited from resolved Material 3 1.4.0; it does not override the Compose theme. Tracking and unspecified properties remain inherited.

`headlineMedium` titles the camera list; `headlineSmall` titles the empty state and profile details. `titleLarge` labels player groups and discovered cameras; `titleMedium` labels camera rows and controls; `titleSmall` labels password memory. Body roles carry instructions, addresses, notices and supporting text. Native buttons use `labelLarge`. The app-bar brand alone applies semibold weight to its inherited title style.

**The Platform Type Rule.** Keep text in Material roles and allow wrapping and system scaling instead of fixing text containers to a single line height.

## Layout

The recurring rhythm is compact control spacing, related-field spacing and larger group spacing, with supporting gaps between short text blocks. Pages use group-sized horizontal gutters; profile fields and player groups use related spacing. The list uses rows and dividers, with bottom space for the add FAB. The full-page editor uses keyboard insets and vertical scrolling; Port and Device ID share a row in a 1:2 width ratio.

Interactive targets are at least (48dp); save/connect buttons have a (52dp) minimum height, password memory uses a (56dp) minimum row, and directional PTZ controls are (56dp) square. Material supplies default target sizing for other interactive components.

The full-width black video area sits below the app bar, outside the weighted, vertically scrolling controls. Its height is the smaller of a (16:9) frame derived from available width and half the available content height; the actual (16:9) SurfaceView is centered within it. This bounds video height in landscape while preserving a controls viewport. Player details request a maximum width of (680dp). No alternate tablet composition or custom breakpoint exists.

The seven existing API37 phone captures cover light home, dark home at font scale (1.3), initial AP setup, diagnostics, a disconnected synthetic player, scrolled PTZ controls and the editor. They show the intended black disconnected video area and status overlay. Later AP phases and active live controls are source-reviewed only. Keyboard, landscape, larger screens, older APIs and runtime accessibility need additional visual or interaction coverage; these captures do not establish camera interoperability. They predate the latest PTZ cleanup and two validation-copy spacing corrections; no new capture is claimed for those changes.

## Elevation & Depth

Content is mostly flat, separated by spacing, role colors and dividers. The add FAB and deletion dialog inherit Material elevation. The app adds no custom shadow or motion tokens; native state layers, ripple and focus behavior remain in use.

## Shapes

The error notice and camera-row icon container share gently rounded inset surfaces. Buttons, fields, chips, FAB and dialogs inherit Material shapes. Preserve native silhouettes rather than introducing a new global radius scale. The video SurfaceView has a rectangular boundary.

## Components

- **Actions:** Filled buttons carry scan, save, connect and setup phase actions; text and outlined buttons carry manual entry, settings handoff, cancellation and secondary actions. Save/connect grow to page width. Loading and disabled states use Material treatment.
- **Fields:** Outlined single-line fields use persistent labels, purpose-appropriate keyboards and masked password entry. Supporting text explains password behavior; validation appears in an error notice.
- **Password memory:** A single toggleable row owns checkbox interaction and semantics; its nested checkbox is presentational. Nonsecret drafts survive recreation; password text is cleared on background/disposal.
- **Navigation and list:** App bars carry back/edit/search and diagnostics actions; camera rows and dividers accompany a manual-add FAB. The home screen exposes AP setup alongside discovery and manual entry.
- **Error notice:** A rounded Material error-container surface has a polite live region and a dismiss text action when supplied. Recovery guidance belongs in the message or adjacent controls.
- **Quality and PTZ:** SD/HD chips enable only in `Streaming` for available qualities. Selection reflects `Negotiating` or `Streaming` quality; disconnected chips have no static HD selection. PTZ requires `Streaming` and confirmed PTZ support; horizontal and vertical controls also respect their axis capabilities. Direction buttons keep the same square size across states; Stop uses a minimum (72dp × 56dp) target. Press/release and cancellation request STOP through the source cleanup path, whose physical-camera behavior remains unverified.
- **Player:** An actual Android SurfaceView occupies the black video area. A status overlay distinguishes disconnected, connecting, authenticating, authenticated, waiting-for-video, reconnecting and error states. It disappears only in `Streaming`, which the session enters from the decoder's first-rendered-frame callback. Connect requires a valid surface; cancellation/disconnect and error recovery remain explicit. No sample footage stands in for a stream.
- **Camera feature tabs:** Native primary tabs within camera detail switch Live/Rekaman/Pengaturan. Recordings use the platform date picker, all/events filters, rows with recording dates/time ranges/duration, newest-first or oldest-first ordering, and a bounded native playback Surface with pause/seek. Export uses CreateDocument with a persistent video-only explanation and a scoped lifecycle handoff; no checkbox blocks Save. The recordings tab reads SD capacity independently and warns from 90% usage. Per-recording SD deletion is disabled with an explicit reason until its protocol contract is verified. Settings group image, lights, recording and SD controls with actual read values, draft/save/readback and unknown/permission copy. Leaving active work or unsaved changes asks for confirmation. Format has a destructive dialog and durable pending recovery, but its button and write entry points remain disabled until terminal semantics are proven. New screen screenshots and runtime accessibility are not verified: API37/API30 emulators crashed during boot. Existing captures above remain historical.
- **AP setup:** A scrollable native form presents hotspot connection, camera selection/local authentication, home-Wi-Fi delivery and identity/login verification as separate phases, with progress and recovery copy. Camera-local, hotspot and home-Wi-Fi passwords have distinct labels. Fresh new-platform PIN binding reports its cloud requirement; an authenticated LAN password change is a separate action. Completion is based on verified identity and local login, not a Wi-Fi acknowledgement alone.
- **Diagnostics:** A native list presents selected-network information and sanitized stage/status events, with JSON export through Android's document picker. It states that firmware compatibility is still under test; event rows do not imply camera acceptance.
- **Deletion:** A native alert dialog confirms removal of a camera profile and its stored password; editing itself stays on a full page.

Sidecar component snippets are illustrative HTML translations for the design panel. Android source and native captures own visual truth; these previews are not runtime verification.

## Do's and Don'ts

### Do:

- **Do** use native Material components, system font scaling and the system-selected theme.
- **Do** preserve Indonesian labels, explicit connection status and state/capability gating for unavailable controls.
- **Do** use the shared spacing rhythm and minimum interactive target sizing.

### Don't:

- **Don't** infer a regenerated green palette from the customized primary color.
- **Don't** use invented footage, decorative imagery or dashboard metrics in this camera interface.
- **Don't** present synthetic tests, a selected quality chip or the black player surface as proof of camera interoperability.

## Camera management extension0.4.2

Mode: Operate, inheriting the existing Material3 identity. Settings adds expandable native groups for alarm, advanced lights, motor configuration and device information; Live adds presets and patrol configuration below the existing directional controls. Each group reads independently and shows actual values, missing capabilities or permission errors. Time editing uses Material TimeInput, preset selection uses a native dropdown, and route dwell uses a bounded Material slider. No new typography, palette, navigation destination, visual asset or dependency is introduced.

Destructive/potentially moving actions name their consequence in confirmation dialogs. Pending restart and calibration distinguish accepted/unknown status from verified completion; closing a local record does not cancel the device operation. Format remains disabled. Labelled fixture UI tests were added and compiled, but native screenshots/runtime verification are unavailable because the API35 and fallback API26 emulators crashed during boot with KVM; historical captures elsewhere in this document do not validate the new groups.

Alarm stage2 uses a native day dropdown to keep one day’s intervals in view. Add/remove edits a draft; Save and Discard are explicit. Inline time/overlap validation gates Save, and deleting a middle interval preserves its own event metadata. New intervals are motion-only and use the proven full-day default; unknown capacity remains conservative. Compose callbacks can be exercised without a camera session. No new capture or runtime accessibility pass is claimed.

Stage3 area editing stays within Settings/Alarm. A temporary native preview stops before the still-image editor opens. The grid overlays real reference pixels, with visible selected-cell marks and 48dp native row/column/toggle alternatives. Use only updates the alarm draft; Cancel releases the reference. Live/Settings retain their existing composition. Synthetic fixture capture is prepared for light/dark, orientation and font QA, but no new native capture or accessibility pass is claimed.

## Recordings surface 0.4.3

Mode: Operate. The supplied card-based reference owns this tab’s replacement composition: dark blue-black canvas, charcoal cards, mint actions, 20dp rounded corners, 16dp gutters and 12–16dp gaps. The light counterpart uses white cards, a pale green canvas and deep green actions with accessible foregrounds. These colors are scoped to Recordings; other surfaces retain their established tokens.

The list places actual SD status/percentage before the date/filter panel and thumbnail-free recording cards. Date arrows/picker and filters load automatically; refresh and ordering are compact menu actions. Recordings lead with time range, then dates and duration/type. Per-card menus offer Play/Save and explain the disabled SD deletion action. No invented GB capacity or sample footage is shown.

Selecting Play replaces the list with storage, rounded native 16:9 video, feedback, date navigation and playback/export controls. Back restores list scroll/filter/order; leaving a download requires confirmation. The list retains its lazy scroll state. The small player form uses a scrollable Column so its native Surface stays attached when controls scroll into view. Date labels wrap across midnight and large fonts; native touch targets remain at least48dp. Visual evidence and its exact native/synthetic scope are recorded in validation/recordings-redesign/results.json.
