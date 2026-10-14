# Native UI review — Recordings 0.4.3

Captured on the connected M2101K6G, Android API33. The fixtures are explicitly labelled synthetic and disconnected; black video is an unavailable native Surface, not sample footage. All12 final captures were inspected together: light portrait, dark portrait at font1.3, and dark landscape, each covering list/storage/navigation and player/error/controls. Global device theme/font settings were not changed.

The single correction batch completed the SD icon, removed the purple storage track/gap/stop marker, enlarged pill filters, flattened error feedback, aligned player button heights and added the Save icon. Player content now uses a scrollable Column to retain its Surface; the recordings list remains virtualized and retains scroll position. The native picker test asserts the Surface is still attached and valid after scrolling to Save.

The confirmation suite passed9/10 cases including all capture variants. The first Compose fixture hit a MIUI hierarchy-readiness race; a targeted rerun with explicit readiness waiting passed. Ten unique native scenarios therefore passed across confirmation and targeted retry. Logs preserve the initial failures rather than treating earlier attempts as successful. Real camera playback/download interoperability and the original297KB failure remain unverified.
