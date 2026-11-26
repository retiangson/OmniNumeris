# ADR 0003 · Local inference benchmark prototype

**Status** Provisional, 2026-11-24  
**Ticket** OW-002 / [KAN-28](https://omninumeris.atlassian.net/browse/KAN-28)  
**Traceability** NFR-01, NFR-06, NFR-09 and FR-MDL-01, FR-MDL-04, FR-MDL-05

## Decision

Build a separate opt-in debug Android APK for an ARM64 CPU-only llama.cpp benchmark. The ordinary app build excludes the benchmark module and model. Pin the MIT-licensed llama.cpp source archive at commit `a6d1a74dbebce3a16f4f2ceddbf38c21a08d8674` and check its SHA-256 during build. Use the Qwen2.5 0.5B Instruct Q4_K_M GGUF from the model publisher as an Apache-2.0 test fixture. Verify its SHA-256 in the benchmark before native loading.

Start measurements at 256 context tokens and two CPU threads, with mmap enabled, a maximum of 64 generated tokens, and one active inference run. Treat these settings as a test profile pending physical 3 GB evidence. Reject severe thermal states and require 512 MiB of available memory above Android's low-memory threshold before generation. This reserve is provisional. Monitor the same conditions during decode and cancel inference when the process crosses them.

The instrumentation uses fixed synthetic prompts and emits timing, memory, thermal status, and token counts. Generated text stays inside the model runtime. The debug APK requests no Internet permission. The benchmark process closes its model, context and sampler at the end of each run. Keep production model imports, distribution and user-facing AI features under their corresponding tickets.

See the [benchmark procedure](../benchmarks/local-inference.md) for device setup, measurements and the hardware gate.

## References

- [OmniNumeris math and offline AI architecture](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382)
- [llama.cpp Android builds](https://github.com/ggml-org/llama.cpp/blob/a6d1a74dbebce3a16f4f2ceddbf38c21a08d8674/docs/android.md)
- [Qwen benchmark model and upstream checksum](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/6dd44a1/qwen2.5-0.5b-instruct-q4_k_m.gguf)
