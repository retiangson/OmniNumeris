# ADR 0020 · Verified math insertion

**Status** Partial implementation  
**Ticket** OW-0704 / [KAN-37](https://omninumeris.atlassian.net/browse/KAN-37)  
**Traceability** FR-MAT-04

## Decision

The application use case accepts a deterministic proof tied to the current reviewed expression. The proposed write includes the result event ID, destination revision and insertion position, exact answer and any source anchor. The host must retain the ID when retrying an event and issue a new one when the confirmed result changes. Replacing the result, switching modes or changing the target invalidates a pending preview.

Manual mode waits for an insertion request. Manual and suggestion previews require approval. Automatic mode can dispatch a verified result after a user grants permission for the specific destination. If that grant is absent, the result can be previewed for manual approval. An automatic write carries the grant to the note adapter for validation at commit. Model explanations and other unverified outcomes are ineligible in every mode. [Math and offline AI architecture](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382) · [Engineering standards](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/622603)

The note adapter must atomically reauthorize the destination and source, verify the placement and revision, and store the insertion, audit record and undo information under the operation ID. An authorized duplicate returns the prior receipt. Conflicting reuse and replay after undo are rejected. Undo reauthorizes and reverses the inserted content while preserving unrelated edits.

## Remaining integration

The math editor now has an ordinary notebook page target for manual, suggestion and automatic insertion of verified typed results. The Room adapter checks page ownership, position, revision, editability and any automatic grant. It stores the expression, exact answer, grant when used, and undo state with the operation. A page starts in Manual. Choosing Automatic creates a grant for that page. Switching modes or making the page read-only revokes it.

Room also authorizes persisted ordinary notebook ink within the same notebook. A source anchor records the ink revision and selected stroke IDs. Ink changes invalidate pending anchors, while result insertion and undo leave the ink revision intact. The notebook lasso opens the selected strokes for manual transcription and deterministic solving. Camera, book and PDF authorization, automatic handwriting recognition, and the protected storage and lock path remain open. [Notebook KAN-64](https://omninumeris.atlassian.net/browse/KAN-64)

## Verification

The application tests exercise manual request and suggestion approval, stale proposals, automatic grant scope and revocation, unverified or mismatched math, typed write denial, replay and undo. The test adapter runs in memory. Production transaction durability and device behavior remain unverified.

On 2026-11-25, all three insertion tests passed. The offline Gradle `check` gate, lint and debug, release and instrumentation APK builds passed.

On 2026-11-26, both notebook workflow tests passed on an offline API 29 emulator. The verified workflow exercised preview cancellation, approved insertion, reopening, undo and read-only rejection. A synthetic Room v1 upgrade preserved the calculator row and recorded exactly one verified operation, subsequently undone. See the [implementation register](../IMPLEMENTATION-REGISTER.md) for the current scope.

The subsequent ordinary notebook mode gate passed offline on API 29 with four instrumentation tests. The Room v3 migration preserved the legacy calculator result and recorded exactly one automatic operation in the UI mode fixture. Replay, revocation and rejection of unregistered media sources were checked against the Room adapter. The shared Gradle test, lint and APK build gate passed.
