# Product vision and SRS

Version 1.0 --- consolidated baseline; proposed technical targets require validation.

## Product overview

Premium private offline workspace for all ages and education levels. Notes, drawing, mathematics, reading, study, documents and general AI are equal priorities. Android phones/tablets, API 29+, target ARM64 and 3 GB RAM. Complete product before public release; internal incremental builds. No account, ads or subscription. Internet only for initial resources, purchases/restoration and optional updates.

## Functional requirements

### Workspace library

-   **FR-LIB-01** --- Support nested folders.
-   **FR-LIB-02** --- Support tags.
-   **FR-LIB-03** --- Support favourites.
-   **FR-LIB-04** --- Support recent items.
-   **FR-LIB-05** --- Support grid/list.
-   **FR-LIB-06** --- Support sorting.
-   **FR-LIB-07** --- Support batch operations.
-   **FR-LIB-08** --- Support duplicate detection.
-   **FR-LIB-09** --- Support recycle bin.
-   **FR-LIB-10** --- Support snapshots.
-   **FR-LIB-11** --- Support version comparison and restore.
-   **FR-LIB-12** --- Support tabs and session restoration.

### Hybrid notebook

-   **FR-NOT-01** --- Support fixed pages and infinite canvas.
-   **FR-NOT-02** --- Support free-position objects and structured rich text.
-   **FR-NOT-03** --- Support tables, lists, checklists.
-   **FR-NOT-04** --- Support page sizes and plain/lined/grid/dot/Cornell/music/custom templates.
-   **FR-NOT-05** --- Support layers.
-   **FR-NOT-06** --- Support transforms.
-   **FR-NOT-07** --- Support undo/redo.
-   **FR-NOT-08** --- Support autosave.
-   **FR-NOT-09** --- Support attachments.
-   **FR-NOT-10** --- Support split screen.
-   **FR-NOT-11** --- Support floating tools.

### Drawing studio

-   **FR-DRW-01** --- Support vector ink and tiled raster painting.
-   **FR-DRW-02** --- Support pressure and tilt.
-   **FR-DRW-03** --- Support brush presets, smoothing, stabilization, opacity, palettes, eyedropper, fill, gradients, symmetry, layer blend modes, pixel/stroke erasers, lasso, transforms, rotation, reference images and transparent high-resolution export.

### Stylus

-   **FR-STY-01** --- Support active pen detection.
-   **FR-STY-02** --- Support palm rejection.
-   **FR-STY-03** --- Support stylus-only mode.
-   **FR-STY-04** --- Support finger navigation.
-   **FR-STY-05** --- Support pressure calibration.
-   **FR-STY-06** --- Support tilt, hover and buttons where supported.
-   **FR-STY-07** --- Support handedness and latency.

### Handwriting

-   **FR-HWR-01** --- Support offline English stroke recognition.
-   **FR-HWR-02** --- Support image handwriting recognition.
-   **FR-HWR-03** --- Support editable conversion.
-   **FR-HWR-04** --- Support corrections.
-   **FR-HWR-05** --- Support search indexing.
-   **FR-HWR-06** --- Support preserve original ink.
-   **FR-HWR-07** --- Support beautification and language extension.

### Mathematics

-   **FR-MAT-01** --- Support typed, handwritten, camera, book and PDF inputs.
-   **FR-MAT-02** --- Support math keyboard and LaTeX.
-   **FR-MAT-03** --- Support circle/lasso recognition.
-   **FR-MAT-04** --- Support manual, suggestion and automatic insertion modes.
-   **FR-MAT-05** --- Support expression correction.
-   **FR-MAT-06** --- Support step generation, validation, hints, alternative methods, history and source links.
-   **FR-MAT-07** --- Support verified versus unverified results.

### Calculator

-   **FR-CAL-01** --- Support floating movable resizable calculator throughout app.
-   **FR-CAL-02** --- Support standard, scientific, programmer, financial, statistics, matrices, graphing and unit conversions.
-   **FR-CAL-03** --- Support exact fractions, precision, memory, history, clipboard and insertion into active notes.

### Graphing

-   **FR-GRP-01** --- Support 2D multi-function plots.
-   **FR-GRP-02** --- Support pan/zoom.
-   **FR-GRP-03** --- Support roots, intercepts and intersections.
-   **FR-GRP-04** --- Support parameters.
-   **FR-GRP-05** --- Support scatter and statistics.
-   **FR-GRP-06** --- Support polar and parametric plots.
-   **FR-GRP-07** --- Support adaptive 3D.
-   **FR-GRP-08** --- Support editable insertion and vector/image export.

### Scanner

-   **FR-SCN-01** --- Support camera/import.
-   **FR-SCN-02** --- Support automatic boundaries and stable capture.
-   **FR-SCN-03** --- Support manual corners.
-   **FR-SCN-04** --- Support perspective, shadow and contrast correction.
-   **FR-SCN-05** --- Support colour/grayscale/mono.
-   **FR-SCN-06** --- Support multi-page.
-   **FR-SCN-07** --- Support receipts, books, whiteboards, business cards.
-   **FR-SCN-08** --- Support dewarping.
-   **FR-SCN-09** --- Support oCR and searchable PDF.
-   **FR-SCN-10** --- Support structured extraction.
-   **FR-SCN-11** --- Support preserve originals.

### PDF editor

-   **FR-PDF-01** --- Support read password-authorized PDFs.
-   **FR-PDF-02** --- Support search, thumbnails, bookmarks.
-   **FR-PDF-03** --- Support highlight, underline, strikeout, ink, comments, text boxes, shapes.
-   **FR-PDF-04** --- Support merge/split/extract/reorder/rotate/insert/delete.
-   **FR-PDF-05** --- Support forms, handwritten signatures, supported text edits, compression, password export, annotation flattening and signature invalidation warnings.

### Interoperability

-   **FR-IO-01** --- Support import PDF, EPUB, DOCX, TXT, Markdown, HTML, CBZ, JPEG, PNG, WebP, SVG, WAV, MP3, M4A, GGUF and native backups.
-   **FR-IO-02** --- Support export PDF/searchable PDF, PNG/JPEG, SVG, DOCX, TXT, Markdown, LaTeX, CSV, audio and native editable format.
-   **FR-IO-03** --- Support region/page/batch/folder/workspace export.
-   **FR-IO-04** --- Support quality, compression, cancellation and file picker.

### Book reader

-   **FR-BOK-01** --- Support collections, covers, metadata, reading position, contents, chapters, thumbnails, progress, search, bookmarks, coloured highlights, ink and typed annotations.
-   **FR-BOK-02** --- Support font, spacing, margins, themes, scrolling/paging, dual page, zoom, dictionary, translation, annotation export, split-screen notes and reading statistics.
-   **FR-BOK-03** --- Support circle-to-solve with linked solutions.

### Read aloud

-   **FR-TTS-01** --- Support offline voices.
-   **FR-TTS-02** --- Support selection/page/chapter playback.
-   **FR-TTS-03** --- Support pause/resume, speed, spoken text highlight, sentence navigation, listening position, background controls, sleep timer, scanned text and supported math speech.
-   **FR-TTS-04** --- Support no network voice fallback.

### Study centre

-   **FR-STD-01** --- Support manual and AI flashcards with media, spaced repetition, review ratings and source links.
-   **FR-STD-02** --- Support quizzes, MCQ, true/false, short answers and handwriting.
-   **FR-STD-03** --- Support timed exams, difficulty, hints, verified grading, mistake analysis, practice generation, goals, schedules, reminders, focus timer, progress, revision sheets and concept maps.
-   **FR-STD-04** --- Support manual mode without AI.

### Local assistant

-   **FR-AI-01** --- Support general chat, streaming, stop/regenerate, editable prompts, history, custom prompts, grammar, rewrite, tone, summary, translation, brainstorming, tutoring, code, tables, diagrams, Markdown/math rendering, citations, private context mode, export/delete and insertion.

### Multimodal AI

-   **FR-VIS-01** --- Support analyze photos, drawings, objects, diagrams, charts and geometry.
-   **FR-VIS-02** --- Support image questions, combined prompts and selected regions.
-   **FR-VIS-03** --- Support adaptive resolution.
-   **FR-VIS-04** --- Support oCR fallback.
-   **FR-VIS-05** --- Support compatible vision model/projector imports.

### Knowledge management

-   **FR-RAG-01** --- Support index permitted notes, ink recognition, OCR, books, documents, transcripts, solutions and study materials.
-   **FR-RAG-02** --- Support keyword and semantic search.
-   **FR-RAG-03** --- Support hybrid retrieval, incremental updates and deletion.
-   **FR-RAG-04** --- Support configurable scope, clickable region citations, links, backlinks, interactive graph and approved relationship suggestions.

### AI actions

-   **FR-AGT-01** --- Support propose create/edit/organize notes, diagrams and study content.
-   **FR-AGT-02** --- Support preview and approval.
-   **FR-AGT-03** --- Support validate commands, execute authorized services, undo and action history.
-   **FR-AGT-04** --- Support no arbitrary filesystem/database access.

### Model management

-   **FR-MDL-01** --- Support device profiling.
-   **FR-MDL-02** --- Support curated catalogue.
-   **FR-MDL-03** --- Support resumable Wi-Fi downloads, checksums, GGUF imports and architecture validation.
-   **FR-MDL-04** --- Support multiple models, task routing, basic/expert settings, context, sampling, threads, acceleration, benchmarks, licence display, versions and deletion.
-   **FR-MDL-05** --- Support dynamic loading, memory/thermal monitoring and task queue.

### Audio

-   **FR-AUD-01** --- Support record/pause/resume, foreground indicators, timestamp ink synchronization, playback speed, bookmarks, import, adaptive live/deferred offline transcription, editable timestamped segments, search, summaries, export, cancellation and interruption recovery.

### Diagrams

-   **FR-DGM-01** --- Support shapes, arrows, connectors, mind maps, flowcharts, org charts, selected UML, ruler, compass, protractor, dimensions, snapping, alignment, distribution, templates, AI editable diagrams and SVG export.

### Presentation

-   **FR-PRS-01** --- Support full screen, navigation, laser pointer, temporary annotations, external displays, presenter controls, zoom and optional annotation saving.

### Privacy and backup

-   **FR-SEC-01** --- Support app PIN/biometrics, notebook encryption, auto-lock, protected caches/indexes, secure handwritten signatures, automatic/manual local backup, encrypted export, validation, selective/full restore, offline device transfer, deletion, permission minimization and screenshot protection.

### Customization

-   **FR-CUS-01** --- Support light/dark/system themes, accents, toolbars, gestures, shortcuts, keyboard/mouse, adaptive layouts, widgets, share menu, quick capture, defaults, English-first localization and accessibility.

### Commercial

-   **FR-BIL-01** --- Support free unlimited core notebook and calculator.
-   **FR-BIL-02** --- Support one-time Pro, no ads or app account.
-   **FR-BIL-03** --- Support play Billing, restore, cached entitlement, preserve user data, clear feature gates and licence notices.
-   **FR-BIL-04** --- Support pricing pending market research.

## Mathematics coverage

Arithmetic, fractions, percentages, number theory, algebra, polynomial equations, inequalities, systems, exponents, logarithms, trigonometry, geometry, coordinate geometry, calculus, linear algebra, statistics, probability, discrete math, complex numbers, numerical methods, selected differential equations, financial and engineering mathematics, and word problems. Publish supported classes; verify domain restrictions and extraneous roots; never label an AI-only answer verified.

## Non-functional requirements

-   NFR-01: Core functions work in airplane mode after setup, including on reference 3 GB devices.
-   NFR-02: Target 60 FPS canvas, app-added ink latency under 25 ms, warm typical page open under 1 s; benchmark before commitment.
-   NFR-03: Basic arithmetic under 100 ms, basic algebra under 1 s, typical scan under 5 s; proposed targets.
-   NFR-04: No known falsely verified answers in release corpus; report unsupported cases.
-   NFR-05: Transactional autosave and recovery; target 99.5% crash-free benchmark sessions.
-   NFR-06: Authenticated encryption, no remote inference, permission-aware indexes, no content telemetry.
-   NFR-07: Screen reader, scalable text, accessible contrast and keyboard navigation.
-   NFR-08: Modular Clean Architecture, independently testable engines, versioned formats and migrations.
-   NFR-09: Bounded memory/cache, background computation, thermal and battery adaptation, cancellation.
-   NFR-10: Commercial licence review and secure untrusted input handling.

## Scope boundaries

Unlimited means no artificial limit, subject to storage. Infinite canvas is sparse/tiled. No DRM bypass. DOCX is rich-text conversion, not full layout fidelity. PDF text editing limited to supported structures. Handwritten signature is not cryptographic signing. Currency rates are manual or dated offline data. Vision, pressure, palm rejection and external displays depend on hardware.
