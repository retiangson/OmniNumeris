package com.omninumeris.inferencespike

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceBenchmarkTest {
    @Test
    fun measurePinnedModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val options = InstrumentationRegistry.getArguments()
        val profile = BenchmarkProfile(
            options.getString("context")?.toInt() ?: 256,
            options.getString("maxTokens")?.toInt() ?: 64,
            options.getString("threads")?.toInt() ?: 2,
        )
        val prompt = options.getString("prompt")?.let(BenchmarkPrompt::valueOf) ?: BenchmarkPrompt.Explanation
        val runs = options.getString("runs")?.toInt() ?: 1
        val cancelAfter = options.getString("cancelAfterMs")?.toLong()
        require(runs in 1..5) { "Set runs between 1 and 5" }
        val airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        val benchmark = InferenceBenchmark(context)
        repeat(runs) { index ->
            val result = benchmark.run(profile, prompt, cancelAfter)
            val record = JSONObject()
                .put("device", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.first())
                .put("modelSha256", BenchmarkModel.SHA256)
                .put("prompt", prompt.name)
                .put("run", index + 1)
                .put("context", profile.context)
                .put("threads", profile.threads)
                .put("maxTokens", profile.maxTokens)
                .put("airplaneMode", airplaneMode)
                .put("totalRamBytes", result.totalRamBytes)
                .put("availableRamBytes", result.availableRamBytes)
                .put("thermalBefore", result.thermalBefore)
                .put("thermalAfter", result.thermalAfter)
                .put("sampledPeakRssKiB", result.sampledPeakRssKiB)
                .put("processHighWaterKiB", result.processHighWaterKiB)
                .put("promptTokens", result.promptTokens)
                .put("outputTokens", result.outputTokens)
                .put("firstTokenNanos", result.firstTokenNanos ?: JSONObject.NULL)
                .put("tokensPerSecond", result.tokensPerSecond ?: JSONObject.NULL)
                .put("stopCause", result.stopCause?.name ?: JSONObject.NULL)
                .put("cancellationNanos", result.cancellationNanos ?: JSONObject.NULL)
            instrumentation.sendStatus(2, Bundle().apply { putString("benchmark", record.toString()) })
            if (cancelAfter == null) {
                assertNull(result.stopCause)
                assertTrue("Expected a token with measured first-token latency", result.firstTokenNanos != null)
            } else {
                assertEquals(StopCause.Requested, result.stopCause)
                assertTrue("Expected measured cancellation latency", result.cancellationNanos != null)
            }
        }
    }
}
