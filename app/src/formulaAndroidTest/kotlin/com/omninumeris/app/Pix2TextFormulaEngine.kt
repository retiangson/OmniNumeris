package com.omninumeris.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import org.json.JSONObject

internal class Pix2TextFormulaEngine(
    private val environment: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    tokenizer: String,
) {
    private val vocabulary = parseVocabulary(tokenizer)
    private val tokenBytes = buildByteDecoder()

    fun recognize(image: Bitmap): FormulaPrediction {
        val pixels = FloatBuffer.wrap(rgbPlanes(image))
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        return OnnxTensor.createTensor(environment, pixels, shape).use { imageTensor ->
            encoder.run(mapOf("pixel_values" to imageTensor)).use { encoded ->
                generate(encoded[0] as OnnxTensor)
            }
        }
    }

    private fun generate(encoderState: OnnxTensor): FormulaPrediction {
        val tokens = LongArray(MAX_NEW_TOKENS + 1)
        // The published generation config uses EOS as the initial decoder token.
        tokens[0] = END_TOKEN.toLong()
        for (length in 1..MAX_NEW_TOKENS) {
            val input = LongBuffer.wrap(tokens, 0, length)
            val next = OnnxTensor.createTensor(environment, input, longArrayOf(1, length.toLong())).use { prefix ->
                decoder.run(mapOf("input_ids" to prefix, "encoder_hidden_states" to encoderState)).use { result ->
                    val logits = (result[0] as OnnxTensor).floatBuffer
                    require(logits.remaining() == length * VOCABULARY_SIZE) { "Unexpected decoder logits shape" }
                    mostLikelyToken(logits, (length - 1) * VOCABULARY_SIZE)
                }
            }
            if (next == END_TOKEN) return FormulaPrediction(decode(tokens, length), complete = true)
            tokens[length] = next.toLong()
        }
        return FormulaPrediction(decode(tokens, tokens.size), complete = false)
    }

    private fun mostLikelyToken(logits: FloatBuffer, start: Int): Int {
        var selected = -1
        var largest = Float.NEGATIVE_INFINITY
        for (id in 0 until VOCABULARY_SIZE) {
            val probability = logits[start + id]
            if (probability > largest) {
                largest = probability
                selected = id
            }
        }
        require(selected >= 0 && largest.isFinite()) { "Decoder returned no finite token score" }
        return selected
    }

    private fun decode(tokens: LongArray, length: Int): String {
        val bytes = ByteArrayOutputStream()
        for (index in 1 until length) {
            val id = tokens[index].toInt()
            if (id in 0..LAST_SPECIAL_TOKEN) continue
            for (character in vocabulary[id]) {
                bytes.write(requireNotNull(tokenBytes[character]) { "Unknown byte-level token character" })
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun rgbPlanes(image: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(image, IMAGE_SIZE, IMAGE_SIZE, true)
        try {
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            val output = FloatArray(3 * pixels.size)
            for (channel in 0..2) {
                val shift = (2 - channel) * 8
                for (index in pixels.indices) {
                    val value = (pixels[index] shr shift) and 0xff
                    output[channel * pixels.size + index] = value / 127.5f - 1f
                }
            }
            return output
        } finally {
            if (resized !== image) resized.recycle()
        }
    }

    private fun parseVocabulary(tokenizer: String): Array<String> {
        val root = JSONObject(tokenizer)
        require(root.getJSONObject("decoder").getString("type") == "ByteLevel")
        val vocab = root.getJSONObject("model").getJSONObject("vocab")
        require(vocab.length() == VOCABULARY_SIZE)
        val result = Array(VOCABULARY_SIZE) { "" }
        for (token in vocab.keys()) {
            val index = vocab.getInt(token)
            require(index in result.indices && result[index].isEmpty() && token.isNotEmpty())
            result[index] = token
        }
        return result
    }

    private fun buildByteDecoder(): Map<Char, Int> {
        var nextMappedCodePoint = 256
        return (0..255).associateBy { byte ->
            if (byte in 33..126 || byte in 161..172 || byte in 174..255) byte.toChar()
            else (nextMappedCodePoint++).toChar()
        }
    }

    companion object {
        private const val IMAGE_SIZE = 384
        private const val VOCABULARY_SIZE = 1_200
        private const val END_TOKEN = 2
        private const val LAST_SPECIAL_TOKEN = 4
        private const val MAX_NEW_TOKENS = 64
    }
}

internal data class FormulaPrediction(val latex: String, val complete: Boolean)
