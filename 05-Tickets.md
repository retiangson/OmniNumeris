# Implementation backlog

Tickets are draft / To Do unless their status indicates otherwise. IDs are local placeholders, not Jira issue keys. Priority P0 = foundational or release-blocking, P1 = required product scope. Dependencies are indicative.

## Initial engineering tickets

### OW-001 --- Bootstrap Android project and CI

Type: Spike/Task \| Priority: P0 \| Status: In Review

Jira: [KAN-27](https://omninumeris.atlassian.net/browse/KAN-27).

Acceptance: API 29 ARM64 build; unit/lint checks on PR; module dependency rules. Record results and ADR; do not claim feasibility without evidence.

Traceability: NFR-08, with baseline coverage for FR-CUS-01 and NFR-06.

Decisions: [ADR 0001](docs/adr/0001-android-bootstrap.md) and [ADR 0002](docs/adr/0002-android-layer-boundaries.md).

Evidence (2026-11-24):

- Windows build with JDK 21 and Android SDK 36 passed `:check :app:assembleDebug :app:assembleRelease` from clean app outputs. The same verification passed with Gradle offline after dependency setup.
- Two JVM layout tests passed. Android lint reported zero errors and seven dependency update notices. The module dependency check rejected an injected AndroidX dependency in `:domain`.
- APK inspection confirmed `minSdk=29` and ARM64 native code in the unsigned release APK. The debug APK contains ARM64 and x86-64 native libraries and requests no Internet permission.
- On the API 35 x86-64 Pixel Tablet emulator, the debug APK launched in airplane mode with side-by-side content at 1280 dp and stacked content at a simulated 360 dp phone width.
- On a physical Samsung SM-X806B running API 36, the debug APK installed and launched using ARM64. UI inspection confirmed stacked content at 824 dp in portrait and side-by-side content at 1318 dp in landscape. The tablet's original rotation setting was restored.
- The pull request workflow passed local actionlint validation. A hosted pull request run and execution on API 29 ARM64 hardware are pending.

KAN-27 implementation evidence (2026-11-24):

- Added Kotlin/JVM application use cases, Android infrastructure, Hilt composition, lifecycle-collected StateFlow, and a Room transaction adapter. The launch path checks private storage before showing the ready state. The Room adapter is ready for the schema and database binding in OW-006.
- `:check :app:assembleDebug :app:assembleRelease` passed with six unit tests and zero lint errors. Lint reported 17 dependency update notices and one advisory on using reclaimable disk space for large allocations. The startup probe checks currently available space and performs no allocation.
- Temporary negative checks confirmed that the build rejects an application-to-infrastructure dependency and that the CI Gradle command fails when a JUnit test fails.
- The rebuilt debug APK launched on the API 36 ARM64 Samsung tablet and reached the storage-ready state through Hilt and the ViewModel. APK inspection confirmed API 29 as the minimum and no Internet permission.
- The checks and both APK builds passed with Gradle offline after dependency setup. Jira moved KAN-27 to In Review.
- The standard debug APK reached the storage-ready screen in airplane mode on the API 29 x86-64 emulator during KAN-31. This adds minimum-API runtime evidence. API 29 ARM64 and hosted pull request verification are pending.

### OW-002 --- Benchmark 3 GB local inference

Type: Spike/Task \| Priority: P0 \| Status: In Progress

Acceptance: Measure peak RSS, first token, throughput, thermal and cancellation on physical device. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-28](https://omninumeris.atlassian.net/browse/KAN-28).

Decision: [ADR 0003](docs/adr/0003-local-inference-spike.md).

Evidence (2026-11-24): The opt-in ARM64 benchmark built and five unit tests passed. Three generation runs and two cancellation runs passed on the API 36 Samsung tablet. The tablet reports about 7.1 GiB of RAM to Android. No physical 3 GB device is available, so reference-device feasibility remains unverified. See the [procedure and measurements](docs/benchmarks/local-inference.md#samsung-tablet-pilot).

### OW-003 --- Prototype pressure-sensitive tiled canvas

Type: Spike/Task \| Priority: P0 \| Status: In Review

Acceptance: Measure ink latency and frame rate; recover after process death. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-29](https://omninumeris.atlassian.net/browse/KAN-29).

Decision: [ADR 0004](docs/adr/0004-ink-canvas-prototype.md).

Evidence (2026-11-24): The offline Gradle checks, debug and release APKs, and instrumentation APK passed. Thirteen standard unit tests passed with zero lint errors. Samsung tablet instrumentation preserved variable stylus pressure and measured 102.2 ink-bearing draws/s with 14 ms p95 event-to-draw latency. An injected finger stroke measured 117.6 draws/s and 12 ms p95. All three committed strokes recovered after force-stop. The release manifest excludes the prototype activity. Physical pen contact, display scan-out, and sustained memory-pressure behavior still need validation.

Device follow-up (2026-11-25): Added pressure-sensitive width presets, a whole-stroke eraser, session undo and default stylus-only input. Two isolated Samsung instrumentation tests passed and 53 existing strokes were preserved. Physical palm-contact testing remains open.

### OW-004 --- Evaluate math OCR and CAS

Type: Spike/Task \| Priority: P0 \| Status: In Review

Acceptance: Label corpus; compare recognition, supported classes, step validity and licences. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-30](https://omninumeris.atlassian.net/browse/KAN-30).

Decision: [ADR 0005](docs/adr/0005-exact-math-and-ocr-evaluation.md).

Evidence (2026-11-24): Twenty labelled typed expressions and 100 seeded linear equations passed. The Samsung tablet measured warm p95 at 0.254 ms for arithmetic and 0.400 ms for the linear sample. The Windows Tesseract baseline matched 0/5 printed and 1/4 synthetic pen fixtures exactly. Dedicated 2D OCR and actual handwriting performance remain unverified. See the [separate recognition and solver measurements](docs/benchmarks/math-recognition-and-solving.md).

Evidence (2026-11-25): Added the required solver class matrix and measured PP-FormulaNet-S on the same labelled Windows corpus. It matched 4/5 printed and 2/4 synthetic pen fixtures structurally. Human handwriting, Android runtime compatibility and mobile OCR performance remain open.

Device evidence (2026-11-25): The opt-in Android Tesseract baseline completed on the Samsung SM-X806B and the offline API 29 emulator. Both matched 0/5 printed and 1/4 synthetic pen fixtures. The ordinary builds and checks passed. The Windows ONNX converter failed with both stable Paddle versions and one development build, leaving dedicated formula OCR on Android open.

Human handwriting evidence (2026-11-25): The dedicated Windows recognizer matched 6/12 tablet samples from one writer. The second prompted pass matched 3/4, including stacked fractions, the exponent and the linear equation. It missed the square root. Windows Tesseract matched 0/4 on the first prompted pass.

Dedicated Android evidence (2026-11-25): Pix2Text MFR matched 5/5 printed and 3/4 synthetic fixtures on both the Samsung tablet and the offline Android 10 emulator. On the tablet it matched 6/12 human samples with warm medians of 424.12–683.97 ms. Production model selection, broad handwriting accuracy and sustained performance remain open.

The opt-in instrumentation build, both device runs, ordinary Gradle checks and debug and release builds passed. The ordinary APKs exclude the experimental model, runtime and user test samples.

### OW-005 --- Evaluate PDF and EPUB SDKs

Type: Spike/Task \| Priority: P0 \| Status: In Review

Acceptance: Test API 29, annotations, forms, text edits, anchors and commercial rights. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-31](https://omninumeris.atlassian.net/browse/KAN-31).

Decision: [ADR 0006](docs/adr/0006-document-sdk-evaluation.md).

Evidence (2026-11-24): Five document SDK tests passed on an API 29 x86-64 emulator in airplane mode and on the API 36 ARM64 Samsung tablet. The PDF checks verified rendering, password-authorized access, form and annotation export, the unchanged original, and a reopened one-page export. The EPUB checks verified local search and fragment restoration after reflow at two text sizes. The default APK builds and checks passed offline, and fixture assets were absent from default packages. Generic text replacement, signature handling, sustained memory stress and production SDK approval remain open. Jira moved KAN-31 to In Review.

### OW-006 --- Design encrypted storage and backup

Type: Spike/Task \| Priority: P0 \| Status: In Review

Acceptance: Threat model; key lifecycle; tamper and restore tests. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-32](https://omninumeris.atlassian.net/browse/KAN-32).

Decision: [ADR 0007](docs/adr/0007-protected-storage-and-portable-recovery.md).

Evidence (2026-11-25):

- Debug vault prototype with Keystore-wrapped notebook keys, authenticated content and portable backups, Room schema export and a non-destructive v1-to-v2 migration.
- Five instrumentation cases passed on Samsung SM-X806B (API 36) and the offline API 29 emulator. Actual process death and restore into a fresh Android user also passed on the emulator.
- Architecture checks, unit tests, lint and both app builds passed. The release APK excludes the prototype. Production cryptographic and lock lifecycle review remains pending.

### OW-007 --- Define product entitlements and model distribution

Type: Spike/Task \| Priority: P0 \| Status: In Review

Acceptance: Document Free/Pro boundaries, licence inventory, download integrity and offline purchase policy. Record results and ADR; do not claim feasibility without evidence.

Jira: [KAN-33](https://omninumeris.atlassian.net/browse/KAN-33).

Decision and evidence (2026-11-25): [ADR 0008](docs/adr/0008-entitlements-and-model-distribution.md) proposes the entitlement matrix, offline restore and revocation policy, signed catalogue, download lifecycle and license inventory. The benchmark fixture's pinned size and digest matched and all five benchmark unit tests passed. Pricing, advanced gates, verification architecture and production model approvals remain open.

## EP-LIB --- Workspace library

### OW-0201 --- Nested folders

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-01

Implement nested folders.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0202 --- tags

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-02

Implement tags.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0203 --- favourites

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-03

Implement favourites.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0204 --- recent items

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-04

Implement recent items.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0205 --- grid/list

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-05

Implement grid/list.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0206 --- sorting

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-06

Implement sorting.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0207 --- batch operations

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-07

Implement batch operations.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0208 --- duplicate detection

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-08

Implement duplicate detection.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0209 --- recycle bin

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-09

Implement recycle bin.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0210 --- snapshots

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-10

Implement snapshots.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0211 --- version comparison and restore

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-11

Implement version comparison and restore.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0212 --- tabs and session restoration

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-LIB \| Requirement: FR-LIB-12

Implement tabs and session restoration.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-NOT --- Hybrid notebook

### OW-0301 --- Fixed pages and infinite canvas

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-01

Implement fixed pages and infinite canvas.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0302 --- free-position objects and structured rich text

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-02

Implement free-position objects and structured rich text.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0303 --- tables

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-03

Implement tables, lists, checklists.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0304 --- page sizes and plain/lined/grid/dot/Cornell/music/custom templates

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-04

Implement page sizes and plain/lined/grid/dot/Cornell/music/custom templates.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0305 --- layers

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-05

Implement layers.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0306 --- transforms

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-06

Implement transforms.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0307 --- undo/redo

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-07

Implement undo/redo.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0308 --- autosave

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-08

Implement autosave.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0309 --- attachments

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-09

Implement attachments.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0310 --- split screen

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-10

Implement split screen.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0311 --- floating tools

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-NOT \| Requirement: FR-NOT-11

Implement floating tools.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-DRW --- Drawing studio

### OW-0401 --- Vector ink and tiled raster painting

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-DRW \| Requirement: FR-DRW-01

Implement vector ink and tiled raster painting.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0402 --- pressure and tilt

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-DRW \| Requirement: FR-DRW-02

Implement pressure and tilt.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0403 --- brush presets

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-DRW \| Requirement: FR-DRW-03

Implement brush presets, smoothing, stabilization, opacity, palettes, eyedropper, fill, gradients, symmetry, layer blend modes, pixel/stroke erasers, lasso, transforms, rotation, reference images and transparent high-resolution export.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-STY --- Stylus

### OW-0501 --- Active pen detection

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-01

Implement active pen detection.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0502 --- palm rejection

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-02

Implement palm rejection.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0503 --- stylus-only mode

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-03

Implement stylus-only mode.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0504 --- finger navigation

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-04

Implement finger navigation.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0505 --- pressure calibration

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-05

Implement pressure calibration.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0506 --- tilt

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-06

Implement tilt, hover and buttons where supported.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0507 --- handedness and latency

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-STY \| Requirement: FR-STY-07

Implement handedness and latency.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-HWR --- Handwriting

### OW-0601 --- Offline English stroke recognition

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-01

Implement offline English stroke recognition.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0602 --- image handwriting recognition

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-02

Implement image handwriting recognition.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0603 --- editable conversion

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-03

Implement editable conversion.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0604 --- corrections

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-04

Implement corrections.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0605 --- search indexing

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-05

Implement search indexing.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0606 --- preserve original ink

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-06

Implement preserve original ink.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0607 --- beautification and language extension

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-HWR \| Requirement: FR-HWR-07

Implement beautification and language extension.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-MAT --- Mathematics

### OW-0701 --- Typed

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-01

Jira [KAN-34](https://omninumeris.atlassian.net/browse/KAN-34).

The typed adapter and common candidate review retain edited and original text plus the source revision. Production handwriting, camera, book and PDF integrations remain pending. [Input contracts and remaining integrations](docs/adr/0013-math-input-candidate-review.md)

Implement typed, handwritten, camera, book and PDF inputs.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0702 --- math keyboard and LaTeX

Type: Story \| Status: In Review \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-02

Implement math keyboard and LaTeX.

Jira: [KAN-35](https://omninumeris.atlassian.net/browse/KAN-35).

Evidence (2026-11-25): The normal workspace exposes the offline editor, structured keys, clipboard and editable error states. The parser shares the existing Kotlin AST. Three parser and three editor unit tests passed, plus keypad and clipboard instrumentation on the offline Android 10 emulator. Build and lint passed. See the [editor contract and supported syntax](docs/adr/0009-offline-math-editor.md).

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0703 --- circle/lasso recognition

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-03

Implement circle/lasso recognition.

The debug canvas selects source-space ink and displays a clipped preview with per-stroke exclusion. Live recognition and production notebook and document selection remain pending. [Selection design and evidence](docs/adr/0019-lasso-selection.md)

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0704 --- manual

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-04

Implement manual, suggestion and automatic insertion modes.

The application policy binds a verified result to its reviewed expression and a revisioned destination. Production note writes, preferences and undo depend on the notebook engine. [Insertion design and evidence](docs/adr/0020-verified-math-insertion.md)

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0705 --- expression correction

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-05

Jira [KAN-38](https://omninumeris.atlassian.net/browse/KAN-38).

The math editor can review the original expression, correct the draft, check syntax and explicitly confirm it. The review supports an authorized source preview and the typed solver discards stale results. Production media routes remain pending. [Correction design and verification](docs/adr/0016-math-expression-correction.md)

Implement expression correction.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0706 --- step generation

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-06

Jira [KAN-39](https://omninumeris.atlassian.net/browse/KAN-39).

Confirmed review drafts support exact steps, hints, two verified methods for linear equations, substitution checks and encrypted typed solution history. The linked-history flow can reopen a verified solution in the active source revision. Production scoped storage and navigation back to an original media source remain pending. [Solver design and verification](docs/adr/0017-deterministic-math-explanations.md)

Implement step generation, validation, hints, alternative methods, history and source links.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0707 --- verified versus unverified results

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-MAT \| Requirement: FR-MAT-07

Jira [KAN-40](https://omninumeris.atlassian.net/browse/KAN-40).

Typed math labels checked proofs, unverified model text, unsupported inputs and failed verification separately. Production local-model and media-source integration remain pending. [Verification design and evidence](docs/adr/0018-math-verification-status.md)

Implement verified versus unverified results.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-CAL --- Calculator

### OW-0801 --- Floating movable resizable calculator throughout app

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-CAL \| Requirement: FR-CAL-01

Jira [KAN-41](https://omninumeris.atlassian.net/browse/KAN-41).

The workspace and math editor share an in-app calculator panel that moves, resizes, collapses and restores. Production notebook and reader launch acceptance remains pending. [Panel design and integration evidence](docs/adr/0014-in-app-floating-calculator.md)

Implement floating movable resizable calculator throughout app.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0802 --- standard

Type: Story \| Status: In Review \| Priority: P0 \| Epic: EP-CAL \| Requirement: FR-CAL-02

Implement standard, scientific, programmer, financial, statistics, matrices, graphing and unit conversions.

Jira: [KAN-42](https://omninumeris.atlassian.net/browse/KAN-42).

Evidence (2026-11-25): Eight typed modes are available from the Compose workspace. Six domain and four app calculator tests passed, along with the UI test of all eight modes on the offline Android 10 emulator. Gradle checks, lint and debug, release and instrumentation builds passed. Samsung standard arithmetic measured 13.19 ms on the first call and 0.286 ms warm p95 across 200 samples. [Mode and precision contract](docs/adr/0010-offline-calculator-modes.md)

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0803 --- exact fractions

Type: Story \| Status: In Progress \| Priority: P0 \| Epic: EP-CAL \| Requirement: FR-CAL-03

Jira [KAN-43](https://omninumeris.atlassian.net/browse/KAN-43).

Standalone calculator precision, exact-value memory, clipboard and encrypted history are implemented. The insertion policy preserves the canonical value and requires approval for the current note revision. The production note editor and transactional adapter remain pending. [Precision, storage and outstanding integration](docs/adr/0015-calculator-exact-values-and-history.md)

Implement exact fractions, precision, memory, history, clipboard and insertion into active notes.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-GRP --- Graphing

### OW-0901 --- 2D multi-function plots

Type: Story \| Status: In Review \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-01

Jira: [KAN-44](https://omninumeris.atlassian.net/browse/KAN-44).

Evidence (2026-11-25): The graphing workspace renders up to four functions with labelled axes and distinct dash patterns, preserves curves after invalid input and breaks at discontinuities. Five sampler tests, two Android 10 offline UI tests, Gradle checks and builds passed. [Sampling and verification](docs/adr/0011-offline-two-dimensional-plots.md)

Implement 2D multi-function plots.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0902 --- pan/zoom

Type: Story \| Status: In Review \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-02

Jira [KAN-45](https://omninumeris.atlassian.net/browse/KAN-45).

On 2026-11-25, viewport and cancellation tests, three Android 10 offline UI tests and the Gradle build and lint checks passed. The gesture test verified drag, off-center pinch and reset. Physical tablet gesture timing still needs measurement. [Navigation and verification](docs/adr/0011-offline-two-dimensional-plots.md#kan-45-verification)

Implement pan/zoom.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0903 --- roots

Type: Story \| Status: In Review \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-03

Jira [KAN-46](https://omninumeris.atlassian.net/browse/KAN-46).

On 2026-11-25, the graph reported exact supported results and labeled numerical estimates for the visible x-range. Eight graph domain tests, four calculator ViewModel tests, five Android 10 offline tests and the Gradle build and lint checks passed. [Numerical tolerances, limitations and verification](docs/adr/0012-graph-roots-and-intersections.md)

Implement roots, intercepts and intersections.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0904 --- parameters

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-04

Implement parameters.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0905 --- scatter and statistics

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-05

Implement scatter and statistics.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0906 --- polar and parametric plots

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-06

Implement polar and parametric plots.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0907 --- adaptive 3D

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-07

Implement adaptive 3D.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-0908 --- editable insertion and vector/image export

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-GRP \| Requirement: FR-GRP-08

Implement editable insertion and vector/image export.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-SCN --- Scanner

### OW-1001 --- Camera/import

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-01

Implement camera/import.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1002 --- automatic boundaries and stable capture

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-02

Implement automatic boundaries and stable capture.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1003 --- manual corners

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-03

Implement manual corners.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1004 --- perspective

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-04

Implement perspective, shadow and contrast correction.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1005 --- colour/grayscale/mono

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-05

Implement colour/grayscale/mono.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1006 --- multi-page

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-06

Implement multi-page.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1007 --- receipts

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-07

Implement receipts, books, whiteboards, business cards.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1008 --- dewarping

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-08

Implement dewarping.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1009 --- OCR and searchable PDF

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-09

Implement oCR and searchable PDF.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1010 --- structured extraction

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-10

Implement structured extraction.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1011 --- preserve originals

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-SCN \| Requirement: FR-SCN-11

Implement preserve originals.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-PDF --- PDF editor

### OW-1101 --- Read password-authorized PDFs

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PDF \| Requirement: FR-PDF-01

Implement read password-authorized PDFs.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1102 --- search

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PDF \| Requirement: FR-PDF-02

Implement search, thumbnails, bookmarks.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1103 --- highlight

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PDF \| Requirement: FR-PDF-03

Implement highlight, underline, strikeout, ink, comments, text boxes, shapes.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1104 --- merge/split/extract/reorder/rotate/insert/delete

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PDF \| Requirement: FR-PDF-04

Implement merge/split/extract/reorder/rotate/insert/delete.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1105 --- forms

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PDF \| Requirement: FR-PDF-05

Implement forms, handwritten signatures, supported text edits, compression, password export, annotation flattening and signature invalidation warnings.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-IO --- Interoperability

### OW-1201 --- Import PDF

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-IO \| Requirement: FR-IO-01

Implement import PDF, EPUB, DOCX, TXT, Markdown, HTML, CBZ, JPEG, PNG, WebP, SVG, WAV, MP3, M4A, GGUF and native backups.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1202 --- export PDF/searchable PDF

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-IO \| Requirement: FR-IO-02

Implement export PDF/searchable PDF, PNG/JPEG, SVG, DOCX, TXT, Markdown, LaTeX, CSV, audio and native editable format.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1203 --- region/page/batch/folder/workspace export

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-IO \| Requirement: FR-IO-03

Implement region/page/batch/folder/workspace export.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1204 --- quality

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-IO \| Requirement: FR-IO-04

Implement quality, compression, cancellation and file picker.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-BOK --- Book reader

### OW-1301 --- Collections

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BOK \| Requirement: FR-BOK-01

Implement collections, covers, metadata, reading position, contents, chapters, thumbnails, progress, search, bookmarks, coloured highlights, ink and typed annotations.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1302 --- font

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BOK \| Requirement: FR-BOK-02

Implement font, spacing, margins, themes, scrolling/paging, dual page, zoom, dictionary, translation, annotation export, split-screen notes and reading statistics.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1303 --- circle-to-solve with linked solutions

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BOK \| Requirement: FR-BOK-03

Implement circle-to-solve with linked solutions.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-TTS --- Read aloud

### OW-1401 --- Offline voices

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-TTS \| Requirement: FR-TTS-01

Implement offline voices.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1402 --- selection/page/chapter playback

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-TTS \| Requirement: FR-TTS-02

Implement selection/page/chapter playback.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1403 --- pause/resume

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-TTS \| Requirement: FR-TTS-03

Implement pause/resume, speed, spoken text highlight, sentence navigation, listening position, background controls, sleep timer, scanned text and supported math speech.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1404 --- no network voice fallback

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-TTS \| Requirement: FR-TTS-04

Implement no network voice fallback.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-STD --- Study centre

### OW-1501 --- Manual and AI flashcards with media

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-STD \| Requirement: FR-STD-01

Implement manual and AI flashcards with media, spaced repetition, review ratings and source links.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1502 --- quizzes

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-STD \| Requirement: FR-STD-02

Implement quizzes, MCQ, true/false, short answers and handwriting.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1503 --- timed exams

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-STD \| Requirement: FR-STD-03

Implement timed exams, difficulty, hints, verified grading, mistake analysis, practice generation, goals, schedules, reminders, focus timer, progress, revision sheets and concept maps.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1504 --- manual mode without AI

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-STD \| Requirement: FR-STD-04

Implement manual mode without AI.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-AI --- Local assistant

### OW-1601 --- General chat

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AI \| Requirement: FR-AI-01

Implement general chat, streaming, stop/regenerate, editable prompts, history, custom prompts, grammar, rewrite, tone, summary, translation, brainstorming, tutoring, code, tables, diagrams, Markdown/math rendering, citations, private context mode, export/delete and insertion.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-VIS --- Multimodal AI

### OW-1701 --- Analyze photos

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-VIS \| Requirement: FR-VIS-01

Implement analyze photos, drawings, objects, diagrams, charts and geometry.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1702 --- image questions

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-VIS \| Requirement: FR-VIS-02

Implement image questions, combined prompts and selected regions.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1703 --- adaptive resolution

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-VIS \| Requirement: FR-VIS-03

Implement adaptive resolution.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1704 --- OCR fallback

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-VIS \| Requirement: FR-VIS-04

Implement oCR fallback.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1705 --- compatible vision model/projector imports

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-VIS \| Requirement: FR-VIS-05

Implement compatible vision model/projector imports.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-RAG --- Knowledge management

### OW-1801 --- Index permitted notes

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-RAG \| Requirement: FR-RAG-01

Implement index permitted notes, ink recognition, OCR, books, documents, transcripts, solutions and study materials.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1802 --- keyword and semantic search

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-RAG \| Requirement: FR-RAG-02

Implement keyword and semantic search.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1803 --- hybrid retrieval

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-RAG \| Requirement: FR-RAG-03

Implement hybrid retrieval, incremental updates and deletion.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1804 --- configurable scope

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-RAG \| Requirement: FR-RAG-04

Implement configurable scope, clickable region citations, links, backlinks, interactive graph and approved relationship suggestions.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-AGT --- AI actions

### OW-1901 --- Propose create/edit/organize notes

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AGT \| Requirement: FR-AGT-01

Implement propose create/edit/organize notes, diagrams and study content.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1902 --- preview and approval

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AGT \| Requirement: FR-AGT-02

Implement preview and approval.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1903 --- validate commands

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AGT \| Requirement: FR-AGT-03

Implement validate commands, execute authorized services, undo and action history.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-1904 --- no arbitrary filesystem/database access

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AGT \| Requirement: FR-AGT-04

Implement no arbitrary filesystem/database access.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-MDL --- Model management

### OW-2001 --- Device profiling

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-MDL \| Requirement: FR-MDL-01

Implement device profiling.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2002 --- curated catalogue

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-MDL \| Requirement: FR-MDL-02

Implement curated catalogue.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2003 --- resumable Wi-Fi downloads

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-MDL \| Requirement: FR-MDL-03

Implement resumable Wi-Fi downloads, checksums, GGUF imports and architecture validation.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2004 --- multiple models

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-MDL \| Requirement: FR-MDL-04

Implement multiple models, task routing, basic/expert settings, context, sampling, threads, acceleration, benchmarks, licence display, versions and deletion.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2005 --- dynamic loading

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-MDL \| Requirement: FR-MDL-05

Implement dynamic loading, memory/thermal monitoring and task queue.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-AUD --- Audio

### OW-2101 --- Record/pause/resume

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-AUD \| Requirement: FR-AUD-01

Implement record/pause/resume, foreground indicators, timestamp ink synchronization, playback speed, bookmarks, import, adaptive live/deferred offline transcription, editable timestamped segments, search, summaries, export, cancellation and interruption recovery.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-DGM --- Diagrams

### OW-2201 --- Shapes

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-DGM \| Requirement: FR-DGM-01

Implement shapes, arrows, connectors, mind maps, flowcharts, org charts, selected UML, ruler, compass, protractor, dimensions, snapping, alignment, distribution, templates, AI editable diagrams and SVG export.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-PRS --- Presentation

### OW-2301 --- Full screen

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-PRS \| Requirement: FR-PRS-01

Implement full screen, navigation, laser pointer, temporary annotations, external displays, presenter controls, zoom and optional annotation saving.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-SEC --- Privacy and backup

### OW-2401 --- App PIN/biometrics

Type: Story \| Status: To Do \| Priority: P0 \| Epic: EP-SEC \| Requirement: FR-SEC-01

Implement app PIN/biometrics, notebook encryption, auto-lock, protected caches/indexes, secure handwritten signatures, automatic/manual local backup, encrypted export, validation, selective/full restore, offline device transfer, deletion, permission minimization and screenshot protection.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-CUS --- Customization

### OW-2501 --- Light/dark/system themes

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-CUS \| Requirement: FR-CUS-01

Implement light/dark/system themes, accents, toolbars, gestures, shortcuts, keyboard/mouse, adaptive layouts, widgets, share menu, quick capture, defaults, English-first localization and accessibility.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

## EP-BIL --- Commercial

### OW-2601 --- Free unlimited core notebook and calculator

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BIL \| Requirement: FR-BIL-01

Implement free unlimited core notebook and calculator.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2602 --- one-time Pro

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BIL \| Requirement: FR-BIL-02

Implement one-time Pro, no ads or app account.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2603 --- Play Billing

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BIL \| Requirement: FR-BIL-03

Implement play Billing, restore, cached entitlement, preserve user data, clear feature gates and licence notices.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.

### OW-2604 --- pricing pending market research

Type: Story \| Status: To Do \| Priority: P1 \| Epic: EP-BIL \| Requirement: FR-BIL-04

Implement pricing pending market research.

Acceptance criteria:

-   Demonstrate the specified capability with representative valid input and verify persisted output where applicable.
-   Handle invalid input, cancellation or unavailable resources without losing existing content.
-   Verify offline operation after setup (except explicit download/purchase flows), and add automated regression coverage.

Dependencies: OW-001; relevant engine and security contracts; see roadmap.
