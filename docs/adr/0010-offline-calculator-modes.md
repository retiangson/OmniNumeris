# ADR 0010 · Offline calculator mode contracts

**Status** Provisional  
**Ticket** OW-0802 / [KAN-42](https://omninumeris.atlassian.net/browse/KAN-42)  
**Traceability** FR-CAL-02

## Domain contract

The pure Kotlin calculator accepts distinct request types and returns a typed result or a math error. The standard calculator and graph expression handoff share the existing parser. Approximate and decimal outputs have distinct result types so a caller can label their precision correctly. None of the modes calls a network service.

| Mode | Supported operations and precision |
| --- | --- |
| Standard | Shared numeric expression grammar with exact rational results, exact rational roots and bounded integer powers. Equations and free variables belong in the solver. |
| Scientific | Sine, cosine, tangent, real square root and natural logarithm. Trigonometry takes an explicit degree or radian unit. Results use binary64 `StrictMath`. Tests use an absolute tolerance of 1e−12 for the reference angles. Tangent rejects the near-pole region where the absolute cosine is at most 1e−15. |
| Programmer | Signed 64-bit decimal, hexadecimal, octal and binary conversion. Negative values display with a leading sign. |
| Financial | End-of-period compound growth using `principal × (1 + rate)ⁿ`, with a decimal per-period rate and at most 1,200 periods. The engine uses DECIMAL128, which has 34 significant digits and half-even rounding. Fees, deposits, taxes and live rates are outside this operation. |
| Statistics | Exact mean and median for up to 128 rational values. |
| Matrices | Exact multiplication of compatible rectangular matrices, each up to 16 by 16. |
| Graphing | A parsed expression AST for handoff to the graph engine. Plotting and treatment of discontinuities belong to the consumer. |
| Unit conversions | Exact same-dimension conversions for the enumerated length, mass, time and temperature units. Temperature conversions reject values below absolute zero. |

The display precision and history integration will consume these typed results in KAN-43. The calculator overlay is tracked by KAN-41. The graph engine consumes the shared AST in KAN-44.

## Workspace integration

Open **Calculator** from the workspace and choose a mode from its selector. A lifecycle-scoped ViewModel keeps each mode's draft across switches and activity resizing. Edits and mode changes cancel pending result delivery. The selected request runs off the main thread.

Scientific input accepts numeric arithmetic and the constant `pi` or `π`. The UI labels binary64 results approximate and displays up to 15 significant decimal digits. Financial input uses a decimal rate per period, so enter `0.05` for 5 percent. Matrices use commas between columns and a new line for each row. Statistics uses a single comma-separated list. Graphing validates the syntax and returns an AST for the [2D plotter](0011-offline-two-dimensional-plots.md).

## Evidence

On 2026-11-25, six calculator unit tests passed, covering all eight modes and 600 seeded signed-integer radix round trips. Gradle architecture checks, unit tests, lint and debug, release and instrumentation builds passed.

On the Samsung SM-X806B running API 36, the standard calculator returned 14 for `2+3×4` on every call. The first call took 13.19 ms. After 50 warm-up calls, 200 measured calls had a median of 0.105 ms and a p95 of 0.286 ms. The test measured parsing and calculation, with result validation outside the timed interval. It did not measure screen rendering or end-to-end interaction latency.

The Compose workspace integration passed three input adapter tests and one ViewModel test covering draft retention and stale result cancellation. On the Android 10 emulator with airplane mode enabled and Wi-Fi and data disabled, the end-to-end UI test exercised all eight modes, checked error messages and reopened a retained mode draft. Both APKs lacked Internet permission. The final debug, release and instrumentation builds, unit tests and lint passed. The debug build was installed on the Samsung tablet and preserved all 22 canvas strokes.
