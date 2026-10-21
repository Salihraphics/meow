package com.salihraphics.meow.analysis.text

import android.graphics.Bitmap
import android.graphics.Color
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.key.Darkness
import com.salihraphics.meow.data.pdf.PdfRenderSession

/** Page images for reading what text can't carry (filled bubbles). On the device only. */
fun interface PageImages {
    suspend fun darkness(pageIndex: Int): Darkness?
}

/** Renders through the viewer's renderer and measures ink; nothing leaves the device. */
class RenderedPageImages(private val session: PdfRenderSession) : PageImages {
    override suspend fun darkness(pageIndex: Int): Darkness = BitmapDarkness(session.render(pageIndex, WIDTH_PX))

    private companion object {
        const val WIDTH_PX = 1000
    }
}

/** Mean darkness of a page-fraction area of [bitmap], sampling every other pixel. */
class BitmapDarkness(private val bitmap: Bitmap) : Darkness {
    override fun mean(area: TextBox): Float {
        val left = (area.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val right = (area.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val top = (area.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (area.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        var total = 0f
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val c = bitmap.getPixel(x, y)
                // Transparent (unrendered) counts as paper.
                val luminance = if (Color.alpha(c) < 16) 1f else (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
                total += 1f - luminance
                count++
            }
        }
        return if (count == 0) 0f else total / count
    }
}
