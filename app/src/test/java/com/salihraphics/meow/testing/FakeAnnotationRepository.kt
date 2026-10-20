package com.salihraphics.meow.testing

import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.domain.model.Stroke

/** In-memory stand-in: a live set plus a deleted set, like the tombstoned table. */
class FakeAnnotationRepository : AnnotationRepository {
    val all = linkedMapOf<String, Stroke>()
    val deleted = mutableSetOf<String>()

    override suspend fun strokesOnPage(documentId: String, pageIndex: Int) =
        all.values.filter { it.documentId == documentId && it.pageIndex == pageIndex && it.id !in deleted }

    override fun add(stroke: Stroke) {
        all[stroke.id] = stroke
    }

    override fun erase(strokeIds: List<String>) {
        deleted += strokeIds
    }

    override fun restore(strokeIds: List<String>) {
        deleted -= strokeIds.toSet()
    }

    override suspend fun flush() = Unit
}

fun line(vararg xy: Float) = xy
