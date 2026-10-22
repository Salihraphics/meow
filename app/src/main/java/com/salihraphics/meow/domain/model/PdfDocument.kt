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
    /** How the student turned the pages in the viewer, degrees clockwise. */
    val viewRotation: Int = 0,
    /** The student's folder for it; null is "Klasörsüz". */
    val folderId: String? = null,
    /** Pinned to the top of the library and the home screen. */
    val pinned: Boolean = false,
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

/** A folder in the library. Folders only group documents; their PDFs stay where they are. */
data class LibraryFolder(val id: String, val name: String) {
    companion object {
        const val MAX_NAME_LENGTH = 40

        fun sanitizeName(raw: String?): String? =
            raw?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_NAME_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

sealed interface FolderResult {
    data class Done(val folder: LibraryFolder) : FolderResult
    data object BlankName : FolderResult
    data object DuplicateName : FolderResult
    data object NotFound : FolderResult
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
