package com.twojstar.llmbench.data.document

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Process-death-safe temporary storage for one or more pending Docbench exports. */
internal class DocbenchPendingExportStore(
    private val directory: File
) {
    private val lock = Any()
    private val activeIds = mutableSetOf<String>()

    fun save(document: TextDocument): String = synchronized(lock) {
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
        activeIds += id
        id
    }

    fun load(id: String): TextDocument? = synchronized(lock) {
        val file = fileFor(id) ?: return@synchronized null
        if (!file.isFile) {
            activeIds -= id
            return@synchronized null
        }
        if (file.length() !in 0..TextDocumentFileAccess.MAX_DOCUMENT_BYTES.toLong()) {
            activeIds -= id
            file.delete()
            return@synchronized null
        }
        val document = try {
            TextDocumentCodec.decodeUtf8(file.readBytes())
        } catch (_: IllegalArgumentException) {
            null
        }
        if (document != null) {
            activeIds += id
        } else {
            activeIds -= id
        }
        document
    }

    fun delete(id: String) {
        synchronized(lock) {
            activeIds -= id
            fileFor(id)?.delete()
        }
    }

    fun pruneOrphans(preserveId: String?) {
        synchronized(lock) {
            if (!directory.isDirectory) return@synchronized
            val preservedIds = activeIds.toMutableSet().apply {
                preserveId?.takeIf(ID_PATTERN::matches)?.let(::add)
            }
            directory.listFiles()?.forEach { file ->
                val name = file.name
                val stem = name.substringBeforeLast('.', missingDelimiterValue = "")
                val isOwnedPayload = ID_PATTERN.matches(stem) &&
                    (name.endsWith(".bin") || name.endsWith(".tmp"))
                if (isOwnedPayload && stem !in preservedIds) file.delete()
            }
        }
    }

    private fun fileFor(id: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        return File(directory, "$id.bin")
    }

    private companion object {
        val ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
