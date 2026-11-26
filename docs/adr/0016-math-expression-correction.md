# ADR 0016 · Math expression correction

**Status** Ready for review  
**Ticket** OW-0705 / [KAN-38](https://omninumeris.atlassian.net/browse/KAN-38)  
**Traceability** FR-MAT-05

## Decision

The math editor opens a correction review from the typed expression field. Review displays the original input separately from an editable draft and reuses the math editor's fraction, power, root, cursor and delete controls. A source host may supply a read-only, authorized text or image preview from the same revision as its recognition request. Review retains the recognition engine's original text and anchor, and displays the authorized source preview when present.

Checking runs the existing parser on the computation dispatcher. A malformed expression keeps its exact draft and reports the parser error and source position when available. Successful parsing enables a separate confirmation action. Editing the text, selecting another candidate, or closing the review cancels the check. An additional current-state identity check prevents a completed background parse from publishing after the draft changed. A consumer must bind any later solution and verification to the current confirmed input.

Each review has a fresh identity. The editable field is keyed by that identity and candidate selection, even if the replacement review starts at the same candidate index. Keyboard and template edits carry both values. Delayed edits from an obsolete dialog or selection are ignored.

Cancel leaves the original editor text or external source unchanged. A confirmed typed correction replaces the typed editor draft. Confirmed media text and its anchor stay in the in-memory review session for the solver or source host. The review does not write to the external source or store its preview in saved instance state. The input host supplies static image previews bounded to 1,024 pixels per edge and revokes the review session when protected content locks. Large typed input displays a bounded original preview while keeping its full draft for error handling.

## Remaining integrations

The notebook lasso exposes correction and confirmation for a manual transcription with an original stroke preview. Automatic OCR and production camera, book and PDF routes remain tracked under unified inputs. The correction host contract and source preservation are exercised by the anchored recognition fixture. [Unified math inputs](https://omninumeris.atlassian.net/browse/KAN-34) · [Solver and verification](0017-deterministic-math-explanations.md) · [OMNI math architecture](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382)

## Verification

On 2026-11-25, seven math input ViewModel tests passed. The stale-result test completed a background parse while holding its publication, then checked that edits and dismissal prevented it from returning to the UI. The typed UI test preserved the original `x^2=27`, displayed the syntax error for `x^`, invalidated the parsed state after a subsequent edit, and applied `x^3=27` only after confirmation.

Gradle checks, lint and the debug, release and instrumentation builds passed. All 12 calculator and editor instrumentation tests passed on the Android 10 emulator in airplane mode with Wi-Fi and cellular data disabled. The opt-in Pix2Text benchmark on the same emulator processed nine fixtures and verified five candidate reviews. Its P04 exponent fixture displayed the original source image and editable recognition output together in the correction dialog, then passed parsing and explicit confirmation with the same source anchor. The tablet was disconnected.

After tying editor events to the current review, all 17 math-review unit tests, the full Gradle checks, lint and APK builds passed. Nine Android instrumentation tests passed offline on Android 10. The replacement-review test also passed on the Samsung SM-X806B, and checksums for all 22 saved stroke files matched. The opt-in OCR regression passed again offline with nine fixtures and five review cases, including P04.

On 2026-11-26, all 17 current math-input unit tests and eight offline API 29 editor and verification instrumentation tests passed. The existing solve/edit regression now verifies that changing a draft clears an already displayed verified result, in addition to suppressing a late result. The offline correction workflow retains malformed text with its error and preserves the original on cancel. The earlier P04 anchor and exponent fixture remains the OCR correction evidence.
