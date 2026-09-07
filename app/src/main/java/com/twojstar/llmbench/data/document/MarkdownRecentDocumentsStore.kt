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
    val displayName: String
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
            val saved = readUriStrings()
            val current = pruneToPersisted(saved, persisted)
            val next = if (uriString in persisted) {
                MarkdownRecentDocumentsCodec.moveToFront(current, uriString)
            } else {
                current
            }
            if (next != saved) writeUriStrings(next)
            MarkdownRecentDocumentsCodec.evictedFrom(current, next)
                .forEach { releaseReadPermission(Uri.parse(it)) }
            resolveDocuments(next)
        }
    }

    suspend fun forget(document: RecentMarkdownDocument): List<RecentMarkdownDocument> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readUriStrings()
            val next = current.filterNot { it == document.uriString }
            if (next != current) writeUriStrings(next)
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
        val saved = readUriStrings()
        val retained = pruneToPersisted(saved, persistedReadUriStrings())
        if (retained != saved) writeUriStrings(retained)
        return resolveDocuments(retained)
    }

    private fun pruneToPersisted(
        uriStrings: List<String>,
        persisted: Set<String>
    ): List<String> = MarkdownRecentDocumentsCodec.normalize(uriStrings.filter { it in persisted })

    private fun resolveDocuments(uriStrings: List<String>): List<RecentMarkdownDocument> =
        uriStrings.map { uriString ->
            val uri = Uri.parse(uriString)
            RecentMarkdownDocument(
                uriString = uriString,
                displayName = MarkdownDocumentFileAccess.displayName(appContext, uri)
            )
        }

    private fun persistedReadUriStrings(): Set<String> = runCatching {
        resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
    }.getOrDefault(emptySet())

    private fun readUriStrings(): List<String> {
        if (!atomicFile.baseFile.isFile) return emptyList()
        val bytes = try {
            atomicFile.readFully()
        } catch (_: IOException) {
            return emptyList()
        }
        return MarkdownRecentDocumentsCodec.decode(bytes)
    }

    private fun writeUriStrings(uriStrings: List<String>) {
        atomicFile.baseFile.parentFile?.mkdirs()
        val bytes = MarkdownRecentDocumentsCodec.encode(uriStrings)
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
