package com.salihraphics.meow.analysis

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.util.Matrix
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Writes real PDF files for tests, with an embedded Turkish-capable font. Coordinates are page
 * fractions from the top-left, like everything else in the app.
 */
class FixturePdf {
    data class Text(val x: Float, val y: Float, val text: String, val size: Float = 11f)
    private class Page(val texts: MutableList<Text> = mutableListOf(), val rotation: Int = 0, val upright: Boolean = false, val bleed: Float = 0f)

    private val pages = mutableListOf<Page>()

    /**
     * A page stored with /Rotate [rotation]. With [upright], positions are where text appears once
     * a viewer applies the rotation, and it reads upright there (like a page scanned sideways and
     * then rotated); otherwise text is drawn unrotated and appears turned.
     */
    fun page(rotation: Int = 0, upright: Boolean = false, block: MutableList<Text>.() -> Unit = {}) {
        pages += Page(mutableListOf<Text>().apply(block), rotation, upright)
    }

    /**
     * A print-ready page: the media box is [bleed] points larger on every side than the crop box,
     * which is what viewers show. Positions are fractions of the crop box.
     */
    fun bleedPage(bleed: Float, block: MutableList<Text>.() -> Unit) {
        pages += Page(mutableListOf<Text>().apply(block), bleed = bleed)
    }

    /** A page with no text at all, like a scan. */
    fun scannedPage() = page { }

    fun write(file: File): File {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        PDDocument().use { doc ->
            val font = PDType0Font.load(doc, javaClass.getResourceAsStream("/testfonts/DejaVuSans.ttf"))
            for (spec in pages) {
                val sideways = spec.upright && spec.rotation % 180 != 0
                val media = if (sideways) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                val page = PDPage(media).apply { rotation = spec.rotation }
                if (spec.bleed > 0f) {
                    val b = spec.bleed
                    page.mediaBox = PDRectangle(0f, 0f, media.width + 2 * b, media.height + 2 * b)
                    page.cropBox = PDRectangle(b, b, media.width, media.height)
                }
                doc.addPage(page)
                if (spec.texts.isEmpty()) continue
                PDPageContentStream(doc, page).use { cs ->
                    for (t in spec.texts) {
                        cs.beginText()
                        cs.setFont(font, t.size)
                        if (spec.upright) cs.setTextMatrix(uprightMatrix(spec.rotation, t.x, t.y, media)) else
                            cs.newLineAtOffset(spec.bleed + t.x * media.width, spec.bleed + (1f - t.y) * media.height)
                        cs.showText(t.text)
                        cs.endText()
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    /** Places a baseline start at display fraction ([fx], [fy]) with text running left to right on screen. */
    private fun uprightMatrix(rotation: Int, fx: Float, fy: Float, media: PDRectangle): Matrix {
        val w = media.width
        val h = media.height
        return when (rotation % 360) {
            90 -> Matrix(0f, 1f, -1f, 0f, fy * w, fx * h)
            180 -> Matrix(-1f, 0f, 0f, -1f, (1 - fx) * w, fy * h)
            270 -> Matrix(0f, -1f, 1f, 0f, (1 - fy) * w, (1 - fx) * h)
            else -> Matrix(1f, 0f, 0f, 1f, fx * w, (1 - fy) * h)
        }
    }

    companion object {
        /**
         * The layout from the device report: a "1. GÜN" header, question numbers printed in boxes
         * (no full stop), four questions in two columns: 1 and 2 left, 3 and 4 right.
         */
        fun MutableList<Text>.dayPage(numbered: Boolean = true) {
            add(Text(0.08f, 0.035f, "1. GÜN", 14f))
            add(Text(0.70f, 0.035f, "TYT Matematik", 10f))
            listOf(0.06f to 0.12f, 0.06f to 0.52f, 0.53f to 0.12f, 0.53f to 0.52f).forEachIndexed { i, (x, y) ->
                val n = i + 1
                if (numbered) add(Text(x, y, "$n", 13f))
                add(Text(x + 0.045f, y, "Aşağıdakilerden hangisi doğrudur?"))
                add(Text(x + 0.045f, y + 0.03f, "x + y = ${n * 3} ise x kaçtır?"))
                add(Text(x + 0.045f, y + 0.08f, listOf("A", "B", "C", "D", "E").mapIndexed { k, l -> "$l) ${n * 10 + k}" }.joinToString("    ")))
            }
            add(Text(0.49f, 0.96f, "7", 9f))
        }

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
