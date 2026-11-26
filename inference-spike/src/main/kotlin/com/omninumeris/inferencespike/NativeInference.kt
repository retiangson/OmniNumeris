package com.omninumeris.inferencespike

internal object NativeInference {
    init {
        System.loadLibrary("omninumeris_inference_spike")
    }

    external fun createCancellation(): Long
    external fun cancel(handle: Long)
    external fun destroyCancellation(handle: Long)

    external fun generate(
        modelPath: String,
        prompt: String,
        context: Int,
        maxTokens: Int,
        threads: Int,
        cancellation: Long,
    ): LongArray
}

internal class NativeCancellation : AutoCloseable {
    var handle = NativeInference.createCancellation()
        private set

    @Synchronized
    fun cancel() {
        if (handle != 0L) NativeInference.cancel(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) NativeInference.destroyCancellation(handle)
        handle = 0
    }
}
