# ADR 0018 · Math verification status

**Status** Ready for review  
**Ticket** OW-0707 / [KAN-40](https://omninumeris.atlassian.net/browse/KAN-40)  
**Traceability** FR-MAT-07

## Decision

A deterministic result earns a Verified label when replay of the confirmed AST agrees with the complete reported result and its steps. Linear replay includes substitution in the original equation. The app renders the replayed proof. A mismatched result or failed verification withholds the answer and shows a verification failure. Unsupported and input-error reports also require a replay match, including the diagnostic. The app uses the replayed engine status and detail.

Local model output enters as bounded plain text. The Unverified AI explanation label and the output appear together in a dedicated surface. Accessibility exposes them as one announcement with the status first. The explanation request is explicit and available only for an unsupported standalone typed expression when a local provider is configured. The app saves only verified solutions and checks the proof again when reopening a record. Linked media history requires an authorized source adapter.

An edit, candidate switch or dismissal removes the current result and its status. Late deterministic and local work can publish only to the confirmed expression that launched it.

## Remaining integration

The production math editor currently has no configured local explanation model. The verification contract and presentation use a synthetic provider in tests. The ordinary ink flow now reauthorizes saved history and reruns the deterministic proof before displaying a verification label. Additional media types depend on their live source resolvers. [Solver KAN-39](https://omninumeris.atlassian.net/browse/KAN-39) · [Unified inputs KAN-34](https://omninumeris.atlassian.net/browse/KAN-34)

## Verification

On 2026-11-25, four solver verification tests and ten math input ViewModel tests passed. They checked mismatched inputs, mislabelled and omitted steps, unsupported and domain errors, model assertions, explicit invocation, history exclusion and stale-result clearing. The full Gradle checks, lint, debug, release and instrumentation APK builds passed.

All 16 Android instrumentation tests passed on the Android 10 emulator with airplane mode enabled and Wi-Fi and cellular data disabled. The tests checked that the valid linear result displays Verified and adversarial model text and rejected proof do not. The same negative label test passed separately on the connected Samsung tablet. Its 22 saved strokes were unchanged byte for byte after the app upgrade.

After requiring replay for rejected reports and grouping the unverified explanation for accessibility, 21 focused unit tests passed. Eight instrumentation tests passed on the offline Android 10 emulator. The tests distinguish a genuine engine rejection from a forged diagnostic and check that the accessibility tree exposes the unverified status together with the adversarial claim. Full Gradle, lint and APK build checks passed. The accessibility regression also passed on the Samsung SM-X806B, with matching checksums for all 22 existing saved stroke files.

On 2026-11-26, 22 focused solver and review unit tests passed. The Android 10 editor and notebook tests verified the normal checked result and the rechecked ink history entry. A separate offline presentation test confirmed that a rejected proof and an adversarial model claim cannot get the Verified badge.
