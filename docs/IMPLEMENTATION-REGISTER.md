# OmniNumeris implementation register

Jira snapshot taken on 25 November 2026 in Pacific/Auckland. All 150 KAN project issues are indexed below. 129 To Do, 11 In Review, 9 In Progress, 1 On Hold. Refresh Jira before using a status or transition. [KAN board](https://omninumeris.atlassian.net/jira/software/projects/KAN/boards/1)

**Working objective** Deliver OmniNumeris in dependency-ordered end-to-end batches. Complete the notebook and calculator insertion workflow first, then integrate verified math insertion. Verify each Jira ticket against the integrated build and record its evidence before moving it to In Review.

## How to work from this file

1. Pick one end-to-end workflow. Read its Jira issues, direct blockers and referenced contracts. Use this index to locate related work without loading the full backlog into each implementation turn.
2. Build the related production paths together. Use quick targeted checks while developing. Add focused tests early for data loss, access control, undo and math verification.
3. When the workflow runs end to end, run the shared build and lint gate once for the batch and the relevant offline/device checks. Record evidence by ticket and criterion. Move a ticket to In Review only when its own scope is verified and the transition is authorized.
4. Keep incomplete criteria open. Preserve the original requirements and resolve conflicts against the Confluence baseline and approved ADRs.

## Requirements by area

- Offline restricted Android workspace and release scope. Read [product vision](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/327700), [requirements](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655361) and the [local SRS](../01-SRS.md).
- Notebook, reader, calculator and adaptive phone/tablet UI. Read the [architecture](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/688129), [UI design](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/458754) and [local architecture](../02-Architecture.md).
- Storage, lock boundaries, backup, revision anchors and recovery. Read the [security design](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/589826), [logical schema](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/622636) and [local security requirements](../03-Data-Security-UX.md). Room does not provide encryption. Protected identifiers, metadata and payloads need their own authorized storage boundary.
- Math and optional local inference. Read the [math pipeline](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382) and [supported solver and OCR classes](benchmarks/math-recognition-and-solving.md). Deterministic calculation and verification run offline without a model. Recognition remains editable and AI prose remains unverified.
- Delivery sequence and release evidence. Read the [milestones](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/327722), [test plan](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/688150), [local test roadmap](../04-Testing-Roadmap.md), [ticket requirements](../05-Tickets.md) and [engineering rules](../AGENTS.md). The Jira rows below link to the full current criteria.

## First implementation batch

Build the notebook → calculate → insert → reopen → undo workflow. Start with production notebook metadata, fixed and infinite page geometry, and an app route. Add the authorized revision-aware write boundary and attach the existing calculator and verified-math use cases. Production storage, locks and undo need their own evidence; the existing debug ink journal and in-memory insertion adapters are prior evidence for their narrower scope.

| Ticket | Requirements and evidence to collect |
| --- | --- |
| [KAN-29](https://omninumeris.atlassian.net/browse/KAN-29) | Review the existing canvas prototype and physical ink evidence. It is the linked technical prerequisite for KAN-64 and was In Review in this snapshot. |
| [KAN-32](https://omninumeris.atlassian.net/browse/KAN-32) | Follow the secure storage design and review requirements for protected scopes. The prototype and design are In Review. |
| [KAN-64](https://omninumeris.atlassian.net/browse/KAN-64) | Create and reopen fixed and infinite notebooks offline. Preserve object coordinates under pan and zoom, enforce fixed bounds, and use viewport-scoped rendering for the infinite canvas. |
| [KAN-65](https://omninumeris.atlassian.net/browse/KAN-65), [KAN-68](https://omninumeris.atlassian.net/browse/KAN-68), [KAN-70](https://omninumeris.atlassian.net/browse/KAN-70), [KAN-71](https://omninumeris.atlassian.net/browse/KAN-71) | Verify independently what ships for placed objects, layer lock, undo/redo, and crash-safe save. One exact-result insertion does not close the broader notebook stories. |
| [KAN-37](https://omninumeris.atlassian.net/browse/KAN-37) | Verify manual action, suggestion approval, eligible verified auto-insert, source and target authorization, rejection of locked or read-only targets, replay and undo. |
| [KAN-43](https://omninumeris.atlassian.net/browse/KAN-43) | Verify exact arithmetic and insertion, precision, memory, history after restart, clipboard, locked target rejection and undo. |
| [KAN-41](https://omninumeris.atlassian.net/browse/KAN-41) | Verify the shared floating calculator in the production notebook and reader. Confirm safe movement, resize, rotation and collapse. Reader coverage remains a separate dependency. |

Prior evidence is in the [ADRs](adr) and [benchmarks](benchmarks). KAN-82 remains deferred, and KAN-28 still requires the specified physical 3 GB ARM64 device. The next integration batch connects approved OCR and production source anchors to the math review and verification path.

### First workflow evidence, 26 November 2026

The production Notebooks route creates and opens fixed or infinite ordinary notebooks. It uses a separate app-private Room database with an exported [version 1 schema](../infrastructure/schemas/com.omninumeris.infrastructure.storage.NotebookDatabase/1.json). Fixed pages can be added and navigated. Rendering reads results within the viewport and allocates no canvas-sized bitmap. This store serves ordinary notebooks. Protected notebooks require their own reviewed storage and authorization path.

- **KAN-64** The offline Android 10 workflow created both notebook kinds, reopened a result from Room, added a fixed page and navigated back to the original. Two [viewport unit tests](../domain/src/test/kotlin/com/omninumeris/domain/NotebookViewportTest.kt) verified zoom focus and coordinate bounds. Production ink, physical performance and broader canvas acceptance remain open.
- **KAN-43** The [Android workflow test](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookWorkflowTest.kt) used the shared calculator to insert `1/2` from `1/3+1/6`, reopened the notebook, undid the insertion and rejected a second attempt on a read-only page. Direct inspection of the synthetic Room fixture verified that it held two pages, one read-only page and exactly one recorded result, the undone `1/2`. This covers ordinary read-only authorization. Protected lock and process-restart verification of the notebook insertion remain open.
- **KAN-41** The same workflow opened the floating calculator from a real notebook. The production reader entry point remains open.
- **KAN-37** The shared revision-aware target is available. Production verified-math insertion, source authorization and automatic-mode preferences remain open.

The recorded shared gate passed with `gradlew.bat --offline :check :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`. The workflow test passed with airplane mode enabled and Wi-Fi and mobile data disabled. The test used emulator-only notebook fixtures.

### Manual verified notebook insertion evidence, 26 November 2026

- **KAN-37** An ordinary notebook now opens the math editor with a page target. A verified typed solution requires a manual insertion request and a separate preview approval. Room stores the confirmed expression and exact answer together, checks the current target revision and read-only flag in the write transaction, and records undo under the result ID. The ordinary store rejects media anchors until their source owner can authorize the write. Suggestion, automatic grants, authorized media anchors and protected notebook transactions remain open. Jira still shows In Progress.
- All three application insertion tests passed. The debug and instrumentation APKs built. Both [notebook workflow tests](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookWorkflowTest.kt) passed on an API 29 emulator with airplane mode enabled and no IPv4 or IPv6 default route. The verified workflow covered cancelling a preview, approving `2*x+3=7 → x = 2`, reopening, undo and read-only denial.
- A synthetic version 1 calculator row survived migration to [Room schema version 2](../infrastructure/schemas/com.omninumeris.infrastructure.storage.NotebookDatabase/2.json) unchanged. SQLite integrity and foreign-key checks passed. The resulting database contained exactly one verified insertion, marked undone, with the confirmed expression and result. This step ran only on synthetic emulator data.

### Notebook insertion modes evidence, 26 November 2026

- **KAN-37** Ordinary notebook pages now persist Manual, Suggestion or Automatic. Suggestion waits for preview approval. Automatic requires specific opt-in for the page, acts only after an explicit solve produces a verified result, and checks its current grant in the Room write transaction. A confirmed expression keeps one operation ID across repeated solves. New pages default to Manual. Read-only revokes the grant and returns the page to Suggestion after edit access is restored.
- Four offline API 29 [workflow](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookWorkflowTest.kt) and [policy](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookInsertionPolicyTest.kt) tests passed. They exercise mode changes and reopening, exact insertion and undo, replay, rejection of unreadable media sources, stale and revoked grants, and read-only writes and undo. The shared offline Gradle check, lint, debug, release and instrumentation APK builds passed.
- The [Room v3](../infrastructure/schemas/com.omninumeris.infrastructure.storage.NotebookDatabase/3.json) migration preserved the synthetic legacy calculator row and passed SQLite integrity and foreign-key checks. The two-page mode fixture retained exactly one automatic operation with a recorded grant, marked undone. Both pages ended with no active grant.
- **Review remaining** Production anchored media insertion needs a source owner that can reauthorize the anchor at commit. Protected locks need the production security boundary and approval described in [ADR 0007](adr/0007-protected-storage-and-portable-recovery.md). KAN-37 remains In Progress.

### Ordinary notebook ink source evidence, 26 November 2026

- **KAN-36, KAN-37, KAN-64** The release Room adapter now stores pressure-sensitive notebook ink and returns viewport-scoped, revisioned snapshots. The lasso use case produces an anchor from persisted strokes. On insertion, Room checks source scope, ownership of each selected stroke and the live ink revision before writing the exact result and source reference in one transaction. Result undo preserves the cited strokes. Canvas capture, lasso UI and the anchored math review entry point remain open.
- Five notebook workflow and policy tests passed offline on API 29. They include persisted ink reopening, lasso hits and an empty selection, same-notebook insertion and replay, cross-notebook and stale-source rejection, and undo preserving original ink. Debug, release and instrumentation APK builds passed. The shared Gradle check and lint passed after APK generation completed to avoid a lint race on generated Hilt sources.
- Synthetic [Room v4](../infrastructure/schemas/com.omninumeris.infrastructure.storage.NotebookDatabase/4.json) data passed integrity and foreign-key checks. The upgrade retained the legacy calculator row. The ink fixture retained its two strokes and one undone result with the expected source page, ink revision and stroke ID.

### Notebook ink workflow evidence, 26 November 2026

- **KAN-36, KAN-37, KAN-64** The ordinary notebook screen now captures ink, lets the user pan and zoom or select ink with a lasso, and opens an editable transcription with a preview of the selected strokes. The deterministic solver can insert the checked expression with its live ink anchor. Handwriting recognition remains experimental. This workflow uses user transcription.
- The [Android workflow test](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookWorkflowTest.kt) drew a synthetic stroke, reported an empty lasso, selected the stroke, transcribed `8+4`, approved the verified result `12`, and undid it. Reopening the notebook preserved the original stroke for selection. The test passed on the offline API 29 emulator. Physical stylus behavior and device performance remain unverified for this screen.
- All six offline notebook instrumentation tests passed. Gradle checks, lint and debug, release and instrumentation APK builds passed. SQLite inspection of the synthetic UI fixture confirmed one original stroke and one undone result referencing that exact stroke at the unchanged ink revision.
- Protected notebook storage, lock enforcement and camera, book and PDF source authorization remain open. Jira review still requires each ticket's remaining acceptance and dependency evidence.

### Protected notebook write authorization evidence, 26 November 2026

- **KAN-32, KAN-37** The debug vault can now bind a pending write to one continuous notebook unlock session. The write checks the lease and revision atomically. Locking invalidates the old lease even if the user reauthenticates. The API 29 offline vault suite exercised six cases, including stale undo rejection and separate notebook scopes. The dedicated fresh-user restore and forced process death fixtures were skipped in this run. Production integration and security review remain open. [Protected storage ADR](adr/0007-protected-storage-and-portable-recovery.md)

### Protected math and calculator prototype evidence, 26 November 2026

- **KAN-32, KAN-37, KAN-43** The debug vault now runs the actual verified-math and calculator use cases. It commits preview approval, result, audit identity and undo state within one encrypted write. Locking or relocking denies stale previews and writes. Undo requires a live lease and preserves the original document. The prototype accepts approved typed results and calculator values. Protected media and automatic placement remain open. [Protected storage ADR](adr/0007-protected-storage-and-portable-recovery.md)
- The combined offline API 29 notebook and vault suite passed 14 cases and skipped two provisioned fixtures for fresh-user restore and forced process death. The new tests checked verified math replay, exact calculator results, stale approval after unlock, read-only denial, lock denial for write and undo, and preservation of original content. Gradle checks and the APK builds passed. APK inspection confirmed the protected adapter is debug-only.

### Keystore device-credential evidence, 26 November 2026

- **KAN-32** The credential-bound wrapping policy passed on synthetic API 29 and API 35 emulator fixtures. Before authentication, Android denied wrapping. After confirmation in system Settings, the wrapped key round-tripped. After the test authorization window expired, Android denied unwrap. Each synthetic PIN and test key alias was removed. The shared Gradle check, lint and APK builds passed. The API 29 notebook and vault regression suite passed 14 cases, with two separately provisioned cases skipped. The probe verifies the Keystore policy independently of production notebook routing. [Protected storage ADR](adr/0007-protected-storage-and-portable-recovery.md)
- **KAN-32, KAN-37, KAN-43** The credential-bound debug vault passed the integrated workflow on API 29 and API 35. It denied creation before authentication, accepted exact calculator insertion after authentication, denied undo and unlock after expiry, rejected a stale preview after reauthentication, and allowed a newly authorized undo that preserved the original content. API 29 used system credential confirmation. API 35 used Android's test-emulator credential verification after its repeated secure windows proved unreliable for scripted input. The synthetic PINs, database and key aliases were removed. The shared Gradle checks and APK builds passed. The regular API 29 suite passed 14 and skipped two. APK inspection confirmed the credential vault remains debug-only.

## Jira inventory

The snapshot rows below record the original inventory. On 26 November 2026, [KAN-38](https://omninumeris.atlassian.net/browse/KAN-38) moved to In Review after all 17 math-input unit tests and eight offline API 29 editor and verification cases passed. The [correction ADR](adr/0016-math-expression-correction.md) records the recognized exponent fixture, malformed input retention, verification invalidation and source preservation. KAN-40 still depends on KAN-39's source and history workflow.

**KAN-64 page ordering** Fixed notebooks can move the current page one place earlier or later through Manage pages, and undo the last move while the notebook is open. Room commits the swap in one transaction. Page content, IDs and revisions stay attached to their original pages. After a result write or undo, the app refreshes the page by ID. Seven offline Android 10 notebook tests passed, including reordering three pages, reopening by page ID, matching the original ink anchor, rejecting stale undo and using the controls in the app. The shared Gradle checks and three APK builds passed. Page thumbnails, duplication, confirmed deletion and physical-device performance evidence remain open.

**KAN-64 page insertion and duplication** Manage pages can insert a blank page immediately after the current page or duplicate the current page there. In one transaction Room updates positions, copies active results and ink, and makes the copy editable with new object IDs and Manual insertion policy. A copied result keeps its source reference to the original evidence. Undoing a result on either page affects only that page. Eight Android 10 notebook tests passed offline. SQLite inspection of a synthetic fixture confirmed independent result identities, original ink provenance, no copied grant and an intact original result after undoing the duplicate. Gradle checks, lint and the debug, release and test APK builds passed. Page thumbnails, confirmed deletion and physical-device performance remain open.

**KAN-64 confirmed page deletion** Manage pages confirms deletion before hiding a page from a fixed notebook. Restore last deleted page works after reopening and retains the original page, result and ink IDs. A deleted page is unavailable for reads, writes and source authorization. Restore creates a new write revision and never restores an automatic insertion grant. The final active page and read-only pages cannot be deleted. Nine offline Android 10 notebook tests passed, including confirmation cancellation and restoration of the first page in a four-page notebook. The existing emulator database migrated in place, and Gradle checks, lint and all three APK builds passed. Page thumbnails and physical-device performance remain open. [Room v5 schema](../infrastructure/schemas/com.omninumeris.infrastructure.storage.NotebookDatabase/5.json)

**KAN-64 page thumbnails** Manage pages shows a lazy strip of fixed-page previews with sampled ink paths and result markers. Each preview is loaded for an active page and navigates by page ID. Room bounds each preview to 12 strokes with at most 48 sampled points per stroke and the fixed page's result slots. All nine offline Android 10 notebook tests passed, including thumbnail navigation, preview identity after reorder and omission of a deleted page. Gradle checks, lint and all three APK builds passed. Physical-device performance remains open. The connected Samsung SM-X806B runs API 36 and reports approximately 7.1 GiB of total RAM.

**KAN-64 physical notebook storage benchmark** The isolated fixture package ran on Samsung SM-X806B, API 36. It wrote 1,024 synthetic strokes of 16 samples each to an infinite notebook. Across 48 warm lookups, opening the page by ID and querying ink for a 1,000 × 760 source-unit viewport took 14.80 ms at p50 and 32.24 ms at p95. This measures storage and decoding. The tablet lock screen blocked foreground frame measurement. The fixture package and its data were removed after the run. The opt-in build flag `-PnotebookDeviceFixture=true` writes its APKs to `app/build/notebook-device-fixture/outputs/apk`. [Instrumentation workload](../app/src/androidTest/kotlin/com/omninumeris/app/NotebookWorkflowTest.kt)

**KAN-37 and KAN-39 notebook result source** A saved ink-backed result now opens a preview of its source strokes and can navigate to the current position of the original page. The result must still be active and the source must belong to the same notebook and match the saved ink revision. A modified, deleted, or unavailable source produces an explicit message. Nine Android 10 tests passed offline, including opening a link in the app and rejecting cross-notebook, stale and undone references. The suite skipped the tablet-only benchmark. Gradle checks, lint and APK builds passed. Source-scoped history outside saved notebook results remains open.

The blocker column records explicit inward Jira links. Ticket descriptions also identify coordination dependencies, including some with no formal link.

| Ticket | Status | Type | Parent | Explicit blockers | Summary |
| --- | --- | --- | --- | --- | --- |
| [KAN-1](https://omninumeris.atlassian.net/browse/KAN-1) | To Do | Task | — | — | OmniNumeris — Product vision, scope and release baseline |
| [KAN-2](https://omninumeris.atlassian.net/browse/KAN-2) | To Do | Epic | — | — | Intelligent mathematical solver |
| [KAN-3](https://omninumeris.atlassian.net/browse/KAN-3) | To Do | Epic | — | — | Advanced calculator |
| [KAN-4](https://omninumeris.atlassian.net/browse/KAN-4) | To Do | Epic | — | — | Graphing engine |
| [KAN-5](https://omninumeris.atlassian.net/browse/KAN-5) | To Do | Epic | — | — | Workspace library |
| [KAN-6](https://omninumeris.atlassian.net/browse/KAN-6) | To Do | Epic | — | — | Digital notebooks |
| [KAN-7](https://omninumeris.atlassian.net/browse/KAN-7) | To Do | Epic | — | — | Professional drawing |
| [KAN-8](https://omninumeris.atlassian.net/browse/KAN-8) | To Do | Epic | — | — | Stylus and precision input |
| [KAN-9](https://omninumeris.atlassian.net/browse/KAN-9) | To Do | Epic | — | — | Handwriting recognition |
| [KAN-10](https://omninumeris.atlassian.net/browse/KAN-10) | To Do | Epic | — | — | Document scanner |
| [KAN-11](https://omninumeris.atlassian.net/browse/KAN-11) | To Do | Epic | — | — | PDF management |
| [KAN-12](https://omninumeris.atlassian.net/browse/KAN-12) | To Do | Epic | — | — | Document import and export |
| [KAN-13](https://omninumeris.atlassian.net/browse/KAN-13) | To Do | Epic | — | — | Interactive book reader |
| [KAN-14](https://omninumeris.atlassian.net/browse/KAN-14) | To Do | Epic | — | — | Offline read aloud |
| [KAN-15](https://omninumeris.atlassian.net/browse/KAN-15) | To Do | Epic | — | — | Study and learning |
| [KAN-16](https://omninumeris.atlassian.net/browse/KAN-16) | To Do | Epic | — | — | Offline AI assistant |
| [KAN-17](https://omninumeris.atlassian.net/browse/KAN-17) | To Do | Epic | — | — | Local AI vision |
| [KAN-18](https://omninumeris.atlassian.net/browse/KAN-18) | To Do | Epic | — | — | Local knowledge retrieval |
| [KAN-19](https://omninumeris.atlassian.net/browse/KAN-19) | To Do | Epic | — | — | AI automation |
| [KAN-20](https://omninumeris.atlassian.net/browse/KAN-20) | To Do | Epic | — | — | AI model manager |
| [KAN-21](https://omninumeris.atlassian.net/browse/KAN-21) | To Do | Epic | — | — | Audio and transcription |
| [KAN-22](https://omninumeris.atlassian.net/browse/KAN-22) | To Do | Epic | — | — | Technical diagrams |
| [KAN-23](https://omninumeris.atlassian.net/browse/KAN-23) | To Do | Epic | — | — | Presentation mode |
| [KAN-24](https://omninumeris.atlassian.net/browse/KAN-24) | To Do | Epic | — | — | Security and backup |
| [KAN-25](https://omninumeris.atlassian.net/browse/KAN-25) | To Do | Epic | — | — | Customization and accessibility |
| [KAN-26](https://omninumeris.atlassian.net/browse/KAN-26) | To Do | Epic | — | — | Commercial release and billing |
| [KAN-27](https://omninumeris.atlassian.net/browse/KAN-27) | In Review | Task | — | — | OW-001 — Bootstrap Android project and CI |
| [KAN-28](https://omninumeris.atlassian.net/browse/KAN-28) | In Progress | Task | — | KAN-27 | OW-002 — Benchmark 3 GB local inference |
| [KAN-29](https://omninumeris.atlassian.net/browse/KAN-29) | In Review | Task | — | KAN-27 | OW-003 — Prototype pressure-sensitive tiled canvas |
| [KAN-30](https://omninumeris.atlassian.net/browse/KAN-30) | In Review | Task | — | KAN-27 | OW-004 — Evaluate math OCR and CAS |
| [KAN-31](https://omninumeris.atlassian.net/browse/KAN-31) | In Review | Task | — | KAN-27 | OW-005 — Evaluate PDF and EPUB SDKs |
| [KAN-32](https://omninumeris.atlassian.net/browse/KAN-32) | In Review | Task | — | KAN-27 | OW-006 — Design encrypted storage and backup |
| [KAN-33](https://omninumeris.atlassian.net/browse/KAN-33) | In Review | Task | — | KAN-27 | OW-007 — Define product entitlements and model distribution |
| [KAN-34](https://omninumeris.atlassian.net/browse/KAN-34) | In Progress | Story | KAN-2 | KAN-30 | OW-0701 — Typed, handwritten, camera, book and PDF math inputs |
| [KAN-35](https://omninumeris.atlassian.net/browse/KAN-35) | In Review | Story | KAN-2 | — | OW-0702 — Math keyboard and LaTeX |
| [KAN-36](https://omninumeris.atlassian.net/browse/KAN-36) | In Progress | Story | KAN-2 | KAN-34 | OW-0703 — Circle/lasso recognition |
| [KAN-37](https://omninumeris.atlassian.net/browse/KAN-37) | In Progress | Story | KAN-2 | — | OW-0704 — Manual, suggestion and automatic insertion modes |
| [KAN-38](https://omninumeris.atlassian.net/browse/KAN-38) | In Progress | Story | KAN-2 | KAN-35 | OW-0705 — Expression correction |
| [KAN-39](https://omninumeris.atlassian.net/browse/KAN-39) | In Progress | Story | KAN-2 | KAN-30, KAN-38 | OW-0706 — Step generation, validation, hints, alternative methods, history and source links |
| [KAN-40](https://omninumeris.atlassian.net/browse/KAN-40) | In Progress | Story | KAN-2 | KAN-39 | OW-0707 — Verified versus unverified results |
| [KAN-41](https://omninumeris.atlassian.net/browse/KAN-41) | In Progress | Story | KAN-3 | — | OW-0801 — Floating movable resizable calculator throughout app |
| [KAN-42](https://omninumeris.atlassian.net/browse/KAN-42) | In Review | Story | KAN-3 | — | OW-0802 — Standard, scientific, programmer, financial, statistics, matrices, graphing and unit conversions |
| [KAN-43](https://omninumeris.atlassian.net/browse/KAN-43) | In Progress | Story | KAN-3 | — | OW-0803 — Exact fractions, precision, memory, history, clipboard and insertion into active notes |
| [KAN-44](https://omninumeris.atlassian.net/browse/KAN-44) | In Review | Story | KAN-4 | KAN-42 | OW-0901 — 2D multi-function plots |
| [KAN-45](https://omninumeris.atlassian.net/browse/KAN-45) | In Review | Story | KAN-4 | — | OW-0902 — Pan/zoom |
| [KAN-46](https://omninumeris.atlassian.net/browse/KAN-46) | In Review | Story | KAN-4 | — | OW-0903 — Roots and intersections |
| [KAN-47](https://omninumeris.atlassian.net/browse/KAN-47) | To Do | Story | KAN-4 | — | OW-0904 — Parameters |
| [KAN-48](https://omninumeris.atlassian.net/browse/KAN-48) | To Do | Story | KAN-4 | — | OW-0905 — Scatter and statistics |
| [KAN-49](https://omninumeris.atlassian.net/browse/KAN-49) | To Do | Story | KAN-4 | — | OW-0906 — Polar and parametric plots |
| [KAN-50](https://omninumeris.atlassian.net/browse/KAN-50) | To Do | Story | KAN-4 | — | OW-0907 — Adaptive 3D |
| [KAN-51](https://omninumeris.atlassian.net/browse/KAN-51) | To Do | Story | KAN-4 | — | OW-0908 — Editable insertion and vector/image export |
| [KAN-52](https://omninumeris.atlassian.net/browse/KAN-52) | To Do | Story | KAN-5 | — | OW-0201 — Nested folders |
| [KAN-53](https://omninumeris.atlassian.net/browse/KAN-53) | To Do | Story | KAN-5 | — | OW-0202 — Tags |
| [KAN-54](https://omninumeris.atlassian.net/browse/KAN-54) | To Do | Story | KAN-5 | — | OW-0203 — Favourites |
| [KAN-55](https://omninumeris.atlassian.net/browse/KAN-55) | To Do | Story | KAN-5 | — | OW-0204 — Recent items |
| [KAN-56](https://omninumeris.atlassian.net/browse/KAN-56) | To Do | Story | KAN-5 | — | OW-0205 — Grid/list |
| [KAN-57](https://omninumeris.atlassian.net/browse/KAN-57) | To Do | Story | KAN-5 | — | OW-0206 — Sorting |
| [KAN-58](https://omninumeris.atlassian.net/browse/KAN-58) | To Do | Story | KAN-5 | — | OW-0207 — Batch operations |
| [KAN-59](https://omninumeris.atlassian.net/browse/KAN-59) | To Do | Story | KAN-5 | — | OW-0208 — Duplicate detection |
| [KAN-60](https://omninumeris.atlassian.net/browse/KAN-60) | To Do | Story | KAN-5 | — | OW-0209 — Recycle bin |
| [KAN-61](https://omninumeris.atlassian.net/browse/KAN-61) | To Do | Story | KAN-5 | — | OW-0210 — Snapshots |
| [KAN-62](https://omninumeris.atlassian.net/browse/KAN-62) | To Do | Story | KAN-5 | — | OW-0211 — Version comparison and restore |
| [KAN-63](https://omninumeris.atlassian.net/browse/KAN-63) | To Do | Story | KAN-5 | — | OW-0212 — Tabs and session restoration |
| [KAN-64](https://omninumeris.atlassian.net/browse/KAN-64) | To Do | Story | KAN-6 | KAN-29 | OW-0301 — Fixed pages and infinite canvas |
| [KAN-65](https://omninumeris.atlassian.net/browse/KAN-65) | To Do | Story | KAN-6 | KAN-64 | OW-0302 — Free-position objects and structured rich text |
| [KAN-66](https://omninumeris.atlassian.net/browse/KAN-66) | To Do | Story | KAN-6 | — | OW-0303 — Tables |
| [KAN-67](https://omninumeris.atlassian.net/browse/KAN-67) | To Do | Story | KAN-6 | — | OW-0304 — Page sizes and plain/lined/grid/dot/Cornell/music/custom templates |
| [KAN-68](https://omninumeris.atlassian.net/browse/KAN-68) | To Do | Story | KAN-6 | KAN-32 | OW-0305 — Layers |
| [KAN-69](https://omninumeris.atlassian.net/browse/KAN-69) | To Do | Story | KAN-6 | — | OW-0306 — Transforms |
| [KAN-70](https://omninumeris.atlassian.net/browse/KAN-70) | To Do | Story | KAN-6 | KAN-64 | OW-0307 — Undo/redo |
| [KAN-71](https://omninumeris.atlassian.net/browse/KAN-71) | To Do | Story | KAN-6 | KAN-64 | OW-0308 — Autosave |
| [KAN-72](https://omninumeris.atlassian.net/browse/KAN-72) | To Do | Story | KAN-6 | — | OW-0309 — Attachments |
| [KAN-73](https://omninumeris.atlassian.net/browse/KAN-73) | To Do | Story | KAN-6 | — | OW-0310 — Split screen |
| [KAN-74](https://omninumeris.atlassian.net/browse/KAN-74) | To Do | Story | KAN-6 | — | OW-0311 — Floating tools |
| [KAN-75](https://omninumeris.atlassian.net/browse/KAN-75) | To Do | Story | KAN-7 | KAN-29 | OW-0401 — Vector ink and tiled raster painting |
| [KAN-76](https://omninumeris.atlassian.net/browse/KAN-76) | To Do | Story | KAN-7 | KAN-75 | OW-0402 — Pressure and tilt |
| [KAN-77](https://omninumeris.atlassian.net/browse/KAN-77) | To Do | Story | KAN-7 | — | OW-0403 — Brush presets |
| [KAN-78](https://omninumeris.atlassian.net/browse/KAN-78) | To Do | Story | KAN-8 | — | OW-0501 — Active pen detection |
| [KAN-79](https://omninumeris.atlassian.net/browse/KAN-79) | To Do | Story | KAN-8 | KAN-78 | OW-0502 — Palm rejection |
| [KAN-80](https://omninumeris.atlassian.net/browse/KAN-80) | To Do | Story | KAN-8 | — | OW-0503 — Stylus-only mode |
| [KAN-81](https://omninumeris.atlassian.net/browse/KAN-81) | To Do | Story | KAN-8 | — | OW-0504 — Finger navigation |
| [KAN-82](https://omninumeris.atlassian.net/browse/KAN-82) | On Hold | Story | KAN-8 | — | OW-0505 — Pressure calibration |
| [KAN-83](https://omninumeris.atlassian.net/browse/KAN-83) | To Do | Story | KAN-8 | — | OW-0506 — Hover, buttons and handedness |
| [KAN-84](https://omninumeris.atlassian.net/browse/KAN-84) | To Do | Story | KAN-9 | — | OW-0601 — Offline handwriting recognition |
| [KAN-85](https://omninumeris.atlassian.net/browse/KAN-85) | To Do | Story | KAN-9 | KAN-84 | OW-0602 — Editable recognition corrections and original ink preservation |
| [KAN-86](https://omninumeris.atlassian.net/browse/KAN-86) | To Do | Story | KAN-9 | KAN-85 | OW-0603 — Handwriting beautification and searchable text |
| [KAN-87](https://omninumeris.atlassian.net/browse/KAN-87) | To Do | Story | KAN-10 | — | OW-1001 — Camera capture and automatic boundaries |
| [KAN-88](https://omninumeris.atlassian.net/browse/KAN-88) | To Do | Story | KAN-10 | KAN-87 | OW-1002 — Perspective correction and image enhancement |
| [KAN-89](https://omninumeris.atlassian.net/browse/KAN-89) | To Do | Story | KAN-10 | KAN-88 | OW-1003 — Multi-page scanning and page organization |
| [KAN-90](https://omninumeris.atlassian.net/browse/KAN-90) | To Do | Story | KAN-10 | — | OW-1004 — Receipt, business card, book and whiteboard scanning |
| [KAN-91](https://omninumeris.atlassian.net/browse/KAN-91) | To Do | Story | KAN-10 | KAN-89 | OW-1005 — Offline OCR and searchable PDF |
| [KAN-92](https://omninumeris.atlassian.net/browse/KAN-92) | To Do | Story | KAN-11 | KAN-31 | OW-1101 — PDF reading, search and thumbnails |
| [KAN-93](https://omninumeris.atlassian.net/browse/KAN-93) | To Do | Story | KAN-11 | KAN-92 | OW-1102 — Ink, highlights, comments and shapes |
| [KAN-94](https://omninumeris.atlassian.net/browse/KAN-94) | To Do | Story | KAN-11 | KAN-92 | OW-1103 — Merge, split, reorder, rotate and extract pages |
| [KAN-95](https://omninumeris.atlassian.net/browse/KAN-95) | To Do | Story | KAN-11 | — | OW-1104 — Forms and reusable secure handwritten signatures |
| [KAN-96](https://omninumeris.atlassian.net/browse/KAN-96) | To Do | Story | KAN-11 | — | OW-1105 — Supported text editing and compression |
| [KAN-97](https://omninumeris.atlassian.net/browse/KAN-97) | To Do | Story | KAN-11 | — | OW-1106 — Password protection and annotation export |
| [KAN-98](https://omninumeris.atlassian.net/browse/KAN-98) | To Do | Story | KAN-12 | KAN-31 | OW-1201 — Editable DOCX import and export |
| [KAN-99](https://omninumeris.atlassian.net/browse/KAN-99) | To Do | Story | KAN-12 | — | OW-1202 — Image and text import |
| [KAN-100](https://omninumeris.atlassian.net/browse/KAN-100) | To Do | Story | KAN-12 | — | OW-1203 — PDF and image export |
| [KAN-101](https://omninumeris.atlassian.net/browse/KAN-101) | To Do | Story | KAN-13 | KAN-31 | OW-1301 — EPUB and PDF reading with navigation and progress |
| [KAN-102](https://omninumeris.atlassian.net/browse/KAN-102) | To Do | Story | KAN-13 | KAN-101 | OW-1302 — Bookmarks, highlights and linked annotations |
| [KAN-103](https://omninumeris.atlassian.net/browse/KAN-103) | To Do | Story | KAN-13 | — | OW-1303 — Reading themes, typography and layout |
| [KAN-104](https://omninumeris.atlassian.net/browse/KAN-104) | To Do | Story | KAN-13 | KAN-40, KAN-101 | OW-1304 — Circle-to-solve equations with linked verified results |
| [KAN-105](https://omninumeris.atlassian.net/browse/KAN-105) | To Do | Story | KAN-13 | — | OW-1305 — Offline dictionary and translation |
| [KAN-106](https://omninumeris.atlassian.net/browse/KAN-106) | To Do | Story | KAN-14 | — | OW-1401 — Offline voice selection and availability |
| [KAN-107](https://omninumeris.atlassian.net/browse/KAN-107) | To Do | Story | KAN-14 | KAN-106 | OW-1402 — Read selection, page or chapter with playback controls |
| [KAN-108](https://omninumeris.atlassian.net/browse/KAN-108) | To Do | Story | KAN-14 | KAN-107 | OW-1403 — Spoken text highlighting, background playback and resume |
| [KAN-109](https://omninumeris.atlassian.net/browse/KAN-109) | To Do | Story | KAN-15 | — | OW-1501 — Manual and AI-generated flashcards |
| [KAN-110](https://omninumeris.atlassian.net/browse/KAN-110) | To Do | Story | KAN-15 | KAN-109 | OW-1502 — Spaced repetition and review history |
| [KAN-111](https://omninumeris.atlassian.net/browse/KAN-111) | To Do | Story | KAN-15 | — | OW-1503 — Quizzes and timed practice |
| [KAN-112](https://omninumeris.atlassian.net/browse/KAN-112) | To Do | Story | KAN-15 | KAN-111, KAN-40 | OW-1504 — Mistake analysis and verified math grading |
| [KAN-113](https://omninumeris.atlassian.net/browse/KAN-113) | To Do | Story | KAN-15 | — | OW-1505 — Study goals, reminders and progress dashboards |
| [KAN-114](https://omninumeris.atlassian.net/browse/KAN-114) | To Do | Story | KAN-16 | KAN-28, KAN-131, KAN-134 | OW-1601 — Offline chat with streaming and cancellation |
| [KAN-115](https://omninumeris.atlassian.net/browse/KAN-115) | To Do | Story | KAN-16 | — | OW-1602 — Writing assistance, grammar and tone |
| [KAN-116](https://omninumeris.atlassian.net/browse/KAN-116) | To Do | Story | KAN-16 | — | OW-1603 — Summarization and translation |
| [KAN-117](https://omninumeris.atlassian.net/browse/KAN-117) | To Do | Story | KAN-16 | — | OW-1604 — Math tutoring and code assistance |
| [KAN-118](https://omninumeris.atlassian.net/browse/KAN-118) | To Do | Story | KAN-16 | — | OW-1605 — Context scope, history and prompt templates |
| [KAN-119](https://omninumeris.atlassian.net/browse/KAN-119) | To Do | Story | KAN-16 | KAN-126 | OW-1606 — Insert AI output with citations and uncertainty labels |
| [KAN-120](https://omninumeris.atlassian.net/browse/KAN-120) | To Do | Story | KAN-17 | KAN-131 | OW-1701 — Local image and diagram understanding |
| [KAN-121](https://omninumeris.atlassian.net/browse/KAN-121) | To Do | Story | KAN-17 | — | OW-1702 — Adaptive image resolution and hardware compatibility |
| [KAN-122](https://omninumeris.atlassian.net/browse/KAN-122) | To Do | Story | KAN-17 | — | OW-1703 — OCR fallback and unsupported capability notices |
| [KAN-123](https://omninumeris.atlassian.net/browse/KAN-123) | To Do | Story | KAN-18 | — | OW-1801 — Local extraction and incremental indexing |
| [KAN-124](https://omninumeris.atlassian.net/browse/KAN-124) | To Do | Story | KAN-18 | KAN-123, KAN-125 | OW-1802 — Hybrid keyword and vector retrieval |
| [KAN-125](https://omninumeris.atlassian.net/browse/KAN-125) | To Do | Story | KAN-18 | KAN-32 | OW-1803 — Permission-aware context scopes |
| [KAN-126](https://omninumeris.atlassian.net/browse/KAN-126) | To Do | Story | KAN-18 | KAN-124 | OW-1804 — Exact source citations and anchors |
| [KAN-127](https://omninumeris.atlassian.net/browse/KAN-127) | To Do | Story | KAN-18 | — | OW-1805 — Knowledge graph and backlinks |
| [KAN-128](https://omninumeris.atlassian.net/browse/KAN-128) | To Do | Story | KAN-19 | — | OW-1901 — Structured AI action proposals and previews |
| [KAN-129](https://omninumeris.atlassian.net/browse/KAN-129) | To Do | Story | KAN-19 | KAN-128 | OW-1902 — Approval and authorized execution |
| [KAN-130](https://omninumeris.atlassian.net/browse/KAN-130) | To Do | Story | KAN-19 | KAN-129 | OW-1903 — Undo, audit and prompt injection defenses |
| [KAN-131](https://omninumeris.atlassian.net/browse/KAN-131) | To Do | Story | KAN-20 | KAN-28 | OW-2001 — Curated model downloads and integrity checks |
| [KAN-132](https://omninumeris.atlassian.net/browse/KAN-132) | To Do | Story | KAN-20 | — | OW-2002 — Custom GGUF import and compatibility validation |
| [KAN-133](https://omninumeris.atlassian.net/browse/KAN-133) | To Do | Story | KAN-20 | — | OW-2003 — Hardware profiling and benchmarks |
| [KAN-134](https://omninumeris.atlassian.net/browse/KAN-134) | To Do | Story | KAN-20 | KAN-133 | OW-2004 — Adaptive memory, context and thermal scheduling |
| [KAN-135](https://omninumeris.atlassian.net/browse/KAN-135) | To Do | Story | KAN-21 | — | OW-2101 — Audio recording and recovery |
| [KAN-136](https://omninumeris.atlassian.net/browse/KAN-136) | To Do | Story | KAN-21 | KAN-135 | OW-2102 — Synchronized ink and playback |
| [KAN-137](https://omninumeris.atlassian.net/browse/KAN-137) | To Do | Story | KAN-21 | KAN-135 | OW-2103 — Offline real-time or deferred transcription |
| [KAN-138](https://omninumeris.atlassian.net/browse/KAN-138) | To Do | Story | KAN-21 | KAN-137 | OW-2104 — Editable transcripts, search and export |
| [KAN-139](https://omninumeris.atlassian.net/browse/KAN-139) | To Do | Story | KAN-22 | — | OW-2201 — Editable mind maps and flowcharts |
| [KAN-140](https://omninumeris.atlassian.net/browse/KAN-140) | To Do | Story | KAN-22 | KAN-139 | OW-2202 — Geometry and technical diagrams |
| [KAN-141](https://omninumeris.atlassian.net/browse/KAN-141) | To Do | Story | KAN-22 | — | OW-2203 — Precision tools, snapping and alignment |
| [KAN-142](https://omninumeris.atlassian.net/browse/KAN-142) | To Do | Story | KAN-23 | — | OW-2301 — Full-screen presentation and laser pointer |
| [KAN-143](https://omninumeris.atlassian.net/browse/KAN-143) | To Do | Story | KAN-23 | KAN-142 | OW-2302 — External display and presenter controls |
| [KAN-144](https://omninumeris.atlassian.net/browse/KAN-144) | To Do | Story | KAN-24 | KAN-145 | OW-2401 — App lock and individually protected notebooks |
| [KAN-145](https://omninumeris.atlassian.net/browse/KAN-145) | To Do | Story | KAN-24 | — | OW-2402 — Encrypted storage, indexes and secure cache lifecycle |
| [KAN-146](https://omninumeris.atlassian.net/browse/KAN-146) | To Do | Story | KAN-24 | KAN-145 | OW-2403 — Encrypted portable backup, selective restore and offline transfer |
| [KAN-147](https://omninumeris.atlassian.net/browse/KAN-147) | To Do | Story | KAN-25 | — | OW-2501 — Custom themes, toolbars, gestures and shortcuts |
| [KAN-148](https://omninumeris.atlassian.net/browse/KAN-148) | To Do | Story | KAN-25 | — | OW-2502 — Adaptive layouts and accessibility |
| [KAN-149](https://omninumeris.atlassian.net/browse/KAN-149) | To Do | Story | KAN-26 | KAN-33 | OW-2601 — Free core and lifetime Pro purchase with offline entitlement |
| [KAN-150](https://omninumeris.atlassian.net/browse/KAN-150) | To Do | Story | KAN-26 | — | OW-2602 — Commercial licensing, privacy and release validation |
