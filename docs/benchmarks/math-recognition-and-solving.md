# OW-004 math recognition and solver evaluation

## Exact typed solver

The Kotlin prototype supports explicit `+`, `-`, `*`, `/`, and `^`, their supported Unicode operator equivalents, parentheses, exact decimals, rational square roots, and linear equations in `x`. The parser accepts `sqrt(...)` and `√(...)`, treats exponentiation as right-associative, and binds a leading sign outside the power. Enter `2*x` for multiplication.

Verified linear solutions carry structured collection and division steps. The solver checks the equality residual at each step and independently substitutes the result into the original equation. It reports identities, contradictions, real-domain errors and unsupported cases, including variable denominators, variable powers other than first power, and nonlinear square-root equations. See the [typed regression corpus](../../domain/src/test/resources/math-typed-corpus.tsv).

The 2026-11-24 Samsung SM-X806B API 36 run measured parser and solver together. Each case ran once, followed by 50 warm-up calls and 200 measured calls. The instrumentation checked every answer. This microbenchmark covers two small inputs.

| Case | Expected | First call | Warm p50 | Warm p95 |
| --- | --- | ---: | ---: | ---: |
| `2+3×4` | `14` | 13.456 ms | 0.237 ms | 0.254 ms |
| `2*x+3=x-5` | `x=-8` | 0.676 ms | 0.293 ms | 0.400 ms |

### Supported classes

These limits apply to the OW-004 prototype. Expressions outside the AST grammar are rejected at parsing. Unsupported operations inside the grammar produce a controlled solver error. Neither path produces a verified answer.

| Class | Exact or numeric support | Domain, verification and unsupported cases |
| --- | --- | --- |
| Arithmetic | Exact rational | Evaluate the AST with bounded integer storage. Reject division by zero, undefined powers and excessive complexity. Powers require an integer exponent from −32 to 32. No floating-point fallback. |
| Fractions | Exact rational | Enter `a/b` with a nonzero denominator. Reduce to a canonical numerator and denominator and preserve grouping through the AST. Variable denominators in equations are unsupported. |
| Percentages | Exact through explicit arithmetic | Enter `p/100` with explicit multiplication for percentage-of calculations. Use the same rational checks. The percent symbol and contextual percent operators are unsupported. |
| Algebra | Exact one-variable linear equations and rational square roots | Check step residuals and substitute into the original equation. Distinguish identities and contradictions. Roots require a nonnegative rational perfect square. Nonlinear equations, variable denominators and irrational results are unsupported. |
| Systems | Unsupported | The grammar accepts a single equation in `x`. Simultaneous equations and singular or underdetermined systems have no verifier. |
| Inequalities | Unsupported | The grammar accepts equality only. No order-preserving transformation or interval verifier. |
| Trigonometry | Unsupported | No trigonometric operations or angle units. No domain or numerical tolerance verifier. |
| Geometry | Unsupported | Numeric rational subexpressions are evaluable. No figures, units or geometric constraint verifier. |
| Calculus | Unsupported | No limits, derivatives or integrals. No interval or convergence checks. |
| Matrices | Unsupported | No matrix input or dimension, determinant or invertibility checks. |
| Vectors | Unsupported | No vector input or dimension, norm or coordinate-domain checks. |
| Probability | Unsupported | Scalar fractions are evaluable. No combinatorics, distribution or probability-domain verifier. |
| Statistics | Unsupported | Scalar arithmetic is evaluable. No sample, population, missing-value or numerical stability policy. |
| Discrete math | Unsupported | No combinatorial, modular, logical or graph operations. No class-specific verifier. |
| Complex numbers | Unsupported | The prototype uses real rationals. Negative square-root arguments are domain errors. No complex branch conventions. |
| Numerical methods | Unsupported | No iterative, approximate or tolerance-based solvers. No convergence verifier. |
| Differential equations | Unsupported | No derivatives or initial/boundary conditions. No residual verifier for differential equations. |

## Expression recognition

The [labelled image corpus](math-ocr-corpus/labels.csv) contains five printed and four synthetic pen fixtures. The synthetic pen images use hand-designed vector glyph paths and contain no human writing samples. Regenerate them on Windows with `tools/benchmarks/new-math-ocr-corpus.ps1`.

The Windows baseline used Tesseract 5.2.0 with pinned English `tessdata_fast` and single-block segmentation. Exact match removes whitespace and normalizes recognized multiplication, division, minus and radical symbols. Scoring preserves the structure of the output. Warm latency is the median of three recognitions per image and begins after loading the image and engine.

| Fixture class | Exact matches | Range of warm medians |
| --- | ---: | ---: |
| Printed | 0 / 5 | 2.27–8.75 ms |
| Synthetic pen | 1 / 4 | 3.06–6.06 ms |

Recognition errors included a multiplication sign read as `x`, an omitted exponent, and a radical read as a letter. Stacked fractions failed as a full expression. These measurements describe the Windows baseline and this corpus. [Raw recognition results](2026-11-24-tesseract-math-baseline.jsonl)

### Android conventional OCR

On 2026-11-25, the opt-in Tesseract4Android 4.9.0 instrumentation suite used the same nine images and English weights. Both APK manifests lacked internet permission. The API 29 emulator ran in airplane mode with Wi-Fi and cellular data disabled. The Samsung tablet kept its existing connectivity settings.

| Device | Printed matches | Synthetic pen matches | Printed warm medians | Synthetic pen warm medians |
| --- | ---: | ---: | ---: | ---: |
| Samsung SM-X806B, ARM64, API 36 | 0 / 5 | 1 / 4 | 3.31–11.79 ms | 4.32–7.99 ms |
| x86-64 emulator, API 29 | 0 / 5 | 1 / 4 | 3.03–10.62 ms | 3.36–10.14 ms |

The test decoded each image before timing. It measured recognition and image handoff once, then reported the median of three repeats. Every normalized output was stable within its run. Both Android targets matched the Windows baseline's exact-match counts. Conventional OCR remains unsuitable for unattended formula transcription on this corpus. [Tablet results](2026-11-25-tesseract-math-android-tablet.jsonl) · [Emulator results](2026-11-25-tesseract-math-android-api29.jsonl)

The ordinary `:check`, debug build, release build and Android test APK build passed with the experiment disabled. Inspection confirmed that all three ordinary APKs exclude the Tesseract native library and the trained data.

To repeat the test, stage the [Tesseract4Android 4.9.0 AAR](https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar) at `.gradle-local/kan30-tesseract-android/tesseract4android-4.9.0.aar` and the [pinned English trained data](https://github.com/tesseract-ocr/tessdata_fast/blob/923915d4ced2a7235221785785a29c4a42d4a/eng.traineddata) at `.gradle-local/math-ocr/tessdata/eng.traineddata`. Gradle checks the AAR checksum and the device test checks the data checksum before native initialization. This configuration adds both artifacts only to the opt-in instrumentation APK.

```powershell
.\gradlew.bat -PmathOcrSpike=true :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.omninumeris.app.TesseractMathBenchmarkTest com.omninumeris.app.test/androidx.test.runner.AndroidJUnitRunner
```

### Dedicated formula recognition

On 2026-11-25, pinned PP-FormulaNet-S ran locally on Windows with an AMD Ryzen 7 7735HS, four CPU threads and MKL-DNN disabled. PaddleOCR 3.3.2, PaddlePaddle 3.2.2 and PaddleX 3.3.13 loaded files from the verified local model directory with model-source checks disabled. Each fixture ran once to warm up, then three times. Timings include image reading, preprocessing, decoding and postprocessing. They exclude model initialization.

| Fixture class | Exact structural matches | Range of warm medians |
| --- | ---: | ---: |
| Printed | 4 / 5 | 605.32–753.75 ms |
| Synthetic pen | 2 / 4 | 586.84–652.08 ms |

All nine outputs were stable across the measured repetitions and reproduced under restricted network access. A separate check with corrupted model metadata rejected the fixture before inference. Manual scoring accepted structurally equivalent LaTeX for multiplication, paired fractions, a fraction inside a root and a braced exponent. It preserved letter case and operators. P05 produced a malformed equation, H03 changed lowercase `x` to uppercase `X`, and H04 replaced exponentiation with `\wedge`. These failures require correction before the solver sees the expression. Human handwriting accuracy and Android performance remain unmeasured. [Raw formula results](2026-11-25-formulanet-math-baseline.jsonl)

To repeat the run, use Python 3.12 with `paddlepaddle==3.2.2`, `paddleocr==3.3.2`, `paddlex==3.3.13`, `tokenizers==0.22.2` and `ftfy==6.3.1`. Place the model's `config.json`, `inference.json`, `inference.yml` and `inference.pdiparams` from the pinned publisher revision into a local directory. The runner checks all four SHA-256 digests before importing the inference engine. [Pinned PP-FormulaNet-S](https://huggingface.co/PaddlePaddle/PP-FormulaNet-S/tree/0572450e501be9eb1b1cdb7e00fccf4b22fab4df)

The Paddle2ONNX 2.1.0 Windows wheel failed to load its native converter on CPython 3.12 with PaddlePaddle 3.2.2, 3.3.1 and the official CPU development build `3.4.0.dev20260407`. Windows reported that a required procedure could not be found. None of the attempts exported an ONNX artifact. A reproducible exporter and Android parity and latency measurements remain open. [PaddleOCR ONNX conversion guide](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/others/obtaining_onnx_models.html)

Inspecting the Windows wheel import table against the installed development build found 110 of 115 named `libpaddle.pyd` imports missing, including Paddle IR operators used by the converter. All 35 named imports from Paddle's `common.dll` matched. This confirms a binary compatibility issue in the tested pair before it reads the model. [Upstream Windows build and linkage changes](https://github.com/PaddlePaddle/Paddle2ONNX/pull/1561)

### Human handwriting smoke test

Eight cropped samples from the Samsung SM-X806B canvas had labels confirmed by the writer or defined by the writing prompts. The Windows PP-FormulaNet-S runner produced the same result across all three measured runs per sample. Scoring allowed equivalent LaTeX for multiplication and superscripts and preserved malformed commands, decimal points, digits and division structure.

| Expected expression | Raw recognizer output | Exact match | Warm median |
| --- | --- | --- | ---: |
| `100 * 40` | `60 \times 80` | No | 630.42 ms |
| `100 * 10` | `(000times10` | No | 518.31 ms |
| `60 * 30 + 9` | `60 \times 30+9` | Yes | 532.16 ms |
| `40 / 3` written as long division | `3sqrt44.:` | No | 470.30 ms |
| `1/2 + 1/3` with diagonal fraction bars | `y_{2}+y_{3}` | No | 665.23 ms |
| `sqrt(49)` | `\sqrt{4.9}` | No | 589.30 ms |
| `2^3` | `2^{3}` | Yes | 575.88 ms |
| `x + 3 = 7` | `x+3=7` | Yes | 578.94 ms |

The first eight samples scored 3 / 8. The initial four prompted samples scored 2 / 4. The Windows Tesseract baseline matched 0 / 4 on those same prompted crops.

The writer repeated the four prompts with the Fine tip and Stylus only selected. The second pass used horizontal fraction bars and matched 3 / 4. The model read the stacked fractions, superscript and equation correctly. It returned malformed LaTeX for the square root. The writer confirmed the small mark beside the equation was intentional. The recognizer returned the same equation with that mark included or cropped out. The alternate crop is excluded from the score.

Combined exact-expression score on Windows was 6 / 12. The captures represent one writer and include repeat expressions. This measures recognition before solving. Cropped images remain in the ignored local test directory.

### Dedicated formula recognition on Android

On 2026-11-25, the published Pix2Text MFR ONNX encoder and decoder ran in the opt-in Android instrumentation APK using ONNX Runtime Android 1.24.1. The tablet used ARM64 CPU inference with four threads. The Android 10 x86-64 emulator ran in airplane mode with Wi-Fi and data disabled. Neither APK declared Internet permission.

| Device | Fixture class | Exact structural matches | Range of warm medians |
| --- | --- | ---: | ---: |
| Samsung SM-X806B, API 36 | Printed | 5 / 5 | 373.33–609.91 ms |
| Samsung SM-X806B, API 36 | Synthetic pen | 3 / 4 | 418.11–1,658.63 ms |
| Samsung SM-X806B, API 36 | Human tablet ink | 6 / 12 | 424.12–683.97 ms |
| Android 10 emulator | Printed | 5 / 5 | 251.16–390.33 ms |
| Android 10 emulator | Synthetic pen | 3 / 4 | 257.94–957.49 ms |

The Android test verified the three model and tokenizer hashes before loading. It decoded each fixture before measuring one warm-up and three timed CPU recognitions, then reported the median. Timings include Android bilinear resizing to 384 by 384, normalization, encoder and bounded greedy decoder execution, and token decoding. The decoder stopped after 64 generated tokens if no end token appeared. The synthetic power case hit that limit on both targets and scored as a miss. All outputs were stable in these runs.

The tablet sessions initialized in 302.46 ms after file staging. Observed process PSS checkpoints were 73,685 KiB before session creation, 206,610 KiB just after loading and 265,666 KiB at the end. These are process snapshots. A smaller physical device, peak memory and sustained thermal behavior remain unmeasured.

Scoring accepted equivalent font directives and layout whitespace while preserving mathematical operators, digit order, case and trailing punctuation. The Android candidate read the Fine-tip square-root sample that PP-FormulaNet-S missed and added punctuation to the equation with a nearby intentional mark. Both candidates scored 6 / 12 on the same human crops. This sample is from one writer with repeated expressions, so it does not establish general handwriting accuracy or production readiness. [Tablet printed and synthetic results](2026-11-25-pix2text-android-tablet.jsonl) · [Emulator results](2026-11-25-pix2text-android-emulator.jsonl)

For reproduction, stage `encoder_model.onnx`, `decoder_model.onnx` and `tokenizer.json` from the pinned MIT-licensed publisher revision under `.gradle-local/kan30-pix2text/assets/pix2text/`. Stage `onnxruntime-android-1.24.1.aar` under `.gradle-local/kan30-pix2text/`. The build checks the AAR digest. The ordinary build excludes the runtime and weights. The standard benchmark uses the checked-in printed and synthetic fixtures. [Pinned Pix2Text model](https://huggingface.co/breezedeus/pix2text-mfr/tree/bea257edb2653f2ae413b084f2ac0e8299d08df0) · [ONNX Runtime Android artifact](https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.24.1/)

```powershell
.\gradlew.bat -PformulaAndroidSpike=true :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.omninumeris.app.Pix2TextAndroidBenchmarkTest com.omninumeris.app.test/androidx.test.runner.AndroidJUnitRunner
```

```powershell
python tools/benchmarks/run-formulanet-baseline.py docs/benchmarks/math-ocr-corpus <model-directory> <output-jsonl>
```

### Candidate comparison

| Candidate | Intended input | Evidence and dependency status |
| --- | --- | --- |
| Tesseract / Tesseract4Android | Conventional image text | Windows, Android ARM64 and Android API 29 x86-64 benchmarks completed. Wrapper and English data publish Apache-2.0 licenses. Bundled Tesseract, Leptonica, JPEG and PNG libraries need a notice audit before redistribution. [Android wrapper](https://github.com/adaptech-cz/Tesseract4Android) |
| ML Kit bundled text OCR | Image text | On-device candidate. Formula accuracy and applicable SDK terms require evaluation. [Android OCR options](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) |
| ML Kit Digital Ink | Ordered pen strokes | Model setup and offline recognition are documented. Formula geometry, accuracy and SDK terms require evaluation. [Digital Ink guide](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android) |
| PP-FormulaNet-S | Printed and handwritten formula images | Host evaluation above. Publisher declares Apache-2.0. The weights are roughly 232 MB. Android operator/runtime compatibility, device measurements and release notices need review. [Publisher model card](https://huggingface.co/PaddlePaddle/PP-FormulaNet-S) |
| Pix2Text MFR / ONNX Runtime | Cropped formula images | Publisher lists MIT for the weights. ONNX Runtime publishes under MIT. Android CPU execution demonstrated. Printed results support further investigation. Handwriting errors and the decoder limit require editable review and an unsupported state before solver handoff. [Publisher model card](https://huggingface.co/breezedeus/pix2text-mfr) · [ONNX Runtime license](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) |
| UniMERNet | Printed and handwritten formula images | Dedicated 2D candidate. Android inference, weights and evaluation data permissions remain open. [Research repository](https://github.com/opendatalab/UniMERNet) |

Use consented handwritten fixtures or a dataset with approved terms for human handwriting measurements. The public MathWriting ink data is published under a noncommercial share-alike license. [Dataset license](https://github.com/google-research/google-research/blob/master/mathwriting/README.md)

To repeat the host baseline, obtain the pinned `eng.traineddata` in a local directory and pass the corpus and data directories to the standalone runner. The runner checks SHA-256 before loading the data.

```powershell
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_GENERATE_ASPNET_CERTIFICATE = 'false'
dotnet run --project tools/benchmarks/math-ocr-baseline/MathOcrBaseline.csproj --configuration Release --artifacts-path .gradle-local/math-ocr/artifacts -- docs/benchmarks/math-ocr-corpus .gradle-local/math-ocr/tessdata
```

[Pinned English model file](https://github.com/tesseract-ocr/tessdata_fast/blob/923915d4ced2a7235221788285785a29c4a42d4a/eng.traineddata)
