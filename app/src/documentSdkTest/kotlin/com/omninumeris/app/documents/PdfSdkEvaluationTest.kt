package com.omninumeris.app.documents

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfSdkEvaluationTest {
    @get:Rule
    val output = TemporaryFolder()

    @Before
    fun initializePdfBox() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        // PDFBox reads glyph assets through applicationContext. These assets belong to the test APK.
        PDFBoxResourceLoader.init(object : ContextWrapper(testContext) {
            override fun getApplicationContext(): Context = this
        })
    }

    @Test
    fun platformRendersSavedPageAndScansWithBoundedBitmap() {
        val reader = fixture("reader.pdf", output.root)
        val scan = fixture("scanned.pdf", output.root)
        val large = fixture("many-pages.pdf", output.root)
        val saved = output.newFile("reading-position.txt").apply { writeText("1") }

        assertRendered(reader, saved.readText().toInt(), expectedPages = 2)
        assertRendered(scan, 0, expectedPages = 1)
        val before = Debug.getPss()
        val start = System.nanoTime()
        for (page in listOf(0, 63, 127)) assertRendered(large, page, expectedPages = 128)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        report("api=${Build.VERSION.SDK_INT} platformPdf pages=128 sampled=3 bitmap=216x288 " +
            "elapsedMs=$elapsed pssBeforeKiB=$before pssAfterKiB=${Debug.getPss()}")
        PDDocument.load(scan).use { document -> assertTrue(PDFTextStripper().getText(document).isBlank()) }
    }

    @Test
    fun pdfBoxExportsAnnotationFormAndPageOperationWithOriginalPreserved() {
        val source = fixture("reader.pdf", output.root)
        val original = source.readBytes()
        val exported = File(output.root, "annotated.pdf")
        val extracted = File(output.root, "extracted-page.pdf")
        PDDocument.load(source, MemoryUsageSetting.setupMixed(4L * 1024 * 1024)).use { document ->
            assertTrue(pageText(document, 1).contains("juniper"))
            val form = requireNotNull(document.documentCatalog.acroForm)
            val field = requireNotNull(form.getField("reader_note"))
            assertEquals("Original", field.valueAsString)
            form.needAppearances = false
            field.setValue("Reviewed")
            val note = PDAnnotationText().apply {
                rectangle = PDRectangle(36f, 340f, 24f, 24f)
                contents = "Original fixture annotation"
            }
            document.getPage(1).annotations.add(note)
            document.save(exported)
            document.removePage(0)
            document.save(extracted)
        }

        assertArrayEquals(original, source.readBytes())
        PDDocument.load(exported).use { result ->
            val page = result.getPage(1)
            val field = requireNotNull(result.documentCatalog.acroForm.getField("reader_note"))
            assertEquals("Reviewed", field.valueAsString)
            val widget = field.widgets.single()
            val appearance = requireNotNull(widget.normalAppearanceStream)
            assertTrue(appearance.contents.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }.contains("(Reviewed)"))
            assertTrue(page.annotations.any {
                it.cosObject == widget.cosObject &&
                    (it.cosObject.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.V) == null ||
                        it.cosObject.getString(com.tom_roush.pdfbox.cos.COSName.V) == "Reviewed")
            })
            assertTrue(page.annotations.any { it.contents == "Original fixture annotation" })
        }
        PDDocument.load(extracted).use { result ->
            assertEquals(1, result.numberOfPages)
            assertTrue(pageText(result, 0).contains("juniper"))
        }
        assertRendered(exported, 1, expectedPages = 2)
    }

    @Test
    fun rejectsUnreadableInputAndRequiresAuthorizedPassword() {
        val protected = fixture("protected.pdf", output.root)
        val corrupted = output.newFile("invalid.pdf").apply { writeText("%PDF-1.7\ntruncated fixture") }
        assertThrows(IOException::class.java) { PDDocument.load(corrupted).close() }
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(protected).close() }
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(protected, "wrong").close() }
        PDDocument.load(protected, "fixture-reader").use {
            assertTrue(pageText(it, 0).contains("kauri"))
        }
    }

    private fun assertRendered(file: File, pageIndex: Int, expectedPages: Int) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (exception: Exception) {
            fd.close()
            throw exception
        }
        renderer.use {
            assertEquals(expectedPages, it.pageCount)
            it.openPage(pageIndex).use { page ->
                val bitmap = Bitmap.createBitmap(216, 288, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val darkPixels = (0 until bitmap.height step 2).sumOf { y ->
                        (0 until bitmap.width step 2).count { x -> Color.red(bitmap.getPixel(x, y)) < 160 }
                    }
                    assertTrue("Expected visible ink on page $pageIndex", darkPixels > 10)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun pageText(document: PDDocument, zeroBasedPage: Int): String = PDFTextStripper().apply {
        startPage = zeroBasedPage + 1
        endPage = startPage
    }.getText(document)
}

internal fun fixture(name: String, directory: File): File =
    File(directory, name).also { target ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("documents/$name").use { source ->
            target.outputStream().use { source.copyTo(it) }
        }
    }

internal fun report(message: String) {
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("documentEvaluation", message)
    })
}
