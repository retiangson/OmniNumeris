# ADR 0004 · Ink canvas prototype

**Status** Provisional, 2026-11-24  
**Ticket** OW-003 / [KAN-29](https://omninumeris.atlassian.net/browse/KAN-29)  
**Traceability** FR-NOT-01, FR-DRW-01, FR-DRW-02, FR-STY-01, NFR-02, NFR-05 and NFR-09

## Decision

Use platform Canvas with an immutable Kotlin/JVM stroke model to evaluate a view that provides pressure-sensitive ink on phones and tablets. The debug workspace exposes **Canvas prototype**. Stylus-only writing is enabled initially. Touch writing uses predictable pressure and allows two-finger navigation when the user turns off stylus-only mode. The prototype uses a single ink layer and a bounded coordinate range for repeatable measurements.

Draw active ink on the UI thread for immediate feedback. Index committed strokes into 256-pixel tiles near their brush footprint. Use background workers for tile rasterization, stroke indexing and journal operations. Raster tiles are derived from vector strokes. The bitmap cache holds at most 96 tiles (24 MiB of pixel data), with at most 16 tile requests in flight. A memory-trim callback evicts tiles and briefly pauses new allocations. On cache misses the view can draw vector ink directly until the tiles are ready.

Give each committed stroke its own UUID and checksummed, versioned record. Write pending files in the app-private no-backup directory, sync their contents and rename atomically on the same filesystem. Recover committed records by reopening the journal. Ignore incomplete pending files and report damaged records individually. This storage path serves the debug canvas. Protected notebooks, their keys, and protected indexes require the design in OW-006.

New strokes store one of three pressure-sensitive widths. Fine is the default, and legacy records retain their original width. The eraser hides one whole stroke per gesture using an atomic journal rename and keeps its contents for undo. Undo reverses recent committed edits in the current canvas session. Erased strokes remain hidden after restart. This work exercises part of the brush, palm rejection and stylus-only stories. [Brush presets](https://omninumeris.atlassian.net/browse/KAN-77) · [Palm rejection](https://omninumeris.atlassian.net/browse/KAN-79) · [Stylus-only mode](https://omninumeris.atlassian.net/browse/KAN-80)

## Validation

Use local `OmniInkProbe` log entries to measure ink-bearing Canvas draws and the time from Android motion-event timestamps to app drawing. This proxy measures application-side scheduling and excludes GPU composition and display scan-out. Capture the device and input method with each run. The 60 FPS and 25 ms targets remain provisional until validated under representative physical workloads.

Connect a physical tablet, open **Canvas prototype**, draw a saved stroke, force-stop the app, and reopen the canvas. Verify the saved count and visible ink. Exercise low and high stylus pressure, two-finger pan and zoom, tile boundaries and longer strokes. Keep test ink free of personal content.

The 2026-11-24 pilot used the Samsung SM-X806B at API 36. The instrumented stylus stream retained a pressure range above the test's low and high thresholds and produced 79 ink-bearing draws at 102.2 Hz with 14 ms p95 event-to-draw latency. A separate injected finger swipe produced 165 draws at 117.6 Hz with 12 ms p95 latency. All three committed test strokes recovered after force-stopping and reopening the app. Device injection exercised the app input path. Physical pen-to-display latency, memory pressure under a sustained large canvas, and performance on a 3 GB device remain unmeasured.

On 2026-11-25, Samsung instrumentation exercised pressure-sensitive fine ink, finger filtering, canceled gestures, erasing, undo and touch writing with the selected width. Journal tests cover legacy stroke widths and erased-stroke recovery after reopening. All 53 existing tablet strokes were present after the update and tests. The tablet tests used injected events. Physical palm contact and behavior across other digitizers remain unverified.

The later handwriting capture on the Samsung tablet showed Fine and Stylus only selected. All 23 exported new-format records passed their checksums and stored Fine. Their 3,481 pressure samples ranged from 0.000 to 1.000 with a median of 0.937. The capture did not contain two identifiable pressure-test lines. The writer confirmed the small mark beside the equation was intentional, and the capture showed no other stray marks. The capture establishes pressure capture and width persistence for this writing session. Physical palm contact was not independently observed.

## References

- [OmniNumeris requirements baseline](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655361)
- [Android stylus input and pressure](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features)
- [Android palm rejection and canceled pointer events](https://developer.android.com/develop/adaptive-apps/cookbook/stylus-palm-rejection)
