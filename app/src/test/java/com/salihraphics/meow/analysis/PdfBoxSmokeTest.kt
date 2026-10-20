package com.salihraphics.meow.analysis

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfBoxSmokeTest {
    @Test
    fun extractsTurkishTextFromAGeneratedPdf() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val bytes = ByteArrayOutputStream().also { out ->
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val font = PDType0Font.load(doc, javaClass.getResourceAsStream("/testfonts/DejaVuSans.ttf"))
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText(); cs.setFont(font, 12f); cs.newLineAtOffset(50f, 700f)
                    cs.showText("CEVAP ANAHTARI — Şık: 1. D  2. A  3. Ç ığüşöİ"); cs.endText()
                }
                doc.save(out)
            }
        }.toByteArray()
        val text = PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
        println("EXTRACTED: $text")
        assertTrue(text.contains("CEVAP ANAHTARI"))
        assertTrue(text.contains("ığüşöİ"))
    }
}
