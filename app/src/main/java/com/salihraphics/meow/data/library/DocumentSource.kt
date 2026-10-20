package com.salihraphics.meow.data.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/** Reads a document the student picked with the system file picker. */
interface DocumentSource {
    fun displayName(uri: Uri): String?

    /** @throws SecurityException or FileNotFoundException if the file can no longer be read. */
    fun open(uri: Uri): InputStream
}

class ContentResolverDocumentSource(private val resolver: ContentResolver) : DocumentSource {
    override fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    override fun open(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("Provider returned no stream for $uri")
}
