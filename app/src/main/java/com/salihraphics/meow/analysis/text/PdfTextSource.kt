package com.salihraphics.meow.analysis.text

import com.salihraphics.meow.analysis.PageText
import java.io.Closeable
import java.io.File

/** Reads the text that is actually in a PDF (not OCR), with positions. */
interface PdfTextSource {
    /** @throws java.io.IOException if the file can't be read as a PDF. */
    fun open(file: File): PdfTextDocument
}

interface PdfTextDocument : Closeable {
    val pageCount: Int

    /** Text of one page; an empty [PageText] for pages that are images (scans). */
    fun page(pageIndex: Int): PageText
}
