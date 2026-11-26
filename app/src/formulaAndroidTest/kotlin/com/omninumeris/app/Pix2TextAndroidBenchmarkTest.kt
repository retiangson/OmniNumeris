package com.omninumeris.app

import android.content.Intent
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.app.math.MathCorrectionCheck
import com.omninumeris.app.math.MathInputReview
import com.omninumeris.app.math.MathInputViewModel
import com.omninumeris.app.math.MathReviewSource
import com.omninumeris.app.math.MathSourceReviewSession
import com.omninumeris.app.math.MathSourceRevocation
import com.omninumeris.app.math.RecognitionReviewDialog
import com.omninumeris.application.MathInputProblem
import com.omninumeris.application.MathRecognitionAdapter
import com.omninumeris.application.MathRecognitionResult
import com.omninumeris.application.ResolveMathInput
import com.omninumeris.application.MathSolutionHistory
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathPageRegion
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathSourceRevision
import java.io.File
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Pix2TextAndroidBenchmarkTest {
    @Test
    fun recognizesFormulaCropsLocally() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val cache = instrumentation.targetContext.cacheDir
        val root = Files.createTempDirectory(cache.toPath(), "formula-ocr-").toFile()
        try {
            val encoder = assets.stageVerified(root, "encoder_model.onnx",
                "bd8d5c322792e9ec45793af5569e9748f82a3d728a9e00213dbfc56c1486f37d")
            val decoder = assets.stageVerified(root, "decoder_model.onnx",
                "fd0f92d7a012f3dae41e1ac79421aea0ea888b5a66cb3f9a004e424f82f3daed")
            val tokenizer = assets.stageVerified(root, "tokenizer.json",
                "3e2ab757277d22639bec28c9d7972e352d3d1dba223051fa674002dc5ab64df3")
            val environment = OrtEnvironment.getEnvironment()
            val includeHuman = InstrumentationRegistry.getArguments().getString("includeHumanOcrFixtures") == "true"
            val checkMathInputReview = InstrumentationRegistry.getArguments().getString("verifyMathInputReview") == "true"
            val checkCorrectionUi = InstrumentationRegistry.getArguments().getString("verifyMathCorrectionUi") == "true"
            val fixtures = assets.readFixtures("") + if (includeHuman) assets.readFixtures("human/") else emptyList()
            val setupStart = SystemClock.elapsedRealtimeNanos()
            val basePss = Debug.getPss()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(4)
                options.setInterOpNumThreads(1)
                environment.createSession(encoder.path, options).use { encoderSession ->
                    environment.createSession(decoder.path, options).use { decoderSession ->
                        val recognizer = Pix2TextFormulaEngine(environment, encoderSession, decoderSession,
                            tokenizer.readText(Charsets.UTF_8))
                        val setup = JSONObject()
                            .put("setupMs", elapsedMs(setupStart))
                            .put("basePssKiB", basePss)
                            .put("loadedPssKiB", Debug.getPss())
                        instrumentation.sendStatus(0, Bundle().apply { putString("formulaSetup", setup.toString()) })
                        for (fixture in fixtures) {
                            val image = assets.open(fixture.asset).use { requireNotNull(BitmapFactory.decodeStream(it)) }
                            try {
                                val first = recognizer.recognize(image)
                                val times = ArrayList<Double>(3)
                                var stable = true
                                repeat(3) {
                                    val start = SystemClock.elapsedRealtimeNanos()
                                    val prediction = recognizer.recognize(image)
                                    times.add(elapsedMs(start))
                                    stable = stable && prediction == first
                                }
                                val record = JSONObject()
                                    .put("id", fixture.id)
                                    .put("source", fixture.source)
                                    .put("expected", fixture.expected)
                                    .put("latex", first.latex)
                                    .put("complete", first.complete)
                                    .put("stable", stable)
                                    .put("warmMedianMs", times.sorted()[1])
                                instrumentation.sendStatus(0, Bundle().apply { putString("formulaResult", record.toString()) })
                                val showSource = checkCorrectionUi && fixture.id == "P04"
                                if (showSource || (checkMathInputReview && fixture.id in setOf("P02", "P03", "H02", "H04"))) {
                                    verifyInputReview(image, fixture, first, recognizer, showSource)
                                    instrumentation.sendStatus(0, Bundle().apply { putString("mathInputReview", fixture.id) })
                                }
                            } finally {
                                image.recycle()
                            }
                        }
                        instrumentation.sendStatus(0, Bundle().apply { putLong("formulaFinalPssKiB", Debug.getPss()) })
                    }
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun verifyInputReview(
        image: Bitmap,
        fixture: Fixture,
        prediction: FormulaPrediction,
        recognizer: Pix2TextFormulaEngine,
        showSource: Boolean,
    ) = runBlocking {
        val source = MathSourceAnchor.Capture(
            MathSourceRevision(UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID(),
            MathPageRegion(0.0, 0.0, 1.0, 1.0),
        )
        val adapter = object : MathRecognitionAdapter {
            override val source = MathInputSource.Camera
            override suspend fun recognize(anchor: MathSourceAnchor): MathRecognitionResult {
                val result = recognizer.recognize(image)
                assertEquals(prediction, result)
                return if (result.complete) MathRecognitionResult.Texts(listOf(result.latex))
                    else MathRecognitionResult.Rejected(MathInputProblem.Unsupported)
            }
        }
        val viewModel = MathInputViewModel(ResolveMathInput(setOf(adapter)), Dispatchers.Default, object : MathSolutionHistory {
            override suspend fun read(): MathSolutionHistoryRead = MathSolutionHistoryRead.Unavailable
            override suspend fun save(record: TypedSolutionRecord): MathSolutionSave = MathSolutionSave.Unavailable
        })
        val sourceImage = MathReviewSource.Image(image.asImageBitmap())
        val selection = MathSourceReviewSession(source)
        withContext(Dispatchers.Main) { viewModel.recognize(selection, sourceImage) }
        try {
            val result = withTimeout(15_000) { viewModel.review.first { it !is MathInputReview.Loading } }
            if (!prediction.complete) {
                assertEquals(MathInputReview.Failed(MathInputSource.Camera, MathInputProblem.Unsupported), result)
            } else {
                val editing = result as MathInputReview.Editing
                assertEquals(prediction.latex, editing.candidates.single().text)
                assertEquals(source, editing.candidates.single().anchor)
                assertEquals(sourceImage, editing.sourcePreview)
                assertEquals(MathCorrectionCheck.Unchecked, editing.check)
                if (showSource) assertOriginalImageAndEditableRecognition(viewModel, prediction.latex)
                withContext(Dispatchers.Main) {
                    viewModel.editCandidate(fixture.expected)
                    viewModel.reviewCandidate()
                }
                withTimeout(15_000) {
                    viewModel.review.filterIsInstance<MathInputReview.Editing>().first {
                        it.check is MathCorrectionCheck.Parsed || it.check is MathCorrectionCheck.Invalid
                    }
                }
                val reviewed = withContext(Dispatchers.Main) { viewModel.confirmCandidate() }
                assertNotNull(reviewed?.input)
                assertEquals(prediction.latex, reviewed?.candidate?.originalText)
                assertEquals(source, reviewed?.candidate?.anchor)
                selection.revoke(MathSourceRevocation.Locked)
                val revoked = withTimeout(5_000) { viewModel.review.filterIsInstance<MathInputReview.Failed>().first() }
                assertEquals(MathInputReview.Failed(MathInputSource.Camera, MathInputProblem.Locked), revoked)
            }
        } finally {
            withContext(Dispatchers.Main) { viewModel.dismissReview() }
        }
    }

    private fun assertOriginalImageAndEditableRecognition(viewModel: MathInputViewModel, rawText: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        val review by viewModel.review.collectAsStateWithLifecycle()
                        RecognitionReviewDialog(
                            review, viewModel::selectCandidate, viewModel::editCandidate,
                            viewModel::reviewCandidate, { viewModel.confirmCandidate() }, viewModel::dismissReview,
                            viewModel::requestHint, viewModel::solveConfirmed,
                            viewModel::requestLocalExplanation,
                            viewModel::saveSolution,
                        )
                    }
                }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean =
                predicate(node) || (0 until node.childCount).any { index ->
                    node.getChild(index)?.let { find(it, predicate) } == true
                }
            var originalVisible = false
            var editableVisible = false
            do {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                originalVisible = root?.let { find(it) { node ->
                    node.isVisibleToUser && node.contentDescription?.toString() == "Original source image"
                } } == true
                editableVisible = root?.let { find(it) { node ->
                    node.isVisibleToUser && node.isEditable && node.text?.toString() == rawText
                } } == true
                if (originalVisible && editableVisible) break
                SystemClock.sleep(40)
            } while (SystemClock.uptimeMillis() < deadline)
            assertTrue("Expected the source image and original candidate in the same review", originalVisible && editableVisible)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun AssetManager.stageVerified(directory: File, name: String, expectedHash: String): File {
        val target = File(directory, name)
        val sha256 = MessageDigest.getInstance("SHA-256")
        DigestInputStream(open("pix2text/$name"), sha256).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        require(sha256.digest().joinToString("") { "%02x".format(it) } == expectedHash) {
            "Formula benchmark model digest mismatch for $name"
        }
        return target
    }

    private fun AssetManager.readFixtures(prefix: String): List<Fixture> {
        val lines = open("${prefix}labels.csv").bufferedReader().use { it.readLines().drop(1) }
        require(lines.isNotEmpty()) { "No formula benchmark fixtures" }
        return lines.map { line ->
            val columns = line.split(',')
            require(columns.size == 5 && columns[1].matches(Regex("[A-Z][0-9]{2}\\.png")))
            Fixture(columns[0], "$prefix${columns[1]}", columns[2], columns[3])
        }
    }

    private fun elapsedMs(start: Long) = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0

    private data class Fixture(val id: String, val asset: String, val source: String, val expected: String)
}
