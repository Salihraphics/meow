package com.salihraphics.meow.data.answerkey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The latest answer-key search report per document, as plain text on the device. It leaves the
 * device only if the student copies or shares it.
 */
class AnalysisReports(private val dir: File) {
    private fun file(documentId: String) = File(dir, "$documentId.txt")

    suspend fun save(documentId: String, report: String) = withContext(Dispatchers.IO) {
        if (report.isBlank()) return@withContext
        dir.mkdirs()
        file(documentId).writeText(report)
    }

    suspend fun load(documentId: String): String? = withContext(Dispatchers.IO) { file(documentId).takeIf { it.exists() }?.readText() }

    suspend fun delete(documentId: String) = withContext(Dispatchers.IO) { file(documentId).delete() }
}
