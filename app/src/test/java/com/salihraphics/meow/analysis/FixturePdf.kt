package com.salihraphics.meow.analysis

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Writes real PDF files for tests, with an embedded Turkish-capable font. Coordinates are page
 * fractions from the top-left, like everything else in the app.
 */
class FixturePdf {
    data class Text(val x: Float, val y: Float, val text: String, val size: Float = 11f)
    private class Page(val texts: MutableList<Text> = mutableListOf(), val rotation: Int = 0)

    private val pages = mutableListOf<Page>()

    fun page(rotation: Int = 0, block: MutableList<Text>.() -> Unit = {}) {
        pages += Page(mutableListOf<Text>().apply(block), rotation)
    }

    /** A page with no text at all, like a scan. */
    fun scannedPage() = page { }

    fun write(file: File): File {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        PDDocument().use { doc ->
            val font = PDType0Font.load(doc, javaClass.getResourceAsStream("/testfonts/DejaVuSans.ttf"))
            for (spec in pages) {
                val page = PDPage(PDRectangle.A4).apply { rotation = spec.rotation }
                doc.addPage(page)
                if (spec.texts.isEmpty()) continue
                PDPageContentStream(doc, page).use { cs ->
                    for (t in spec.texts) {
                        cs.beginText()
                        cs.setFont(font, t.size)
                        cs.newLineAtOffset(t.x * PDRectangle.A4.width, (1f - t.y) * PDRectangle.A4.height)
                        cs.showText(t.text)
                        cs.endText()
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    companion object {
        /** A question page in the usual Turkish layout: test header, questions, options, page number. */
        fun MutableList<Text>.questionPage(header: String?, first: Int, pageNumber: Int) {
            if (header != null) add(Text(0.08f, 0.06f, header, 14f))
            add(Text(0.08f, 0.15f, "$first. Aşağıdakilerden hangisi doğrudur?"))
            add(Text(0.08f, 0.20f, "A) 12     B) 15     C) 18     D) 21     E) 24"))
            add(Text(0.08f, 0.45f, "${first + 1}. A ve B kümeleri için hangisi yanlıştır?"))
            add(Text(0.08f, 0.50f, "A) 1     B) 2     C) 3     D) 4     E) 5"))
            add(Text(0.49f, 0.96f, "$pageNumber", 9f))
        }
    }
}
