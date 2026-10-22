package com.salihraphics.meow.feature.library

import com.salihraphics.meow.analysis.key.TurkishText
import com.salihraphics.meow.domain.model.LibrarySort
import com.salihraphics.meow.domain.model.PdfDocument
import java.text.Collator
import java.util.Locale

/** Which part of the library is shown. */
sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Pinned : LibraryFilter
    /** Documents in no folder ("Klasörsüz"). */
    data object Unfiled : LibraryFilter
    data class Folder(val id: String) : LibraryFilter
}

/** Search, filter and sort, in one pure function so it is simple to test. */
object LibraryOrganizer {
    private val collator: Collator = Collator.getInstance(Locale("tr", "TR")).apply { strength = Collator.PRIMARY }

    fun arrange(documents: List<PdfDocument>, filter: LibraryFilter, query: String, sort: LibrarySort): List<PdfDocument> {
        val needle = TurkishText.fold(query.trim())
        val shown = documents.filter { doc ->
            val inFilter = when (filter) {
                LibraryFilter.All -> true
                LibraryFilter.Pinned -> doc.pinned
                LibraryFilter.Unfiled -> doc.folderId == null
                is LibraryFilter.Folder -> doc.folderId == filter.id
            }
            // Search covers the name the student sees and the original file name, ignoring
            // case and Turkish dots ("ozet" finds "ÖZET").
            inFilter && (needle.isEmpty() || listOfNotNull(doc.displayName, doc.originalFileName).any { TurkishText.fold(it).contains(needle) })
        }
        val sorted = when (sort) {
            LibrarySort.RECENT -> shown.sortedByDescending { it.lastOpenedAt ?: it.importedAt }
            LibrarySort.ADDED -> shown.sortedByDescending { it.importedAt }
            LibrarySort.NAME -> shown.sortedWith { a, b -> collator.compare(a.displayName, b.displayName) }
        }
        // Pinned documents lead every list, in the chosen order among themselves.
        return sorted.sortedByDescending { it.pinned }
    }
}
