package com.twojstar.llmbench.data.document

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Process-death-safe temporary storage for one or more pending Docbench exports. */
internal class DocbenchPendingExportStore(
    private val directory: File
) {
    fun save(document: TextDocument): String {
        val bytes = TextDocumentCodec.encodeUtf8(document)
        if (bytes.size > TextDocumentFileAccess.MAX_DOCUMENT_BYTES) {
            throw IOException("Document is larger than the export limit.")
        }

        directory.mkdirs()
        if (!directory.isDirectory) throw IOException("Could not prepare export storage.")

        val id = UUID.randomUUID().toString()
        val target = fileFor(id) ?: throw IOException("Could not create export storage.")
        val staging = File(directory, "$id.tmp")
        try {
            FileOutputStream(staging).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!staging.renameTo(target)) {
                throw IOException("Could not finalize export storage.")
            }
        } catch (error: IOException) {
            staging.delete()
            target.delete()
            throw error
        }
        return id
    }

    fun load(id: String): TextDocument? {
        val file = fileFor(id) ?: return null
        if (!file.isFile) return null
        if (file.length() !in 0..TextDocumentFileAccess.MAX_DOCUMENT_BYTES.toLong()) {
            file.delete()
            return null
        }
        return try {
            TextDocumentCodec.decodeUtf8(file.readBytes())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun delete(id: String?) {
        id?.let(::fileFor)?.delete()
    }

    private fun fileFor(id: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        return File(directory, "$id.bin")
    }

    private companion object {
        val ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
