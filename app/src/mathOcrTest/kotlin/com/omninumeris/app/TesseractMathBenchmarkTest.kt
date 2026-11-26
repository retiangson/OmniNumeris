package com.omninumeris.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Test

class TesseractMathBenchmarkTest {
    @Test
    fun measuresLabeledImagesWithoutSolving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val cache = instrumentation.targetContext.cacheDir.toPath()
        val benchmarkRoot = Files.createTempDirectory(cache, "math-ocr-").toFile()
        try {
            val data = File(benchmarkRoot, "tessdata")
            check(data.mkdir()) { "Cannot create private OCR staging directory" }
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(context.assets.open("eng.traineddata"), digest).use { input ->
                File(data, "eng.traineddata").outputStream().use { input.copyTo(it) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2") {
                "English trained data SHA-256 mismatch"
            }
            val fixtures = context.assets.open("labels.csv").bufferedReader().use { it.readLines().drop(1) }
            check(fixtures.isNotEmpty()) { "No OCR labels" }
            val engine = TessBaseAPI()
            try {
                check(engine.init(benchmarkRoot.path, "eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                    "Cannot initialize Tesseract with pinned English data"
                }
                engine.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                for (line in fixtures) {
                    val columns = line.split(',')
                    require(columns.size == 5 && columns[1].matches(Regex("[A-Z][0-9]{2}\\.png"))) {
                        "Invalid OCR fixture label row"
                    }
                    val (id, filename, source, expected) = columns
                    val bitmap = context.assets.open(filename).use { requireNotNull(BitmapFactory.decodeStream(it)) }
                    try {
                        val first = recognize(engine, bitmap)
                        val warm = List(3) { recognize(engine, bitmap) }
                        val normalized = canonicalize(first.text)
                        val record = JSONObject()
                            .put("id", id)
                            .put("source", source)
                            .put("expected", expected)
                            .put("raw", first.text.trim())
                            .put("normalized", normalized)
                            .put("exact", normalized == expected)
                            .put("stable", warm.all { canonicalize(it.text) == normalized })
                            .put("firstMs", first.elapsedMs)
                            .put("warmMedianMs", warm.map(Recognition::elapsedMs).sorted()[1])
                        instrumentation.sendStatus(0, Bundle().apply { putString("mathOcrResult", record.toString()) })
                    } finally {
                        bitmap.recycle()
                    }
                }
            } finally {
                engine.recycle()
            }
        } finally {
            benchmarkRoot.deleteRecursively()
        }
    }

    private fun recognize(engine: TessBaseAPI, bitmap: Bitmap): Recognition {
        val start = SystemClock.elapsedRealtimeNanos()
        engine.setImage(bitmap)
        val text = engine.getUTF8Text().orEmpty()
        val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        engine.clear()
        return Recognition(text, elapsed)
    }

    private fun canonicalize(text: String): String = text.filterNot(Char::isWhitespace)
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace("√", "sqrt")

    private data class Recognition(val text: String, val elapsedMs: Double)
}
