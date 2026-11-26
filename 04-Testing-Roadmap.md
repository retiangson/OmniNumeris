# Testing and implementation roadmap

## Testing

Unit: AST, calculator, step rules, scheduling, permissions, transforms, repositories. Property tests: mathematical equivalence with domain restrictions, serialization, undo/redo. Integration: database migrations, asset transactions, recognition, RAG citations, model loading, import/export round trips, billing and backup. UI: phone/tablet, stylus/finger, accessibility, split screen, circle-to-solve, calculator insertion, reading position and AI approvals. Security: malicious archives/PDF/GGUF, prompt injection, locked index leakage, key loss, tampered backup and network capture in airplane mode. Performance: physical 3/4/6/8+ GB devices, API 29 and current Android, low storage, thermal throttling, process death, long sessions, raster tiles, OCR accuracy and model latency. Build labelled math corpus with typed/ink/photo/book inputs; measure recognition separately from solving and verification. Study grading distinguishes deterministic and subjective AI feedback. Test offline TTS, deferred transcription and no network fallback. Release gates: all required stories accepted, no critical security/data loss bugs, no known falsely verified math answers, commercial licence inventory, documented device limits and Play compliance.

## Roadmap (internal milestones; no premature public release)

1.  Feasibility spikes: renderer, math OCR/CAS, PDF SDK, local AI 3 GB, licences.
2.  Foundation: Gradle, CI, design system, database, storage, security and resource manager.
3.  Workspace: library, notebook, ink, raster, templates, recovery, calculator.
4.  Math: parser, recognition, solver, verification, graphing and cross-workspace gestures.
5.  Documents: scanner, OCR, PDF, DOCX, EPUB, reading, annotations, TTS and exports.
6.  AI: model manager, chat, vision, local RAG, citations, knowledge graph and approved actions.
7.  Learning and media: flashcards, assessments, audio sync/transcription, diagrams, presentation.
8.  Commercial and hardening: backup/transfer, customization, billing, accessibility, performance, security, licence audit, closed testing and release.

## Definition of done

Requirement IDs linked; acceptance tests pass; offline behavior verified; error and cancellation paths tested; accessibility and memory impact reviewed; security and licence review completed where applicable; docs updated. No ticket marked done without evidence.
