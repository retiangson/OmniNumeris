# OW-002 local inference benchmark

Use a physical ARM64 device with 3 GB of RAM for the reference result. Record its model, Android API, RAM visible to the OS, available memory and thermal state. Repeat the matrix on each additional device and label those runs by their hardware. Keep the device at a stable ambient temperature and disconnect the charger when possible.

## Build and stage

Install Android NDK 27.2.12479018 and CMake 3.22.1 alongside the SDK in the [build instructions](../../README.md). Build the benchmark application and its instrumentation APK on Windows.

```powershell
.\gradlew.bat -PinferenceSpike=true :inference-spike:assembleDebug :inference-spike:assembleDebugAndroidTest
adb install -r inference-spike/build/outputs/apk/debug/inference-spike-debug.apk
adb install -r -t inference-spike/build/outputs/apk/androidTest/debug/inference-spike-debug-androidTest.apk
```

An existing copy of the pinned source archive can be supplied with `'-PllamaArchive=path/to/llama.cpp.tar.gz'`. CMake checks the same SHA-256 for either source.

Fetch the pinned model revision from the publisher onto the host. Verify its SHA-256 before pushing it to the device. The benchmark verifies the same digest before each run. [Pinned benchmark model](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/6dd44a1/qwen2.5-0.5b-instruct-q4_k_m.gguf)

```powershell
$model = 'qwen2.5-0.5b-instruct-q4_k_m.gguf'
$expectedHash = '74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db'
if ((Get-FileHash -LiteralPath $model).Hash -ne $expectedHash) { throw 'Model checksum mismatch' }
adb push $model "/data/local/tmp/$model"
adb shell "run-as com.omninumeris.inferencespike mkdir -p no_backup"
adb shell "cat /data/local/tmp/$model | run-as com.omninumeris.inferencespike sh -c 'cat > no_backup/$model'"
adb shell "rm -f /data/local/tmp/$model"
```

## Run the matrix

Enable airplane mode for the reference runs. Use a fresh instrumentation invocation for each process-cold sample and `-e runs 5` for repeated samples in one process. Specify `Explanation`, `Summary` or `Outline` with `-e prompt`. Use `-e cancelAfterMs 100` for a cancellation sample, then adjust the delay to exercise cancellation during generation. Capture the instrumentation output in a log file.

```powershell
adb shell am instrument -w -r -e prompt Explanation -e context 256 -e threads 2 -e maxTokens 64 com.omninumeris.inferencespike.test/androidx.test.runner.AndroidJUnitRunner
```

Each `benchmark` record reports the device, payload configuration, sampled peak RSS, process RSS high-water mark, first-token latency, decode throughput, thermal status and any cancellation result. Sampled peak RSS uses 100 ms samples. The high-water mark includes earlier work in the same process. First-token latency starts after checksum verification and includes model/context loading and prompt decode. Checksum verification may warm the filesystem cache before a process-cold measurement. Compute decode throughput after the first token. Cancellation latency measures the interval from the cancellation request to native return. Throughput and latency fields are absent when the run has insufficient tokens.

Record unavailable device, resource deferrals and failed runs explicitly. Require measured 3 GB results before selecting a supported device profile. The Samsung API 36 tablet used for OW-001 reports about 7.1 GiB of RAM to Android and is suitable for a prototype smoke test.

## Samsung tablet pilot

On 2026-11-24, the SM-X806B ran the optimized ARM64 build using API 36. The model checksum was verified before every run. Three `Outline` runs in the same process used two threads, a 256-token context and a 64-token output cap.

| Run | First token | Decode rate | Process RSS high-water mark |
| --- | ---: | ---: | ---: |
| 1 | 698 ms | 28.59 tokens/s | 615 MiB |
| 2 | 727 ms | 27.30 tokens/s | 620 MiB |
| 3 | 766 ms | 26.55 tokens/s | 622 MiB |

The loading cancellation returned in 147 ms from the request. The generation cancellation returned in 58 ms after producing 10 tokens. Android reported thermal state 0 before and after each run. Airplane mode stayed off, and APK inspection confirmed the benchmark app requests no Internet permission. The temporary benchmark apps and staged fixture were removed from the tablet. [Raw metrics](2026-11-24-samsung-tablet-pilot.jsonl)

The pilot validates the harness on this device. Thermal deferral under sustained stress and the reference profile on physical 3 GB hardware remain unverified.
