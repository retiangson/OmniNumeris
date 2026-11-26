package com.omninumeris.app.documents

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.app.MainActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.shared.util.toUrl
import org.readium.r2.shared.util.use
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

class EpubSdkEvaluationTest {
    @get:Rule
    val output = TemporaryFolder()

    private val networkAttempts = AtomicInteger()
    private val retriever = AssetRetriever(
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
        object : HttpClient {
            override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> {
                networkAttempts.incrementAndGet()
                return Try.failure(HttpError.IO(IOException("Network disabled for the document evaluation")))
            }
        },
    )
    private val opener = PublicationOpener(EpubParser())

    @OptIn(ExperimentalReadiumApi::class)
    @Test
    fun localPublicationCanSearchAndRestoreChapterAnchor() = runBlocking<Unit> {
        val source = fixture("reader.epub", output.root)
        val original = source.readBytes()
        val position = output.newFile("reading-position.json")
        val start = System.nanoTime()
        requireNotNull(open(source)).use { book ->
            assertEquals("Offline Reading Fixture", book.metadata.title)
            assertEquals(2, book.readingOrder.size)
            assertEquals(2, book.tableOfContents.size)
            val chapter = book.readingOrder[1]
            val locator = requireNotNull(book.locatorFromLink(chapter))
            assertTrue(book.positions().any { it.href == locator.href })
            val results = mutableListOf<Locator>()
            requireNotNull(book.search("stable sentence")).use { search ->
                assertTrue(search.forEach { results += it.locators } is Try.Success)
            }
            assertTrue(results.any { it.href == locator.href && it.text.highlight == "stable sentence" })
            val anchor = locator.copy(
                locations = locator.locations.copy(fragments = listOf("reading-anchor")),
                text = Locator.Text(highlight = "A stable sentence marks the reading location."),
            )
            position.writeText(anchor.toJSON().toString())
        }
        requireNotNull(open(source)).use { reopened ->
            val restored = requireNotNull(Locator.fromJSON(JSONObject(position.readText())))
            assertEquals(requireNotNull(reopened.locatorFromLink(reopened.readingOrder[1])).href, restored.href)
            val resource = requireNotNull(reopened.get(restored.href))
            resource.use {
                val xhtml = requireNotNull(it.read().getOrNull()).toString(Charsets.UTF_8)
                assertTrue(xhtml.contains("id=\"${restored.locations.fragments.single()}\""))
                assertTrue(xhtml.contains(requireNotNull(restored.text.highlight)))
                assertAnchorSurvivesReflow(xhtml, restored.locations.fragments.single())
            }
        }
        assertArrayEquals(original, source.readBytes())
        assertEquals(0, networkAttempts.get())
        report("api=${Build.VERSION.SDK_INT} readium chapters=2 openSearchReflowMs=${(System.nanoTime() - start) / 1_000_000}")
    }

    @Test
    fun malformedPublicationFailsLocally() = runBlocking<Unit> {
        val invalid = output.newFile("broken.epub").apply { writeText("Invalid EPUB fixture") }
        assertNull(open(invalid))
        assertEquals(0, networkAttempts.get())
    }

    private suspend fun open(file: File): Publication? {
        val asset = retriever.retrieve(file.toUrl()).getOrNull() ?: return null
        return opener.open(asset, allowUserInteraction = false).getOrNull().also {
            if (it == null) asset.close()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun assertAnchorSurvivesReflow(xhtml: String, fragment: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        var renderingView: WebView? = null
        try {
            instrumentation.runOnMainSync {
                val view = WebView(activity).apply {
                    // The only inputs are generated test XHTML and a quoted fixture ID.
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                }
                renderingView = view
                activity.setContentView(view)
            }
            val view = requireNotNull(renderingView)

            fun measure(textZoom: Int): Int {
                val rendered = CountDownLatch(1)
                var result: String? = null
                instrumentation.runOnMainSync {
                    view.settings.textZoom = textZoom
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(webView: WebView, url: String) {
                            webView.evaluateJavascript(
                                """
                                (() => {
                                  const anchor = document.getElementById(${JSONObject.quote(fragment)});
                                  if (!anchor || window.innerHeight <= 0) return 0;
                                  anchor.scrollIntoView();
                                  const rect = anchor.getBoundingClientRect();
                                  return window.scrollY > 0 && rect.top >= -1 && rect.bottom <= window.innerHeight
                                    ? document.documentElement.scrollHeight : 0;
                                })()
                                """.trimIndent(),
                            ) {
                                result = it
                                rendered.countDown()
                            }
                        }
                    }
                    view.loadDataWithBaseURL(null, xhtml, "text/html", "UTF-8", null)
                }
                assertTrue("Local EPUB chapter did not finish painting", rendered.await(12, TimeUnit.SECONDS))
                return requireNotNull(result).toInt()
            }

            val normal = measure(100)
            val enlarged = measure(175)
            assertTrue("Expected visible anchor and different reflowed heights: $normal, $enlarged",
                normal > 0 && enlarged > normal)
        } finally {
            instrumentation.runOnMainSync {
                activity.setContentView(View(activity))
                renderingView?.destroy()
                activity.finish()
            }
        }
    }
}
