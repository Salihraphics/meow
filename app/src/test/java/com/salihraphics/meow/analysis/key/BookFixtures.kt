package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextLine
import com.salihraphics.meow.analysis.TextSource
import com.salihraphics.meow.analysis.TextWord

/** Builds positioned page text the way extraction or OCR would produce it. */
object BookFixtures {
    data class L(val text: String, val top: Float, val left: Float = 0.08f, val right: Float = 0.92f)

    fun line(l: L): TextLine {
        val words = l.text.split(' ').filter { it.isNotEmpty() }
        val step = (l.right - l.left) / words.size.coerceAtLeast(1)
        return TextLine(words.mapIndexed { i, w -> TextWord(w, TextBox(l.left + i * step, l.top, l.left + (i + 1) * step - 0.005f, l.top + 0.015f)) })
    }

    fun page(index: Int, vararg lines: L, source: TextSource = TextSource.EMBEDDED) =
        PageText(index, lines.map(::line).sortedWith(compareBy({ it.box.top }, { it.box.left })), source)

    /** A typical question page: test header, two questions with option lines, page number. */
    fun questionPage(index: Int, header: String?, firstQuestion: Int, vararg footer: L): PageText {
        val body = mutableListOf<L>()
        if (header != null) body += L(header, 0.05f)
        body += L("$firstQuestion. Aşağıdakilerden hangisi doğrudur?", 0.15f)
        body += L("A) 12 B) 15 C) 18 D) 21 E) 24", 0.20f)
        body += L("${firstQuestion + 1}. A ve B kümeleri için verilenlerden hangisi yanlıştır?", 0.40f)
        body += L("A) 1 B) 2 C) 3 D) 4 E) 5", 0.45f)
        body += L("3. Bölüm sonunda 9. Sınıf konuları tekrar edilir.", 0.60f)
        body += L("${index + 1}", 0.96f, 0.48f, 0.52f)
        body += footer
        return page(index, *body.toTypedArray())
    }
}
