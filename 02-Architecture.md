# Architecture and feasibility

## Design

Modular Android monolith: Compose/MVVM/StateFlow → application use cases → pure Kotlin domain contracts ← infrastructure adapters. Hilt DI, coroutines, Room/SQLite, private blob storage, NDK/JNI. No required backend. Separate feature modules from reusable engines; avoid circular dependencies. Native runtimes behind cancellable interfaces.

## Engines

Canvas: vector strokes and sparse tiled raster layers, spatial index, viewport culling, disk-backed undo, pressure/tilt samples, immutable object IDs. Compose shell with hardware-accelerated drawing surface. CameraX/OpenCV for scanner; bundled OCR and Digital Ink candidates; separate 2D math OCR model. Math AST parser → deterministic CAS/step rules → domain-aware verifier → optional LLM explanation. Candidate CAS must pass commercial licence and accuracy review. KaTeX bundled locally. PDF engine undecided; Readium candidate for EPUB; OOXML adapter for DOCX. Media3, whisper.cpp and offline TTS. llama.cpp for GGUF, compatible vision projectors, small ONNX/LiteRT embeddings, SQLite FTS and local vector retrieval.

## AI pipeline

Input → task classification → permission check → recognition/extraction → retrieval or deterministic tool → resource scheduler → local model if needed → validate output → cite source anchors → preview approved mutations. Never execute arbitrary generated code or SQL. Recheck authorization at retrieval and execution. Treat imported text as untrusted data.

## Resource policy

3--4 GB: core always available; small 0.5--0.6B quantized model candidate, keyword-first retrieval, deferred transcription, one heavy task at a time. 6 GB: test 1--1.5B and embeddings. 8--12+ GB: test 1.5--7B and vision depending on free memory. RAM alone is not sufficient. Profile CPU, ABI, free memory, context/KV cache, thermal and storage. Release OCR before LLM when needed. Never silently send work online. Model catalogue signed/verified, resumable downloads, custom import validation. A 4096-square RGBA layer consumes 64 MiB before overhead; use sparse tiles.

## Feasibility and risks

High: core notes, calculator, scanning, reader. Medium: raster drawing, local RAG, transcription, advanced math. High risk: handwritten math OCR, general vision on low RAM, PDF text editing and step verification. Prototype each before locking dependencies. Economic: no remote inference cost but model hosting, Play fees, licences, test devices and support remain. 100k downloads of 1 GB ≈ 100 TB transfer. Pricing pending competitor and cost analysis. Solo development may take 12--24+ months; not a commitment.

## Open decisions / spikes

PDF SDK licence and API 29 compatibility; math CAS and step rules; math OCR training/benchmark corpus; rendering/ink latency; offline TTS availability; EPUB anchor stability; DOCX fidelity; model distribution rights and bandwidth; actual 3 GB device benchmarks; encrypted search implementation; portable backup key derivation; billing offline entitlement policy; Free/Pro feature boundaries and price; child audience Play policy; product name/trademark. Record decisions as ADRs before implementation.
