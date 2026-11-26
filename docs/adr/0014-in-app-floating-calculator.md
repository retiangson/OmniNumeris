# ADR 0014 · In-app floating calculator

**Status** Partial implementation  
**Ticket** OW-0801 / [KAN-41](https://omninumeris.atlassian.net/browse/KAN-41)  
**Traceability** FR-CAL-01

## Decision

Place the floating calculator in the main Compose activity above its current workspace content. The dashboard, math editor and debug canvas open the same panel, backed by a single activity-scoped calculator ViewModel. Opening, moving, resizing, collapsing and closing the panel are presentation changes. Calculator drafts, results and plots stay in the ViewModel while the activity survives or is recreated for a configuration change. The panel size, placement and visibility use saved presentation state.

The panel uses the current safe drawing area and resizes around the software keyboard. Its preferred size is clamped to that area, with a scrollable calculator body in compact windows. Relative placement and preferred size survive temporary reductions in the available space. The title grip moves the panel and the bottom-right handle resizes it. Both handles accept arrow keys, and the Adjust panel menu offers the same changes without dragging. The panel collapses to a Restore calculator button and stays inside the app window.

The math correction and solution-history dialogs can open the same shared calculator. They temporarily yield the dialog window while the panel is expanded. Closing or collapsing it returns to the current review or history state, including an unconfirmed correction. Any source revocation that occurs while the calculator is open still applies when the review returns.

## Remaining integrations

The debug canvas is a destination inside the workspace host and uses its existing private ink journal. The production notebook and reader are still pending. Their launch, navigation and focus acceptance remain unverified. [Notebook KAN-64](https://omninumeris.atlassian.net/browse/KAN-64) · [PDF reader KAN-92](https://omninumeris.atlassian.net/browse/KAN-92) · [Book reader KAN-101](https://omninumeris.atlassian.net/browse/KAN-101)

The placement and controls follow the shared adaptive workspace design. [Confluence UI design system](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/458754)

## Verification

On 2026-11-25, three placement tests covered all four edges, minimum and maximum sizes, and how temporary space changes preserve preferred size and position. Gradle checks, lint, debug, release and instrumentation APK builds passed.

On the Android 10 phone emulator, direct touchscreen input reduced the panel width by 121 pixels and height by 190 pixels. A subsequent title drag moved it 61 pixels horizontally and 73 pixels vertically within the observed safe bounds. Visual inspection showed the editor behind the resized panel. The debug build with the panel and adjustment menu installed on the Samsung tablet with all 22 saved stroke files matching byte for byte. The tablet was locked and subsequently disconnected before the final accessibility-label update.

All ten offline instrumentation cases passed on the 1080 × 1920 phone and 1600 × 2560 tablet-width profiles of the Android 10 emulator. They exercised the Adjust panel menu, independent calculator and editor drafts, system Back collapse, restore, rotation, and keyboard clearance alongside the calculator and editor regressions. The wide-screen run also confirmed that the calculation button keeps a stable accessible label while announcing progress separately. Debug and release manifests request neither system overlay nor Internet permission.

After integrating the canvas, 13 offline calculator and canvas tests passed on each of the Android 10 emulator profiles above. The new case taps Calculate over the canvas and verifies the same draft and result after collapse and navigation to the math editor. The build, lint and unit checks passed. The connected Samsung tablet remained locked, so the new canvas path was verified on the emulator profiles.

For the review and history entry points, all 17 offline Android 10 calculator and editor tests passed, together with the Gradle, lint and APK build checks. The new test opens the same calculator result from both dialogs, verifies that collapse preserves an unconfirmed correction, and verifies that close returns to solution history. It also passed on the Samsung SM-X806B. All 22 saved ink checksums matched after testing.
