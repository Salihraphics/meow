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
import android.net.Uri
import com.salihraphics.meow.data.answerkey.KeySourceFiles
import java.io.File
import java.io.IOException
import java.util.UUID

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
    /** Where separate answer-key PDFs are kept. */
    private val keyFiles: KeySourceFiles? = null,
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

    /** A search the student asked for on the review screen. */
    sealed interface KeyRequest {
        data class Running(val progress: Float) : KeyRequest
        data class Done(val testsFound: Int) : KeyRequest
        data class Failed(val reason: Reason) : KeyRequest

        enum class Reason { NOT_A_PDF, NO_ACCESS, UNREADABLE }
    }

    private val _requests = MutableStateFlow<Map<String, KeyRequest>>(emptyMap())

    /** The latest student-requested search per document, until [clearRequest]. */
    val requests: StateFlow<Map<String, KeyRequest>> = _requests.asStateFlow()

    fun clearRequest(documentId: String) = _requests.update { it - documentId }

    /** "The key is on these pages" (0-based): reads them as key pages, with OCR where needed. */
    fun readKeyPages(documentId: String, pages: List<Int>) = request(documentId) { progress ->
        val file = library.pdfFile(documentId)
        val ocr = runCatching { ocrFor(file) }.getOrNull()
        try {
            val outcome = analyzer.analyzeKeyPages(file, pages, ocr?.ocr, progress)
            keys.saveKeyPagesOutcome(documentId, pages, outcome)
        } finally {
            ocr?.close()
        }
    }

    /** A separate answer-key PDF for [documentId]: copied in privately and read as key pages. */
    fun readKeyPdf(documentId: String, uri: Uri) = request(documentId) { progress ->
        val keyFiles = keyFiles ?: throw IOException("no key file store")
        val sourceId = UUID.randomUUID().toString()
        val name = keyFiles.copyIn(uri, sourceId)
        val file = keyFiles.file(sourceId)
        val ocr = runCatching { ocrFor(file) }.getOrNull()
        try {
            val outcome = analyzer.analyzeKeyPages(file, null, ocr?.ocr, progress)
            keys.saveKeySourceOutcome(documentId, sourceId, name.orEmpty(), outcome)
        } finally {
            ocr?.close()
        }
    }

    private fun request(documentId: String, work: suspend ((Float) -> Unit) -> Int) {
        if (_requests.value[documentId] is KeyRequest.Running) return
        _requests.update { it + (documentId to KeyRequest.Running(0f)) }
        scope.launch {
            val state = try {
                KeyRequest.Done(work { p -> _requests.update { it + (documentId to KeyRequest.Running(p)) } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                KeyRequest.Failed(KeyRequest.Reason.NO_ACCESS)
            } catch (e: java.io.FileNotFoundException) {
                KeyRequest.Failed(KeyRequest.Reason.NO_ACCESS)
            } catch (e: IOException) {
                KeyRequest.Failed(if (e.message == "not a PDF") KeyRequest.Reason.NOT_A_PDF else KeyRequest.Reason.UNREADABLE)
            } catch (e: Exception) {
                KeyRequest.Failed(KeyRequest.Reason.UNREADABLE)
            }
            _requests.update { it + (documentId to state) }
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
