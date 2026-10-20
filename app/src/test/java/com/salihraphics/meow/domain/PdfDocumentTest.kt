package com.salihraphics.meow.domain

import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.feature.viewer.MAX_RENDER_WIDTH_PX
import com.salihraphics.meow.feature.viewer.renderWidthFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentTest {
    @Test
    fun namesComeFromFileNamesWithoutExtension() {
        assertEquals("Deneme 3", PdfDocument.nameFromFileName("Deneme 3.pdf"))
        assertEquals("TYT", PdfDocument.nameFromFileName("TYT.PDF"))
        assertEquals("rapor.pdf.bak", PdfDocument.nameFromFileName("rapor.pdf.bak"))
        assertNull(PdfDocument.nameFromFileName(".pdf"))
        assertNull(PdfDocument.nameFromFileName(null))
    }

    @Test
    fun renderWidthIsBucketedAndCapped() {
        assertEquals(renderWidthFor(1000f), renderWidthFor(1010f))
        assertTrue(renderWidthFor(1000f) >= 1000)
        assertEquals(MAX_RENDER_WIDTH_PX, renderWidthFor(10_000f))
        assertTrue(renderWidthFor(1f) > 0)
    }
}
