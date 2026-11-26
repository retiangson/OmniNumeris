package com.omninumeris.inferencespike

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

private const val MEMORY_RESERVE_BYTES = 512L * 1024 * 1024

internal fun hasMemoryForBenchmark(available: Long, threshold: Long, isLowMemory: Boolean): Boolean =
    !isLowMemory && available >= threshold && available - threshold >= MEMORY_RESERVE_BYTES

internal enum class StopCause { Requested, LowMemory, Thermal, MonitoringUnavailable }

internal data class BenchmarkResult(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val thermalBefore: Int,
    val thermalAfter: Int,
    val sampledPeakRssKiB: Long,
    val processHighWaterKiB: Long,
    val promptTokens: Long,
    val outputTokens: Long,
    val firstTokenNanos: Long?,
    val tokensPerSecond: Double?,
    val stopCause: StopCause?,
    val cancellationNanos: Long?,
)

internal class InferenceBenchmark(private val context: Context) {
    private val activity = requireNotNull(context.getSystemService(ActivityManager::class.java))
    private val power = requireNotNull(context.getSystemService(PowerManager::class.java))

    fun run(profile: BenchmarkProfile, prompt: BenchmarkPrompt, cancelAfterMs: Long?): BenchmarkResult {
        require(cancelAfterMs == null || cancelAfterMs in 0..5_000)
        check(!runLock.isHeldByCurrentThread && runLock.tryLock()) { "An inference benchmark is already running" }
        try {
            val model = File(context.noBackupFilesDir, BenchmarkModel.FILE_NAME)
            verifyResources()
            BenchmarkModel.verify(model)
            val thermalBefore = power.currentThermalStatus
            val memory = verifyResources()
            val peakRss = AtomicLong(readRssKiB("VmRSS"))
            val stopCause = AtomicReference<StopCause>()
            val cancelStart = AtomicLong()

            return NativeCancellation().use { cancellation ->
                fun stop(cause: StopCause) {
                    if (stopCause.compareAndSet(null, cause)) {
                        cancelStart.set(SystemClock.elapsedRealtimeNanos())
                        cancellation.cancel()
                    }
                }

                val monitor = Executors.newSingleThreadScheduledExecutor()
                try {
                    monitor.scheduleWithFixedDelay({
                        try {
                            peakRss.accumulateAndGet(readRssKiB("VmRSS"), ::maxOf)
                            if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) stop(StopCause.Thermal)
                            val current = readMemory()
                            if (!hasMemoryForBenchmark(current.availMem, current.threshold, current.lowMemory)) {
                                stop(StopCause.LowMemory)
                            }
                        } catch (_: Exception) {
                            stop(StopCause.MonitoringUnavailable)
                        }
                    }, 0, 100, MILLISECONDS)
                    if (cancelAfterMs != null) {
                        monitor.schedule({ stop(StopCause.Requested) }, cancelAfterMs, MILLISECONDS)
                    }
                    val output = NativeInference.generate(
                        model.absolutePath, prompt.modelInput, profile.context, profile.maxTokens, profile.threads,
                        cancellation.handle,
                    )
                    val nativeFinished = SystemClock.elapsedRealtimeNanos()
                    val cancelled = output[4] == 1L
                    BenchmarkResult(
                        memory.totalMem, memory.availMem, thermalBefore, power.currentThermalStatus,
                        maxOf(peakRss.get(), readRssKiB("VmRSS")), readRssKiB("VmHWM"), output[0], output[1],
                        output[2].takeIf { it > 0 }, outputTokensPerSecond(output[1], output[3]),
                        stopCause.get().takeIf { cancelled },
                        cancelStart.get().takeIf { cancelled && it > 0 }?.let { nativeFinished - it },
                    )
                } finally {
                    monitor.shutdownNow()
                }
            }
        } finally {
            runLock.unlock()
        }
    }

    private fun readMemory() = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)

    private fun verifyResources(): ActivityManager.MemoryInfo {
        check(power.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE) { "Benchmark deferred for thermal pressure" }
        return readMemory().also { memory ->
            check(hasMemoryForBenchmark(memory.availMem, memory.threshold, memory.lowMemory)) {
                "Benchmark deferred for insufficient available memory"
            }
        }
    }

    private fun readRssKiB(field: String): Long = File("/proc/self/status").useLines { lines ->
        lines.firstOrNull { it.startsWith("$field:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
            ?: error("Process RSS field is unavailable: $field")
    }

    companion object {
        private val runLock = ReentrantLock()
    }
}
