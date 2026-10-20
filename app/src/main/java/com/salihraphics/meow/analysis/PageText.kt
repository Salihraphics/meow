package com.salihraphics.meow.analysis

/** A rectangle in page fractions (0..1), top-left origin: the same space as annotation strokes. */
data class TextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top

    fun union(other: TextBox) = TextBox(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

    companion object {
        fun of(boxes: List<TextBox>): TextBox = boxes.reduce { a, b -> a.union(b) }
    }
}

data class TextWord(val text: String, val box: TextBox)

/** Words on one line of one column, left to right. */
data class TextLine(val words: List<TextWord>) {
    val text: String = words.joinToString(" ") { it.text }
    val box: TextBox = TextBox.of(words.map { it.box })
}

enum class TextSource { EMBEDDED, OCR }

/** The positioned text of one page, from the PDF itself or from OCR. Lines are top to bottom. */
data class PageText(val pageIndex: Int, val lines: List<TextLine>, val source: TextSource) {
    val characterCount: Int get() = lines.sumOf { line -> line.text.count { !it.isWhitespace() } }
}
