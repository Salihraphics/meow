package com.salihraphics.meow.testing

import android.net.Uri
import com.salihraphics.meow.data.library.DocumentSource
import com.salihraphics.meow.data.pdf.PdfInspector
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Looks enough like a PDF to pass the header check; [FakeInspector] decides the rest. */
fun pdfBytes(marker: String = "a") = "%PDF-1.7\n% test $marker\n1 0 obj << >> endobj\n%%EOF\n".toByteArray()

/** Stands in for the system file picker's documents. */
class FakeDocumentSource : DocumentSource {
    val files = mutableMapOf<Uri, Pair<String?, ByteArray>>()
    val denied = mutableSetOf<Uri>()
    var opened = 0

    fun add(name: String?, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://picker/${files.size}")
        files[uri] = name to bytes
        return uri
    }

    override fun displayName(uri: Uri): String? = files[uri]?.first

    override fun open(uri: Uri): InputStream {
        if (uri in denied) throw SecurityException("revoked")
        val (_, bytes) = files[uri] ?: throw FileNotFoundException(uri.toString())
        opened++
        return ByteArrayInputStream(bytes)
    }
}

class FakeInspector : PdfInspector {
    var pages = 12
    var failure: Exception? = null

    override fun pageCount(file: File): Int {
        failure?.let { throw it }
        return pages
    }

    override fun writeThumbnail(pdf: File, target: File, widthPx: Int): Boolean {
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(1, 2, 3))
        return true
    }

    companion object {
        fun passwordProtected() = FakeInspector().apply { failure = SecurityException("password") }
        fun damaged() = FakeInspector().apply { failure = IOException("corrupt xref") }
    }
}
