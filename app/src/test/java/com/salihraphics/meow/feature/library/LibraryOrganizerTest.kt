package com.salihraphics.meow.feature.library

import com.salihraphics.meow.domain.model.LibrarySort
import com.salihraphics.meow.testing.sampleDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizerTest {
    private val ozet = sampleDocument("a", "ÖZET Fizik").copy(importedAt = 100, lastOpenedAt = 900, folderId = "f1")
    private val cikmis = sampleDocument("b", "Çıkmış Sorular").copy(importedAt = 300, lastOpenedAt = null)
    private val deneme = sampleDocument("c", "Deneme 3").copy(importedAt = 200, lastOpenedAt = 400, pinned = true, folderId = "f2")
    private val all = listOf(ozet, cikmis, deneme)

    private fun ids(filter: LibraryFilter = LibraryFilter.All, query: String = "", sort: LibrarySort = LibrarySort.RECENT) =
        LibraryOrganizer.arrange(all, filter, query, sort).map { it.id }

    @Test
    fun pinnedComeFirstThenTheChosenOrder() {
        // Recent uses the last opening, or the import time for documents never opened.
        assertEquals(listOf("c", "a", "b"), ids(sort = LibrarySort.RECENT))
        assertEquals(listOf("c", "b", "a"), ids(sort = LibrarySort.ADDED))
        // Turkish alphabetical order: Ç after C, Ö after O.
        assertEquals(listOf("c", "b", "a"), ids(sort = LibrarySort.NAME))
        val unpinned = LibraryOrganizer.arrange(all.map { it.copy(pinned = false) }, LibraryFilter.All, "", LibrarySort.NAME)
        assertEquals(listOf("b", "c", "a"), unpinned.map { it.id })
    }

    @Test
    fun filtersPickFoldersPinnedAndUnfiled() {
        assertEquals(listOf("a"), ids(LibraryFilter.Folder("f1")))
        assertEquals(listOf("c"), ids(LibraryFilter.Pinned))
        assertEquals(listOf("b"), ids(LibraryFilter.Unfiled))
        assertEquals(emptyList<String>(), ids(LibraryFilter.Folder("nope")))
    }

    @Test
    fun searchIgnoresCaseAndTurkishLettersAndCoversTheFileName() {
        assertEquals(listOf("a"), ids(query = "ozet"))
        assertEquals(listOf("b"), ids(query = "  cikmis "))
        assertEquals(listOf("a"), ids(query = "fizik.pdf"))
        // Search stays inside the selected filter.
        assertEquals(emptyList<String>(), ids(LibraryFilter.Folder("f2"), query = "ozet"))
        assertEquals(listOf("c", "a", "b"), ids(query = ""))
    }
}
