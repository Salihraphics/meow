package com.salihraphics.meow.data.pdf

import java.io.File
import java.io.IOException

/** Checks that a file is a PDF Android can render, and makes its library thumbnail. */
interface PdfInspector {
    /**
     * @return the page count.
     * @throws SecurityException if the PDF is password protected.
     * @throws IOException if the file is damaged or not a PDF the renderer understands.
     */
    fun pageCount(file: File): Int

    /** Writes a JPEG of the first page. Best effort: returns false on any failure. */
    fun writeThumbnail(pdf: File, target: File, widthPx: Int): Boolean
}
