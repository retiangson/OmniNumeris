# ADR 0006 · Offline document SDK evaluation

**Status** Provisional, 2026-11-24  
**Ticket** OW-005 / [KAN-31](https://omninumeris.atlassian.net/browse/KAN-31)  
**Traceability** FR-PDF-01 through FR-PDF-05, FR-BOK-01, FR-IO-01, FR-IO-02, NFR-01, NFR-09 and NFR-10

## Decision

Evaluate platform PDF rendering, PDFBox Android for document operations, and Readium Streamer for EPUB parsing and location recovery. The two SDK dependencies and generated documents are confined to the instrumentation APK. Production dependency selection requires the remaining compatibility, security and redistribution checks.

Keep imported originals immutable. Represent PDF reading positions using document revision identity and page index, with coordinates and quote context for annotations. Validate each exported document by reopening it before publishing the copy. Signatures and text replacement need specific preservation or invalidation rules before implementation. Protected imports and their derived files belong in the storage scope designed by OW-006.

Use EPUB resource URLs with a fragment and quote context for positions. Treat page counts as layout-dependent. The Readium probe opens local assets, denies HTTP requests, and resolves saved locators against a reopened publication. The instrumentation renders the saved fragment in an offline WebView at 100% and 175% text zoom and checks that it scrolls into view after both layouts. Production Readium Navigator integration needs separate validation.

## Candidate matrix

| Candidate | Capabilities and SDK boundary | Redistribution and open work |
| --- | --- | --- |
| Android platform PdfRenderer | Baseline page rendering from API 21. API 35 adds password parameters and PDF inspection and editing APIs. | Platform API. Place untrusted parsing in an isolated process and verify the enabled features per OS level. [Android API reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) |
| Jetpack AndroidX PDF | The alpha19 release describes backported reading and rendering down to API 28. Method and feature availability still needed on the project's minimum API. | AndroidX candidate. Pin a supported version and verify rendering, form and export coverage before selection. [Jetpack PDF release notes](https://developer.android.com/jetpack/androidx/releases/pdf) |
| PDFBox Android 2.0.27.0 | Instrumentation evaluates text extraction, password-authorized reading, text annotation, form appearance and a page removal/export. Embedded text extraction leaves scanned pages for OCR. | Apache-2.0 project with separate third-party notices. The Android port trails the current upstream PDFBox releases. Review port maintenance, bundled dependencies and parser isolation before opening untrusted documents. [Android port and license](https://github.com/TomRoush/PdfBox-Android) · [Upstream PDFBox security](https://pdfbox.apache.org/security.html) |
| Readium Kotlin Toolkit 3.1.2 | API 23 minimum for this release. Instrumentation evaluates the EPUB spine, chapter search and locator round-trip. | BSD-3-Clause for the toolkit. Audit transitive dependencies and exercise the navigator and reflow before adoption. LCP and protected EPUBs require separate entitlement and license review. [Toolkit guide](https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/guides/getting-started.md) · [Toolkit license](https://github.com/readium/kotlin-toolkit/blob/3.1.2/LICENSE) |
| MuPDF | Alternative renderer and PDF editing candidate. | Vendor offers AGPL and commercial licensing. Evaluate licensing approval and Android compatibility before integration. [Artifex licensing options](https://artifex.com/products) |
| Apache POI XWPF | Separate DOCX import/export candidate with access to document paragraphs, runs and tables. | Evaluate OOXML and Android dependency compatibility under the DOCX interoperability work. The API does not establish Word layout fidelity. [XWPF guide](https://poi.apache.org/components/document/quick-guide-xwpf.html) |

## Reproduction

The original fixture generator is [new-document-fixtures.py](../../tools/benchmarks/new-document-fixtures.py). It uses ReportLab and Pillow and writes test assets for the fillable, image-only, many-page and password PDFs and a reflowable EPUB. The public fixture password is defined in the generator.

```powershell
.\gradlew.bat '-PdocumentSdkSpike=true' :check :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e package com.omninumeris.app.documents com.omninumeris.app.test/androidx.test.runner.AndroidJUnitRunner
```

Record device API and ABI, offline setting, pass count, and sampled resource measurements. The many-page fixture exercises page selection and bounded bitmap sizing. PSS before and after the sample includes the instrumentation process and cannot establish peak memory or 3 GB feasibility.

## Verified results

All five checks passed on 2026-11-24 on the offline API 29 x86-64 emulator with 2 GiB of configured memory and the API 36 ARM64 Samsung tablet. The tablet's network settings stayed in place, and the EPUB test received zero HTTP requests on both devices.

| Runtime | PDF sample, 3 of 128 pages | PSS before → after, KiB | EPUB open, search, reopen and reflow |
| --- | ---: | ---: | ---: |
| API 29 x86-64 emulator, airplane mode | 105 ms | 129,885 → 130,293 | 4,366 ms |
| API 36 ARM64 Samsung tablet | 41 ms | 176,077 → 175,437 | 1,192 ms |

The PDF tests rendered the saved page and an image-only scan, rejected malformed and wrong-password input, and opened the authorized password fixture. The exported copy retained its text annotation, updated form field and matching widget appearance. The second export reopened as one page, and the source bytes remained unchanged. The 128-page sample measures a short bounded render pass. The EPUB timing includes WebView startup and two layouts.

PDFBox's loader required the test APK's glyph resources through an application-compatible context. The password fixture uses legacy PDF encryption to exercise interoperability. Production signature verification, generic text replacement, protected EPUBs, stress behavior and a final dependency audit remain open.
