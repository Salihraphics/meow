package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.domain.model.AnalysisStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs answer-key analysis in the background, one document at a time, so importing never waits
 * on it. Every document in the library is analysed once (or again if its file changed or the
 * analyzer improved); runs interrupted by a killed process start over on the next launch.
 */
class AnswerKeyAnalysisScheduler(
    private val scope: CoroutineScope,
    private val library: PdfLibraryRepository,
    private val keys: AnswerKeyRepository,
    private val analyzer: AnswerKeyAnalyzer,
    /** OCR for one file's scanned pages, or null when unavailable. Closed after the run. */
    private val ocrFor: suspend (File) -> OcrHandle?,
) {
    interface OcrHandle : AutoCloseable {
        val ocr: PageOcr
    }

    private val queue = Channel<kotlin.Pair<String, Boolean>>(Channel.UNLIMITED)
    private val queued = HashSet<String>()
    private var currentId: String? = null
    private var currentJob: Job? = null

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Documents being analysed (or waiting), with progress 0..1. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun start() {
        scope.launch {
            for ((id, force) in queue) {
                synchronized(queued) { queued -= id }
                val job = launch { run(id, force) }
                currentId = id
                currentJob = job
                job.join()
                currentId = null
                _progress.update { it - id }
            }
        }
        scope.launch {
            // Only membership matters; reading positions change the list constantly.
            library.documents.map { list -> list.map { it.id }.toSet() }.distinctUntilChanged().collect { ids ->
                for (id in ids) {
                    val existing = keys.analysis(id)
                    val hash = library.contentHash(id) ?: continue
                    val stale = existing == null || existing.contentSha256 != hash ||
                        existing.analyzerVersion < AnswerKeyRepository.ANALYZER_VERSION ||
                        existing.status == AnalysisStatus.RUNNING.name || existing.status == AnalysisStatus.PENDING.name
                    if (stale) enqueue(id)
                }
                // A document that left the library stops being analysed.
                currentId?.let { if (it !in ids) currentJob?.cancel() }
            }
        }
    }

    /** Queues [documentId]; with [force], analyses again even if a result exists. */
    fun enqueue(documentId: String, force: Boolean = false) {
        val added = synchronized(queued) { queued.add(documentId) }
        if (!added && !force) return
        _progress.update { it + (documentId to 0f) }
        queue.trySend(documentId to force)
    }

    private suspend fun run(documentId: String, force: Boolean) {
        val hash = library.contentHash(documentId) ?: return
        val existing = keys.analysis(documentId)
        if (!force && existing != null && existing.contentSha256 == hash && existing.status == AnalysisStatus.DONE.name &&
            existing.analyzerVersion >= AnswerKeyRepository.ANALYZER_VERSION
        ) return
        val file = library.pdfFile(documentId)
        if (!file.exists()) return
        keys.markStarted(documentId, hash)
        val ocr = runCatching { ocrFor(file) }.getOrNull()
        try {
            val outcome = analyzer.analyze(file, ocr?.ocr) { p -> _progress.update { it + (documentId to p) } }
            keys.saveOutcome(documentId, hash, outcome)
        } catch (e: CancellationException) {
            throw e // left as RUNNING; picked up again on the next launch if the document remains
        } catch (e: Exception) {
            keys.markFailed(documentId, hash, e.javaClass.simpleName)
        } finally {
            ocr?.close()
        }
    }
}
