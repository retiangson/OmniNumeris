package com.omninumeris.inferencespike

import java.io.File
import java.security.MessageDigest

internal data class BenchmarkProfile(
    val context: Int = 256,
    val maxTokens: Int = 64,
    val threads: Int = 2,
) {
    init {
        require(context in 128..512 && maxTokens in 1..128 && maxTokens < context && threads in 1..4) {
            "Expected 128–512 context tokens, 1–128 output tokens below the context limit, and 1–4 CPU threads"
        }
    }
}

internal enum class BenchmarkPrompt(val question: String) {
    Explanation("Explain in a short paragraph how to divide a collection into equal groups."),
    Summary("Summarize the following note in two sentences. The library is open from Monday to Friday. " +
        "Readers may borrow up to four books and return them using the drop box."),
    Outline("List six short steps for organizing a study session around a textbook chapter.");

    val modelInput: String
        get() = "<|im_start|>user\n$question<|im_end|>\n<|im_start|>assistant\n"
}

internal object BenchmarkModel {
    const val FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    const val SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    private const val SIZE_BYTES = 491_400_032L

    fun verify(file: File) {
        require(file.isFile) { "Stage the pinned GGUF file in the benchmark app's no-backup directory" }
        require(file.length() == SIZE_BYTES) { "Benchmark model size mismatch" }
        require(sha256(file) == SHA256) { "Benchmark model SHA-256 mismatch" }
    }
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val chunk = ByteArray(128 * 1024)
        while (true) {
            val size = input.read(chunk)
            if (size < 0) break
            digest.update(chunk, 0, size)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun outputTokensPerSecond(tokens: Long, afterFirstNanos: Long): Double? =
    if (tokens > 1 && afterFirstNanos > 0) (tokens - 1) * 1_000_000_000.0 / afterFirstNanos else null
