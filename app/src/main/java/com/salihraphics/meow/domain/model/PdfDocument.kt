package com.salihraphics.meow.domain.model

/** A PDF in the student's library. MEOW keeps its own copy; the original file is never modified. */
data class PdfDocument(
    val id: String,
    val displayName: String,
    /** Name of the file the student picked, kept for reference. */
    val originalFileName: String?,
    val sizeBytes: Long,
    val pageCount: Int,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    /** Zero-based page the student was last reading. */
    val lastPageIndex: Int,
) {
    companion object {
        const val MAX_NAME_LENGTH = 120

        /** Trims and collapses whitespace; blank means "no usable name". */
        fun sanitizeName(raw: String?): String? =
            raw?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(MAX_NAME_LENGTH)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        /** "Deneme 3.pdf" → "Deneme 3". */
        fun nameFromFileName(fileName: String?): String? =
            sanitizeName(fileName?.replace(Regex("(?i)\\.pdf$"), ""))
    }
}

enum class PdfImportError {
    NOT_A_PDF,
    EMPTY,
    PASSWORD_PROTECTED,
    DAMAGED,
    /** The picked file could not be opened (permission revoked, file moved, provider failed). */
    NO_ACCESS,
    /** Reading or copying failed, e.g. the device ran out of space. */
    STORAGE,
}

sealed interface PdfImportResult {
    data class Imported(val document: PdfDocument) : PdfImportResult
    /** The same file (by content) is already in the library; nothing was added or overwritten. */
    data class AlreadyInLibrary(val document: PdfDocument) : PdfImportResult
    data class Failed(val error: PdfImportError) : PdfImportResult
}
