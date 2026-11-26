# ADR 0012 · Graph roots, intercepts and intersections

**Status** Provisional  
**Ticket** OW-0903 / [KAN-46](https://omninumeris.atlassian.net/browse/KAN-46)  
**Traceability** FR-GRP-03

## Decision

Search the inclusive x-range of the current graph viewport. Return roots and y-intercepts for each plotted function and intersections for each distinct pair. Include a y-intercept only when x = 0 falls inside the range. Show results for the entire x-range and draw markers for points that also fit the visible y-range.

Pass the existing expression trees to the verified linear solver. Display a rational result as exact after the solver verifies the solution by substitution. Evaluate y-intercepts as exact rationals when the existing evaluator supports them. Label numerical points with an approximation symbol.

For the numerical search, evaluate 512 intervals on the selected x-range. Use the same real-domain interval evaluator as the graph plotter to reject brackets across poles, holes or unsupported regions. Refine sign changes by bisection and refine sampled local minima to detect tangencies that do not change sign. A collapsed floating-point interval alone provides insufficient evidence of coincidence.

Bisection stops at an x-bracket width of the greater of 1e−10 or eight floating-point spacings at the midpoint. The tangency search uses the greater of 1e−14 or eight spacings. Accept a numerical point when the residual is at most 1e−7 of the sampled bracket scale. Exact floating-point zeros identified inside continuous brackets can stop refinement early. These are convergence checks, and their accuracy depends on the expression's conditioning. The search caps each refinement at 96 iterations and returns at most 32 numerical points per function or curve pair. Domain gaps, unresolved candidates and truncated results receive an incomplete-search message.

Closely spaced roots or intersections may fall between samples, and a real-domain endpoint between samples may be missed. A result stating that the search found no point describes the searched range and resolution. Higher precision or symbolic nonlinear solving requires a separate decision.

The ViewModel runs analysis on the calculator dispatcher, checks cancellation inside the search, and invalidates results when the viewport or plotted functions change. The calculator remains offline and stores no new persistent data.

## Verification

On 2026-11-25, eight domain tests covered roots near −2 and 2, intersections near (0, 0) and (1, 1), an exact 1/3 intersection, range endpoints, tangencies, near misses, discontinuities, missing samples and cancellation. The four calculator ViewModel tests passed, including stale-result cancellation after a viewport change. Offline Gradle checks, lint, debug, release and instrumentation APK builds passed.

All five calculator instrumentation tests passed on the Android 10 emulator in airplane mode with Wi-Fi and cellular data disabled. The graph results cases verified approximate roots and intersections, an exact y-intercept, and the empty-root message. The final debug APK installed on the connected Samsung tablet with all 22 stroke files matching byte for byte. The tablet was locked during device verification.

Visual inspection on the phone emulator confirmed the selected range, distinct exact and approximate labels, and root markers on the x-axis.
