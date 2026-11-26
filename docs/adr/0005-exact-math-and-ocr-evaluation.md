# ADR 0005 · Exact math and OCR evaluation

**Status** Provisional, 2026-11-24  
**Ticket** OW-004 / [KAN-30](https://omninumeris.atlassian.net/browse/KAN-30)  
**Traceability** FR-MAT-01, FR-MAT-02, FR-MAT-06, FR-MAT-07, NFR-03 and NFR-04

## Decision

Prototype a pure Kotlin/JVM AST and exact rational engine for typed arithmetic and linear equations. The parser accepts explicit arithmetic operators, decimal numbers, parentheses, integer powers, square-root notation, and the variable `x`. Verify the linear step sequence by checking equality residuals and substitute the resulting value into the original AST.

Limit input length, nesting, numeric literal size, exponent and result size. Treat variable denominators and nonlinear operations as unsupported before algebraic rearrangement. Preserve domain errors from division by zero, undefined powers and negative radicands. Typed, ink, image and document adapters can use the AST after expression correction.

Evaluate OCR at the expression-transcription boundary. Keep character confusion, fraction grouping, superscripts and radical mistakes visible in the recognition score. A correct solution of a misrecognized expression cannot count as successful recognition.

The Tesseract baseline is unsuitable for accepting formulas automatically on the evaluated fixtures. Android Tesseract matched the Windows baseline's accuracy counts on the Samsung tablet and offline API 29 emulator. Keep conventional text and digital-ink OCR, plus dedicated math recognition, as separate candidates. A dedicated model needs an approved weights and dataset inventory, Android runtime compatibility and an offline physical-device benchmark.

The PP-FormulaNet-S host experiment improved structural recognition on the same fixtures. The tested Windows conversion path could not export that model. Published Pix2Text MFR ONNX files ran on the ARM64 tablet and the offline Android 10 emulator. Pix2Text matched all five printed fixtures on both Android targets and six of twelve human tablet samples on the Samsung. The two formula candidates misread different human samples.

Advance the workflow with provisional, editable recognition and explicit handling for ambiguous or incomplete output. Keep the exact Kotlin AST as the solver boundary. The current results support a cropped-image prototype. Production model adoption, ordered-stroke recognition, wider handwriting coverage and sustained Android performance require separate validation and approval.

See [supported syntax, candidate status and measurements](../benchmarks/math-recognition-and-solving.md).

## References

- [OmniNumeris math and offline AI architecture](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655382)
- [Tesseract 5.2 Windows wrapper](https://github.com/charlesw/tesseract)
- [Tesseract English model data and license](https://github.com/tesseract-ocr/tessdata_fast/tree/923915d4ced2a7235221788285785a29c4a42d4a)
- [ML Kit Digital Ink Android guide](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android)
- [UniMERNet research implementation](https://github.com/opendatalab/UniMERNet)
