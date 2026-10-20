package com.salihraphics.meow.data.library

import android.net.Uri
import com.salihraphics.meow.data.annotation.StrokeDao
import com.salihraphics.meow.data.library.db.PdfDocumentDao
import com.salihraphics.meow.data.library.db.PdfDocumentEntity
import com.salihraphics.meow.data.pdf.PdfInspector
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.domain.model.PdfImportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

class LocalPdfLibraryRepository(
    private val dao: PdfDocumentDao,
    private val files: PdfFileStore,
    private val source: DocumentSource,
    private val inspector: PdfInspector,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Annotations of a removed document go with it. */
    private val strokes: StrokeDao? = null,
    /** Lets other data tied to a document (answer keys) go with it. */
    private val onRemoved: suspend (String) -> Unit = {},
) : PdfLibraryRepository {

    override val documents: Flow<List<PdfDocument>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): PdfDocument? = dao.get(id)?.toDomain()

    override suspend fun contentHash(id: String): String? = dao.get(id)?.contentSha256

    override suspend fun import(uri: Uri, fallbackName: String): PdfImportResult = withContext(io) {
        val id = newId()
        val incoming = files.newIncomingFile(id)
        try {
            val copy = when (val c = copyIn(uri, incoming)) {
                is Copy.Failed -> return@withContext PdfImportResult.Failed(c.error)
                is Copy.Done -> c
            }
            if (copy.size == 0L) return@withContext PdfImportResult.Failed(PdfImportError.EMPTY)
            if (!startsWithPdfHeader(incoming)) return@withContext PdfImportResult.Failed(PdfImportError.NOT_A_PDF)

            dao.findByContent(copy.sha256)?.let { return@withContext PdfImportResult.AlreadyInLibrary(it.toDomain()) }

            val pageCount = try {
                inspector.pageCount(incoming)
            } catch (e: SecurityException) {
                return@withContext PdfImportResult.Failed(PdfImportError.PASSWORD_PROTECTED)
            } catch (e: IOException) {
                return@withContext PdfImportResult.Failed(PdfImportError.DAMAGED)
            } catch (e: IllegalArgumentException) {
                return@withContext PdfImportResult.Failed(PdfImportError.DAMAGED)
            }
            if (pageCount <= 0) return@withContext PdfImportResult.Failed(PdfImportError.EMPTY)
            coroutineContext.ensureActive()

            val stored = try {
                files.commit(incoming, id)
            } catch (e: IOException) {
                files.delete(id)
                return@withContext PdfImportResult.Failed(PdfImportError.STORAGE)
            }
            inspector.writeThumbnail(stored, files.thumbnailFile(id), THUMBNAIL_WIDTH_PX)

            val time = now()
            val entity = PdfDocumentEntity(
                id = id,
                displayName = PdfDocument.nameFromFileName(copy.fileName) ?: fallbackName,
                originalFileName = copy.fileName,
                sizeBytes = copy.size,
                pageCount = pageCount,
                contentSha256 = copy.sha256,
                importedAt = time,
                lastOpenedAt = null,
                lastPageIndex = 0,
                updatedAt = time,
                deletedAt = null,
            )
            try {
                dao.insert(entity)
            } catch (e: Throwable) {
                files.delete(id)
                throw e
            }
            PdfImportResult.Imported(entity.toDomain())
        } finally {
            incoming.delete()
        }
    }

    override suspend fun rename(id: String, newName: String): Boolean {
        val name = PdfDocument.sanitizeName(newName) ?: return false
        return dao.rename(id, name, now()) > 0
    }

    override suspend fun remove(id: String) {
        val time = now()
        dao.markDeleted(id, time)
        strokes?.markDocumentDeleted(id, time)
        onRemoved(id)
        withContext(io) { files.delete(id) }
    }

    override suspend fun recordReading(id: String, pageIndex: Int) {
        dao.updateReading(id, pageIndex.coerceAtLeast(0), now())
    }

    override fun pdfFile(id: String): File = files.pdfFile(id)

    override fun thumbnailFile(id: String): File = files.thumbnailFile(id)

    private sealed interface Copy {
        data class Done(val fileName: String?, val size: Long, val sha256: String) : Copy
        data class Failed(val error: PdfImportError) : Copy
    }

    /** Streams the picked file into [target], hashing as it goes. Stops promptly if cancelled. */
    private suspend fun copyIn(uri: Uri, target: File): Copy {
        val fileName = source.displayName(uri)
        val input = try {
            source.open(uri)
        } catch (e: SecurityException) {
            return Copy.Failed(PdfImportError.NO_ACCESS)
        } catch (e: FileNotFoundException) {
            return Copy.Failed(PdfImportError.NO_ACCESS)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            input.use { inp ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = inp.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        size += read
                    }
                }
            }
        } catch (e: IOException) {
            return Copy.Failed(PdfImportError.STORAGE)
        } catch (e: SecurityException) {
            return Copy.Failed(PdfImportError.NO_ACCESS)
        }
        return Copy.Done(fileName, size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    /** PDFs start with "%PDF-", allowing a little leading junk as some generators do. */
    private fun startsWithPdfHeader(file: File): Boolean {
        val head = ByteArray(1024)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0) return false
        return String(head, 0, read, Charsets.ISO_8859_1).contains("%PDF-")
    }

    companion object {
        const val THUMBNAIL_WIDTH_PX = 320
    }
}
