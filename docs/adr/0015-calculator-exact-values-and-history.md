# ADR 0015 · Calculator precision, memory and history

**Status** Partial implementation  
**Ticket** OW-0803 / [KAN-43](https://omninumeris.atlassian.net/browse/KAN-43)  
**Traceability** FR-CAL-03

## Decision

Keep exact scalar results as normalized rational numbers. The calculator defaults to fraction display and can display decimals with 4, 8, 12 or 15 significant digits using round-half-even. Rounded fractions use the approximation symbol when they differ from the exact value. Changing the display leaves the original result unchanged. Copying an exact result uses its canonical fraction even while the screen shows a rounded decimal. Clipboard writes require an explicit action and mark the clip as sensitive. [Android clipboard guidance](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling)

Memory stores exact scalar results and supports addition, subtraction, recall in Standard mode and clear. Recall restores the exact value as the displayed result. It also fills the expression field when the canonical fraction fits the existing parser's numeric literal limit. Memory and display preferences live for the calculator session.

History records the newest 32 successful standalone numeric or matrix calculations, keeping their input, mode and available option names, canonical output and result kind. Each input and output is limited to 8,192 UTF-16 code units. Rejected input and plot definitions do not become history records. A write failure leaves the live calculation available and reports that history was unavailable.

History is stored under the app's private no-backup directory. Each commit uses a fresh AES-256-GCM data key wrapped by a dedicated Android Keystore key. Authenticated associated data binds the record to the standalone calculator history format. Writes are serialized through a repository and commit through Android's AtomicFile with encrypted read-back verification. Decode and authentication failures preserve the existing bytes. No plaintext staging file is created. The app clears the decrypted history view when the dialog closes. The notebook vault prototype continues to use the shared encryption primitive and its original wrapping key scope. [Protected storage design](0007-protected-storage-and-portable-recovery.md) · [Android AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)

Calculator note insertion shares the canonical value used by history and clipboard. The preview retains result kind and programmer radix, and writes the canonical fraction for an exact result regardless of display precision. It rejects graphs, calculation errors and oversized values. Preview binds to the active result event, note revision and insertion position. Approval rechecks those fields and sends the proposal through the same authorized write and undo contract used for verified math. [Verified math insertion contract](0020-verified-math-insertion.md)

## Remaining integration

The production notebook still needs to supply the active target and implement the authorized transaction, audit and durable undo. The calculator currently exposes no note-insertion control. Calculator history and clipboard currently serve the standalone calculator. Protected notebook integration needs explicit scope and lock handling before it can use those features. [KAN-64](https://omninumeris.atlassian.net/browse/KAN-64) · [OMNI security design](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/589826)

## Verification

On 2026-11-25, the calculator ViewModel and history format tests passed. They verify exact simplification, precision changes, memory arithmetic and recall, retention of canonical fractions, and format bounds. Full Gradle checks, lint, release, debug and instrumentation APK builds passed.

Seventeen instrumentation tests passed on Android 10 in airplane mode with Wi-Fi and cellular data disabled. They verified the Tools menu, sensitive clipboard data containing the exact fraction `1/3`, memory recall, encrypted history reload and tamper rejection, and five existing protected vault regressions. A separate device run calculated `67/89+1/89`, stopped the app process, verified a different process after launch, and reopened the original expression and exact result `68/89` from history. The physical tablet was disconnected during these checks.

After sharing canonical formatting with insertion previews, all three calculator insertion tests and the full Gradle checks, lint and APK builds passed. The insertion tests cover canonical fractions, approximation and radix metadata, stale proposals, and lock and undo responses using an in-memory adapter. Twelve calculator and history instrumentation tests passed offline on the Android 10 emulator. A separate run saved `561/907+1/907`, stopped the app and reopened `562/907` from encrypted history in a fresh process.
