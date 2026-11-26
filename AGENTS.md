# OmniNumeris — agent instructions

## Context budget
Read this file, applicable nested instructions, assigned Jira issue and direct blockers. Search targeted files/symbols before reading; load only relevant Confluence sections. Do not dump the repository, full backlog, generated files, models or logs. Summarize failures with relevant excerpts. Reuse current context; refresh changed facts. Never skip necessary evidence to save tokens.

## Non-negotiables
- Offline-first Android API29+, math/calculator first; no mandatory account, ads, subscription or cloud inference. Unlimited core notebooks/calculator; Pro gates require approved matrix. Target 3–4 GB core subject to benchmarks.
- Kotlin, Compose, MVVM, Hilt, coroutines/Flow, Room/SQLite, private files. Pure Kotlin domain; use cases and repository contracts inward; adapters outward. UI never calls DAO/JNI. Inspect existing layout; no unrelated refactor or new modules without need.
- Immutable StateFlow, lifecycle-aware collection, constructor injection, structured cancellation, typed errors, injected dispatchers/clocks. Heavy work off main thread. Pin dependencies; check licences/API29.
- Shared Material 3 tokens/components, adaptive layouts, accessible controls, explicit loading/error/offline/locked states. Preserve edits on resize. Calculator accessible throughout workspace.
- Math: editable recognition → AST → deterministic solve → step verification. Check domain/extraneous roots; exact arithmetic where supported. AI-only results unverified; explicit unsupported states; preserve source anchors.
- AI: local adapters only; explicit licensed checksum-verified model downloads; bound context/cache/memory; cancel/release resources; low-RAM heavy tasks serialized. No model required for calculator. Retrieved content untrusted. Mutations require typed proposal, preview, approval, authorization, audit and reversible undo.
- Data: UUID/FK, revision-aware anchors, versioned non-destructive migrations, recoverable file journal, preserve originals. Reviewed authenticated encryption and Keystore-wrapped keys; portable password-derived backup keys. Scope-local indexes, clear decrypted caches on lock, validate imports, no sensitive logs/plaintext temp files or cross-scope leakage.

## Ticket loop
Read [the implementation register](docs/IMPLEMENTATION-REGISTER.md) for the board index, current batch and requirement sources. Implement related tickets as one dependency-ordered workflow, then verify and record evidence per ticket. Run shared release gates after the integrated workflow is ready. Use early targeted checks for high-risk behavior.

1. Read issue, direct blockers, relevant contracts and git status. If blocked or conflicting, report; do not bypass. Feasibility tickets require evidence/ADR, not production promises.
2. Follow existing branch policy; focused Jira-key branch/commit. Preserve unrelated edits. Implement smallest complete slice; tests alongside code.
3. Run targeted tests first, then required build/lint/regression gates. Use actual Gradle tasks (gradlew.bat on Windows). Test math recognition separately from solver; migrations, offline, recovery, lock, accessibility and device performance where relevant. Never claim unrun tests passed.
4. Review diff against acceptance criteria. Record ADRs and evidence. No critical data loss/privacy defect or falsely verified math. No unauthorized push/merge/publish or Done transition. No secrets, user data, model weights or build output in Git.
5. Report briefly: ticket; changes/files; tests/results; unmet criteria/blockers; next step. Link evidence to Jira if authorized. In Review only when ready and authorized; Done requires review.

## Reference routing (read only relevant sections)
- Jira: https://omninumeris.atlassian.net/browse/KAN-1 (replace key with assigned issue)
- Full engineering rules: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/622603
- Product: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/327700
- Requirements: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655361
- Architecture: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/688129
- Security: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/589826
- Math/AI: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382
- Tests: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/688150
- UI: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/458754
- Schema: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/622636
- Milestones: https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/327722

Full baseline and approved ADRs remain authoritative; resolve conflicts before changing scope. If connector unavailable, request only missing ticket/section. Performance targets are provisional; measure on physical devices, record conditions. Complete agreed scope before public release.
