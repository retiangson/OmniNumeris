# ADR 0017 · Deterministic math explanations

**Status** Ready for review  
**Ticket** OW-0706 / [KAN-39](https://omninumeris.atlassian.net/browse/KAN-39)  
**Traceability** FR-MAT-06

## Decision

Run the exact solver from a confirmed correction draft. The solver consumes the confirmed AST. A hint returns rule guidance without a numeric answer. A solve reports ordered operations and exact results for supported scalar arithmetic, or balancing steps followed by substitution in the original equation for supported linear equations. Identities and contradictions have their own outcomes.

The work runs on the computation dispatcher. Editing, changing candidates or dismissing review invalidates the current solution. A completed computation can publish only to the confirmed draft that launched it.

The supported classes follow the KAN-30 matrix. Its parser requires explicit multiplication, so the ticket's linear example is entered as `2*x+3=7`. The solver checks the original domain and never proposes roots for nonlinear radical equations or equations with variable denominators. Those classes have an explicit unavailable state. Other unsupported classes continue to use the parser or solver's existing typed errors. [Supported-class matrix](../benchmarks/math-recognition-and-solving.md)

A verified linear equation with a unique solution can also use the zero of the original left-minus-right difference. The alternative evaluates this affine function at zero and one with exact rationals, derives the slope, and checks that the proposed root agrees with balancing and satisfies the original difference. Verification replays both methods. The user can switch between the explanations, and other outcomes retain the unavailable label. If the additional calculation exceeds an arithmetic bound, the verified primary explanation remains available.

## Remaining acceptance

Users can explicitly save a successful standalone typed solution. The encrypted record holds the original input, confirmed input, expected outcome and solver version. Reopening runs the current matching-version solver again and checks the result before showing steps. Opening and closing an archived review preserves the current editor draft. Saved typed solutions use a file and Keystore scope distinct from calculator history.

An active media source can supply a history adapter restricted to its exact anchor, including its revision and selected region. The review saves verified results there and filters returned records against the same anchor before display. Reopening checks the answer again and preserves the active source preview and anchor. A lock, revision change or loss of access clears the linked history and solution from presentation. The standalone store continues to hold source-free typed results only.

Ordinary notebook ink now supplies a Room history adapter for the exact saved selection and its ink revision. On reopening, the adapter rechecks active page, notebook ownership and stroke IDs. The editor rechecks the saved answer before displaying verified steps. Camera, book and PDF history require their production source owners. Protected notebook storage remains covered by its security review. [Unified inputs](https://omninumeris.atlassian.net/browse/KAN-34) · [Expression correction](https://omninumeris.atlassian.net/browse/KAN-38)

## Verification

On 2026-11-25, four explanation tests checked ordered linear transformations, substitution, fractions, percentage arithmetic, roots, powers, domain errors and unsupported equations. Eight math input ViewModel tests passed, including a completed solution suppressed after an edit. The Gradle checks, lint, debug, release and instrumentation builds passed.

All 13 UI tests passed on the Android 10 emulator in airplane mode with Wi-Fi and cellular data disabled. The solver test confirmed that the hint withheld `x = 2`, then checked the explicit solve action, balancing rule, substitution showing `7 = 7`, and unavailable alternative-method state. The Samsung tablet was disconnected.

The typed history follow-up passed the full Gradle checks and 15 offline Android tests. These covered reopening a corrected solution without changing the editor's current draft, rejecting a mismatched recorded answer, and refusing calculator ciphertext copied into the typed solution store without overwriting it. A separate Android run saved `864/1337-1/1337`, force-stopped the app, launched a new process, and reopened the rechecked exact result `863/1337`.

For the alternative method, six explanation tests and four proof-verification tests passed. The tests cover slope signs, a fractional root, a tampered slope, and an alternate sample exceeding the numeric budget while the primary proof remains valid. Full Gradle checks, lint and APK builds passed. Seven Android 10 instrumentation tests passed offline, including switching methods for `2*x+3=7`, reopening both methods from encrypted history, and showing the unavailable label for scalar arithmetic. The physical tablet was locked during this run.

For linked history, 16 math ViewModel tests and the full Gradle check, lint and APK builds passed. Eight offline Android 10 instrumentation tests passed. The linked-history dialog test also passed on the Samsung SM-X806B, with matching checksums for all 22 existing canvas stroke files. The tests use an in-memory source adapter to verify reopening within the same anchor, rejecting records from another page or revision, and clearing the history and source preview after revocation.

On 2026-11-26, the ordinary notebook workflow saved and reopened a verified transcript against the same ink after closing, deleting and restoring the page. The Room policy test verified persistence, newest-first retention and rejection of cross-notebook and obsolete revisions. Sixteen Android 10 notebook and editor tests passed offline, with the tablet-only benchmark skipped. All 22 focused solver and math-review unit tests passed, including rejection of a proposed root from a different equation. The shared Gradle checks, lint and APK builds passed.
