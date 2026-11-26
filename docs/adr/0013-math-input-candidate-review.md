# ADR 0013 · Math input and candidate review

**Status** Partial implementation  
**Ticket** OW-0701 / [KAN-34](https://omninumeris.atlassian.net/browse/KAN-34)  
**Traceability** FR-MAT-01

## Decision

The typed editor and media recognizers use the same parser. The typed editor preserves its existing live preview and independently saved draft. A local media adapter receives a source selection with a content and revision UUID. The input resolver attaches that selection to each candidate and limits a recognition response to eight expressions of up to 512 UTF-16 code units each. The review keeps the original text alongside edits. Explicit review parses the chosen candidate and retains the source anchor with the resulting AST.

Selection contracts identify notebook strokes within a page, the region of a capture asset, a book locator with quote context, or a region within a PDF page. Regions use fractional coordinates in the source orientation. The source engines retain ownership of the original content, authorize revision reads and adapt their locators to this contract. The review does not write the source, put recognized text in saved instance state, or pass it to a solver on its own.

Recognition work runs on a background dispatcher, one request at a time per editor. Replacing or dismissing a request cancels it and discards its result. A media review requires a session for one source revision. The source owner revokes the session on lock, revision change or loss of access. The review clears the preview, candidates, confirmed expression and derived result and cancels in-flight work. Confirmation and asynchronous result publication check revocation before use. An absent adapter, revoked source, unsupported selection, empty output or read failure leaves the typed editor available.

## Remaining integrations

- Handwriting requires the notebook and ink-recognition engines. [KAN-64](https://omninumeris.atlassian.net/browse/KAN-64) · [KAN-84](https://omninumeris.atlassian.net/browse/KAN-84)
- Camera math requires capture, secure original retention and an approved, checksum-verified local recognition model. Pix2Text remains a benchmark candidate. [KAN-87](https://omninumeris.atlassian.net/browse/KAN-87) · [Recognition evaluation](0005-exact-math-and-ocr-evaluation.md)
- Book and PDF math require reader selections connected to persisted document and revision identifiers. [KAN-92](https://omninumeris.atlassian.net/browse/KAN-92) · [KAN-101](https://omninumeris.atlassian.net/browse/KAN-101) · [Confluence anchor schema](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/622636)

The app reports unavailable sources while these integrations are pending. Each source engine must authorize the read and wire its real lock, access and revision events to the review session. [Confluence math pipeline](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382)

## Verification

On 2026-11-25, five math-input unit tests passed. They covered a typed equation, multiple candidates and independent edits, revision retention, correction before AST handoff, unavailable and locked inputs, and cancellation with serialized engine calls. The PDF revision test uses a test adapter. Gradle checks, lint, debug, release and test APK builds passed. Seven math editor and calculator UI tests passed on Android 10 in airplane mode with Wi-Fi and cellular data disabled.

Built the opt-in test APK with `-PformulaAndroidSpike=true` and ran `Pix2TextAndroidBenchmarkTest` with `-e verifyMathInputReview true`. On the offline Android 10 emulator and the Samsung ARM64 tablet, the checksum-verified Pix2Text benchmark model recognized the printed fraction P02, printed radical P03, and synthetic pen fraction H02. The shared review initially exposed the raw text without an AST. After the test applied the labelled correction and explicitly checked it, the AST retained the raw text and revision anchor. Both devices returned incomplete OCR for H04, which produced an unsupported state with no candidate. Each device ran nine public corpus fixtures. The tablet's 22 saved stroke files matched byte for byte after testing.

After adding media-session revocation, all 13 math-review unit tests and the Gradle check, lint and APK builds passed. The new cases cover lock after solving, revision change during non-cancellable OCR, and a confirmation attempt before the revocation observer runs. On the offline Android 10 emulator, the nine-fixture Pix2Text regression passed. The three completed review cases dropped their review state when the benchmark revoked the session. Five standard editor and verification instrumentation tests also passed offline. The standard debug, release and test APKs contained no ONNX, TFLite or GGUF assets. The production source engines still need to supply revocation events.

The ordinary debug, release and restored test APKs exclude the benchmark runtime and model files. The debug and release manifests request no Internet permission.
