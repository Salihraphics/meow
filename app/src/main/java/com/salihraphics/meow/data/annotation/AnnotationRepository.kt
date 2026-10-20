package com.salihraphics.meow.data.annotation

import android.util.Log
import com.salihraphics.meow.domain.model.Stroke
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface AnnotationRepository {
    /** Live strokes on one page, oldest first. Waits for pending writes, so it is never stale. */
    suspend fun strokesOnPage(documentId: String, pageIndex: Int): List<Stroke>

    /** Queues a write and returns immediately; drawing never waits for the disk. */
    fun add(stroke: Stroke)

    fun erase(strokeIds: List<String>)

    /** Brings erased (or undone) strokes back. */
    fun restore(strokeIds: List<String>)

    /** Suspends until every write queued so far is on disk. */
    suspend fun flush()
}

/**
 * Writes go through one ordered queue running on [scope], which outlives screens: leaving the
 * viewer right after a stroke still saves it, and an erase can never overtake the add it undoes.
 */
class LocalAnnotationRepository(
    private val dao: StrokeDao,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : AnnotationRepository {

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (e: Exception) {
                    // One failed write must not stop the queue; the rest still get saved.
                    Log.w("MeowAnnotations", "Annotation write failed", e)
                }
            }
        }
    }

    override suspend fun strokesOnPage(documentId: String, pageIndex: Int): List<Stroke> {
        flush()
        return dao.page(documentId, pageIndex).map { it.toDomain() }
    }

    override fun add(stroke: Stroke) = enqueue { dao.insert(StrokeEntity.from(stroke, now())) }

    override fun erase(strokeIds: List<String>) {
        if (strokeIds.isNotEmpty()) enqueue { dao.markDeleted(strokeIds, now()) }
    }

    override fun restore(strokeIds: List<String>) {
        if (strokeIds.isNotEmpty()) enqueue { dao.restore(strokeIds, now()) }
    }

    override suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writes.send { done.complete(Unit) }
        done.await()
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend(write)
    }
}
