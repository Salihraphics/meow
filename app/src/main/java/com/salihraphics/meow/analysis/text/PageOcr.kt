package com.salihraphics.meow.analysis.text

import com.salihraphics.meow.analysis.PageText

/** Which part of a page to read with OCR. Margins are cheap strips; the full page costs more. */
enum class OcrRegion { FULL_PAGE, TOP_MARGIN, BOTTOM_MARGIN }

/** OCR for pages that have no embedded text (scans). */
interface PageOcr {
    /** Text of [region] of [pageIndex], positioned in whole-page fractions; null if OCR failed. */
    suspend fun read(pageIndex: Int, region: OcrRegion): PageText?
}
