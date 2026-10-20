package com.salihraphics.meow.data.library

import java.io.File

/**
 * MEOW's private copies of imported PDFs, under `files/library/`. Nothing here ever points at the
 * student's original file.
 */
class PdfFileStore(root: File) {
    private val documents = File(root, "documents")
    private val thumbnails = File(root, "thumbnails")
    private val incoming = File(root, "incoming")

    fun pdfFile(id: String) = File(documents, "$id.pdf")

    fun thumbnailFile(id: String) = File(thumbnails, "$id.jpg")

    /** A fresh temporary file for an import in progress. */
    fun newIncomingFile(id: String): File {
        incoming.mkdirs()
        return File(incoming, "$id.part")
    }

    /** Moves a verified import into place. Never replaces an existing document. */
    fun commit(incomingFile: File, id: String): File {
        documents.mkdirs()
        val target = pdfFile(id)
        check(!target.exists()) { "Document $id already exists" }
        if (!incomingFile.renameTo(target)) {
            incomingFile.copyTo(target)
            incomingFile.delete()
        }
        return target
    }

    fun delete(id: String) {
        pdfFile(id).delete()
        thumbnailFile(id).delete()
    }

    /** Removes leftovers from imports interrupted by a crash or process death. */
    fun clearIncoming() {
        incoming.listFiles()?.forEach { it.delete() }
    }
}
