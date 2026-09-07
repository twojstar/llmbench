package com.twojstar.llmbench.data.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal data class RecentMarkdownDocument(
    val uriString: String,
    val displayName: String,
    val isPinned: Boolean
) {
    val uri: Uri
        get() = Uri.parse(uriString)
}

internal fun shouldForgetRecentDocumentAfterOpenFailure(error: Throwable): Boolean =
    error is FileNotFoundException || error is SecurityException

internal class MarkdownRecentDocumentsStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val atomicFile = AtomicFile(File(appContext.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()

    fun hasReadPermission(uri: Uri): Boolean =
        uri.scheme == CONTENT_SCHEME && persistedReadUriStrings().contains(uri.toString())

    fun retainReadPermission(uri: Uri): Boolean {
        if (uri.scheme != CONTENT_SCHEME) return false
        if (hasReadPermission(uri)) return true
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return hasReadPermission(uri)
    }

    fun releaseReadPermission(uri: Uri) {
        if (uri.scheme != CONTENT_SCHEME) return
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun load(): List<RecentMarkdownDocument> = mutex.withLock {
        withContext(Dispatchers.IO) { loadUnlocked() }
    }

    suspend fun record(uri: Uri): List<RecentMarkdownDocument> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val persisted = persistedReadUriStrings()
            val uriString = uri.toString()
            val saved = readEntries()
            val current = pruneToPersisted(saved, persisted)
            val next = if (uriString in persisted) {
                MarkdownRecentDocumentsCodec.promote(current, uriString)
            } else {
                current
            }
            if (next != saved) writeEntries(next)
            MarkdownRecentDocumentsCodec.evictedFrom(current, next)
                .forEach { releaseReadPermission(Uri.parse(it)) }
            resolveDocuments(next)
        }
    }

    suspend fun togglePinned(document: RecentMarkdownDocument): List<RecentMarkdownDocument> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val saved = readEntries()
            val current = pruneToPersisted(saved, persistedReadUriStrings())
            val stored = current.firstOrNull { it.uriString == document.uriString }
            val next = stored?.let { entry ->
                MarkdownRecentDocumentsCodec.setPinned(current, entry.uriString, !entry.isPinned)
            } ?: current
            if (next != saved) writeEntries(next)
            resolveDocuments(next)
        }
    }

    suspend fun forget(document: RecentMarkdownDocument): List<RecentMarkdownDocument> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readEntries()
            val next = current.filterNot { it.uriString == document.uriString }
            if (next != current) writeEntries(next)
            releaseReadPermission(document.uri)
            loadUnlocked()
        }
    }

    suspend fun forgetIfUnavailable(
        document: RecentMarkdownDocument,
        error: Throwable
    ): List<RecentMarkdownDocument>? =
        if (shouldForgetRecentDocumentAfterOpenFailure(error)) forget(document) else null

    private fun loadUnlocked(): List<RecentMarkdownDocument> {
        val saved = readEntries()
        val retained = pruneToPersisted(saved, persistedReadUriStrings())
        if (retained != saved) writeEntries(retained)
        return resolveDocuments(retained)
    }

    private fun pruneToPersisted(
        entries: List<MarkdownRecentDocumentEntry>,
        persisted: Set<String>
    ): List<MarkdownRecentDocumentEntry> =
        MarkdownRecentDocumentsCodec.normalize(entries.filter { it.uriString in persisted })

    private fun resolveDocuments(entries: List<MarkdownRecentDocumentEntry>): List<RecentMarkdownDocument> =
        entries.map { entry ->
            val uri = Uri.parse(entry.uriString)
            RecentMarkdownDocument(
                uriString = entry.uriString,
                displayName = MarkdownDocumentFileAccess.displayName(appContext, uri),
                isPinned = entry.isPinned
            )
        }

    private fun persistedReadUriStrings(): Set<String> = runCatching {
        resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
    }.getOrDefault(emptySet())

    private fun readEntries(): List<MarkdownRecentDocumentEntry> {
        if (!atomicFile.baseFile.isFile) return emptyList()
        val bytes = try {
            atomicFile.readFully()
        } catch (_: IOException) {
            return emptyList()
        }
        return MarkdownRecentDocumentsCodec.decode(bytes)
    }

    private fun writeEntries(entries: List<MarkdownRecentDocumentEntry>) {
        atomicFile.baseFile.parentFile?.mkdirs()
        val bytes = MarkdownRecentDocumentsCodec.encode(entries)
        var output: FileOutputStream? = atomicFile.startWrite()
        try {
            val stream = requireNotNull(output)
            stream.write(bytes)
            stream.flush()
            atomicFile.finishWrite(stream)
            output = null
        } catch (error: IOException) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private companion object {
        const val CONTENT_SCHEME = "content"
        const val FILE_NAME = "markdown-recent-documents-v1.bin"
    }
}
