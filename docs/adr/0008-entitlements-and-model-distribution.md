# ADR 0008 · Offline entitlements and local model distribution

**Status** Proposed for product and security review  
**Ticket** OW-007 / [KAN-33](https://omninumeris.atlassian.net/browse/KAN-33)  
**Traceability** FR-BIL-01 through FR-BIL-04, FR-MDL-01 through FR-MDL-05, NFR-10

## Entitlement matrix

| Capability | Free | Confirmed lifetime Pro |
|---|---|---|
| Core notebooks and calculator | Unlimited offline | Unlimited offline |
| Open and recover existing user content | Available under the same notebook authorization rules | Available under the same notebook authorization rules |
| Pricing and advanced feature gates | Pending product review | Pending product review |

OmniNumeris has no app account, advertising or subscription. Offer lifetime Pro in Play as a non-consumable one-time purchase. Show localized pricing from Play once the product and prices have been approved. Gate only capabilities in the approved matrix. Preserve content, backups and metadata when billing is unavailable or access changes. Content created with a future gated tool still needs a readable representation and a recovery path.

## Purchase, offline access and recovery

Initiate network-dependent purchase or restore from an explicit user action. Use the Play account that owns the purchase for restore. Keep OmniNumeris workspace data and model usage out of billing requests. Keep Play credentials out of the app and any entitlement evidence out of portable notebook backups and Android automatic backup.

Validate the package, product and purchase state and deduplicate by the Play purchase token. A pending, canceled or unverified purchase does not unlock Pro. Record fulfillment idempotently and acknowledge verified purchases promptly. Keep a durable in-flight record so a restart can resume acknowledgment without double-fulfillment. Create an offline-confirmed entitlement only after confirmation of the acknowledgment, including restore of an already acknowledged purchase. Acknowledgment and revocation details follow the platform guidance. [Play Billing integration](https://developer.android.com/google/play/billing/integrate) · [Play Billing fraud and abuse guidance](https://developer.android.com/google/play/billing/security)

Store confirmed entitlement locally in installation-bound authenticated storage. Offline access has no renewal timer. A verified revocation may remove Pro access at a later successful reconciliation. Connection failures, an unavailable Store and an empty result with ambiguous account identity leave the previously confirmed state intact. Explain the result and offer an explicit retry. On reinstall or device change, core works immediately and the user can restore Pro online through Play. Restore never needs notebook data.

Google recommends a backend for verification and revocation. Approval of the verification architecture, operational cost and privacy review is pending in the implementation work. A client-only cache on a compromised device cannot guarantee timely detection of revocations. The lifetime policy also accepts delayed revocations while a device stays offline. KAN-149 must resolve the trusted verification signal before production purchase or restore ships. [Play Billing fraud and abuse guidance](https://developer.android.com/google/play/billing/security) · [KAN-149](https://omninumeris.atlassian.net/browse/KAN-149)

## Model distribution

Show the model, format, size, license, origin and device compatibility before the user starts the download. Keep the calculator and notebooks independent of model availability. The first catalogue needs explicit model and artifact approval, including redistribution rights, device benchmarks and source pinning. No runtime engine or provider selection is made by this ADR.

The app verifies catalogue authenticity with a pinned verification key before accepting versioned model entries. Plan review for key rotation and rejection of catalogue downgrades. Each entry binds the model and immutable artifact version, approved HTTPS origins, expected byte length, SHA-256, license and supported format and architecture. Enforce those origins for redirects. A digest verifies integrity only when the expected value comes from authenticated catalogue metadata.

Download to an app-private staging file only after approval. Default to Wi-Fi and show transfer progress. Resume only the same artifact version and HTTP validator with a matching range. Discard an inconsistent partial download. Pause retains a bounded partial file for resume. Cancel closes the stream and removes staging data. After process death, a restart reconciles staging state and waits for the user's next action before transfer resumes.

Verify size and full-file digest, then validate model metadata against the supported architecture, format and device limits before permitting native load. Publish the validated file and update its registry entry using a recoverable commit. Keep the last valid version selectable after any failed update. Locally imported files require their own provenance and license checks. A locally computed digest identifies bytes for later integrity checks. It does not establish their provenance. Deletion releases model-owned files after active use has stopped. It preserves user documents and their references.

## License inventory

| Artifact | License evidence | Use and review |
|---|---|---|
| Qwen2.5-0.5B-Instruct GGUF Q4_K_M | Apache-2.0 on the publisher's model card. The pinned SHA-256 is recorded in `BenchmarkProfile.kt`. | KAN-28 benchmark fixture. Record applicable notices and confirm the pinned artifact and device envelope before catalogue approval. [Publisher model card](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) · [Pinned benchmark check](../../inference-spike/src/main/kotlin/com/omninumeris/inferencespike/BenchmarkProfile.kt) |
| llama.cpp at `a6d1a74dbebce3a16f4f2ceddbf38c21a08d8674` | MIT license confirmed in the extracted pinned benchmark source. | Benchmark runtime. Audit bundled code and notices separately before distribution. [Upstream license](https://github.com/ggml-org/llama.cpp/blob/master/LICENSE) |
| Tesseract `tessdata_fast` English weights at `923915d4ced2a7235221785785a29c4a42d4a` | Upstream project publishes Apache-2.0. The license file URL for the pinned revision returned HTTP 404 during this review. | Host OCR feasibility fixture. Confirm terms at the exact artifact revision before redistribution. Production handwriting weights and engine remain unselected. [Upstream license](https://github.com/tesseract-ocr/tessdata_fast/blob/main/LICENSE) · [OCR experiment](0005-exact-math-and-ocr-evaluation.md) |
| Embedding, reranking, handwriting, vision and speech artifacts | Pending selection and per-artifact review. | Catalogue inclusion needs verified terms, notices, immutable hashes and device measurements. |

## Acceptance checks for implementation

| Scenario | Required result |
|---|---|
| First launch offline with no purchase or model | Core notebooks and calculator are available with no usage cap or setup. |
| Play reports a pending purchase, then completion after app restart | No Pro access while pending. Replay or acknowledgment retry produces one durable fulfillment per token. |
| Confirmed Pro goes offline or Play is unavailable | Existing confirmation and user content survive. Core remains available. |
| New installation or ambiguous empty Play result | The new installation starts with core access and can restore online. An ambiguous result cannot revoke an existing confirmation. |
| Verified refund or revocation | Apply approved gates to future gated actions. Existing content and backups remain accessible under their normal permissions. |
| Interrupted download, process death or cancellation | No staging artifact loads. Resume only a matching artifact. Cancellation clears the partial. |
| Same-sized corrupt file, wrong format or failed update | Reject before native load and preserve the last valid model. |
| No approved license or device evidence | Keep the model out of the catalogue. |

This ADR is the feasibility deliverable. Play sandbox billing, refund and account-change flows, download recovery, catalogue signing and install-time validation require implementation tests before release.

## Verification

On 2026-11-25, the existing offline `-PinferenceSpike=true :inference-spike:testDebugUnitTest` passed five cases, including invalid model rejection and a reference SHA-256 vector. The local Qwen artifact matched the pinned size of 491,400,032 bytes and the SHA-256 in the benchmark source. The cases in this ADR were reviewed against the OMNI baseline and Play documentation. The invalid model test exercises the size guard. Same-length corruption and interrupted downloads remain end-to-end implementation tests.

## Sources

- [OMNI product vision](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/327700)
- [OMNI math and AI design](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382)
- [OMNI testing and roadmap](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/688150)
