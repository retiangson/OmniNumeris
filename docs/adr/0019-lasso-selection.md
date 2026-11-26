# ADR 0019 · Lasso selection and preview

**Status** Partial implementation  
**Ticket** OW-0703 / [KAN-36](https://omninumeris.atlassian.net/browse/KAN-36)  
**Traceability** FR-MAT-03

## Decision

The debug canvas offers an explicit lasso mode. It processes batched pointer positions in order and inverse-transforms the loop through the active viewport. It queries committed ink tiles and checks stroke centerlines against the source-space polygon. A segment can qualify even when both of its endpoints lie outside the polygon. The preview clips matched ink to the polygon and lets the user exclude or restore individual strokes. Empty and oversized selections have explicit outcomes.

Selection and rasterization run on a cancellable worker with bounded geometry and bitmap allocation. The preview is transient and separate from the ink journal. The canvas discards an unfinished loop on pointer cancellation and the preview on dismissal or tool change.

## Remaining integration

The ticket still needs a production notebook and document source with revision-aware anchors and an approved local recognition route. The debug preview currently reports recognition as unavailable. [Unified inputs KAN-34](https://omninumeris.atlassian.net/browse/KAN-34) · [Notebook and reader dependencies](https://omninumeris.atlassian.net/browse/KAN-36)

## Verification

Geometry tests cover zoom and pan mapping, crossing segments, boundary points, concave regions and bounded selection. The canvas instrumentation test exercises overlap correction, empty result, dismissal and byte-for-byte preservation of the fixture journal through the normal toolbar and dialog.

On 2026-11-25, the three geometry tests, Gradle checks and lint, and debug, release and instrumentation APK builds passed. All three canvas instrumentation tests passed on the API 29 emulator with airplane mode enabled and Wi-Fi and mobile data disabled. The lasso instrumentation also passed on the Samsung SM-X806B tablet. SHA-256 checksums for all 22 existing tablet stroke files matched after the debug upgrade and test.

The batched-input regression pinches to zoom, then sends three lasso corners in one stylus move event. It selects two overlapping strokes and excludes the nearby decoy, allows each matching stroke to be removed from the preview, and leaves the fixture journal byte for byte intact. The three geometry tests, build and lint checks, and 13 offline canvas and calculator instrumentation tests passed. The zoomed lasso regression passed on the Samsung SM-X806B, with matching checksums for all 22 existing stroke files.
