# ADR 0011 · Offline 2D function plots

**Status** Provisional  
**Tickets** OW-0901 / [KAN-44](https://omninumeris.atlassian.net/browse/KAN-44), OW-0902 / [KAN-45](https://omninumeris.atlassian.net/browse/KAN-45)  
**Traceability** FR-GRP-01, FR-GRP-02

## Decision

Take function expressions from the calculator's existing Graphing contract and sample its Kotlin math AST on the injected computation dispatcher. The baseline viewport covers x and y from −10 to 10. A plot uses 400 horizontal intervals. The sampler accepts a validated viewport and a bounded sample count for future navigation. It checks cancellation during computation.

Evaluate finite real values at sample positions. Before joining adjacent samples, evaluate an interval enclosure over the entire interval between them. A denominator whose enclosure includes zero, a square root outside its real domain, an unsupported power or non-finite arithmetic breaks the path. This also separates poles that fall between grid points. Clip drawable segments to the viewport.

The plotted values use binary64 arithmetic. Interval enclosure may omit visible portions when the same variable appears repeatedly in a denominator or radical. The algorithm errs toward leaving gaps in those cases. Features narrower than the sample spacing may be missed. A subsequent graphing story covers roots and intersections.

The workspace retains up to four functions. Color, dash patterns and labeled legends distinguish curves. A rejected or canceled attempt preserves existing curves. Individual functions can be removed. The calculator ViewModel retains curves and drafts while switching modes or resizing.

## Viewport navigation

Plot drawing and touch input use the same chart bounds. Dragging shifts both axes in the direction of the gesture. Pinching keeps the same world point under the moving centroid until the viewport reaches a world boundary. The minimum navigation span per axis is 0.0001 and world coordinates are bounded from −1,000,000 to 1,000,000. The graph also provides zoom and reset buttons, accessibility pan actions and spoken viewport bounds.

The axes respond immediately and nearby samples provide a clipped preview during drag or zoom. The ViewModel resamples saved expressions after a 24 ms pause in viewport changes. Each change cancels the previous job, and sampling checks for cancellation on each interval. The newest samples replace the preview together and preserve calculator drafts and curve membership.

## Verification

Sampler regression tests exercise common coordinates for the line and parabola, on-grid and between-grid poles, removable holes, clipping, root domains, viewport validation and cancellation. Calculator tests exercise invalid input and retention of previous curves.

On 2026-11-25, five sampler tests, two calculator ViewModel tests and the full Gradle checks, lint, debug, release and instrumentation APK builds passed. Both calculator UI tests passed on the Android 10 emulator in airplane mode with Wi-Fi and cellular data disabled. The graphing case added `x^2`, `x` and `1/x`, showed labelled axes and retained all three curves and legends after malformed input.

Visual inspection on the phone-width emulator confirmed the labels, three distinct line styles and a visible break across zero. The updated APK installed on the Samsung tablet with all 22 canvas strokes preserved.

### KAN-45 verification

Four viewport tests cover coordinate round trips, off-center pinch with simultaneous pan, axis motion and finite world bounds. The calculator ViewModel tests cover cancellation and the final viewport after rapid gestures, removal of a curve during resampling, and preservation of an independent calculator result. The Android touch test checks dragging, a two-pointer pinch around an off-center point, maintaining the world x-coordinate at the pinch center and resetting the view without losing the curve.

On 2026-11-25, the full offline Gradle checks, lint, debug, release and instrumentation APK builds passed. All three calculator instrumentation tests passed on an Android 10 phone emulator in airplane mode with Wi-Fi and cellular data disabled.

A 1080 × 1920 Android 10 emulator with 2 GB RAM and SwiftShader ran nine 280 ms drags with three plotted functions. Android `gfxinfo` reported 156 frames, 84 classified as janky, a 19 ms median and a 24 ms 95th percentile. The final debug APK installed on the connected Samsung tablet with 22 saved stroke files matching byte for byte. The tablet's secure lock screen prevented gesture and frame-timing verification on physical hardware.
