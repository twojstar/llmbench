package com.twojstar.llmbench.data.document

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

internal data class OpenedTextDocument(
    val document: TextDocument,
    val displayName: String,
    val hasProviderDisplayName: Boolean,
    val mimeType: String?
)

private data class TextDocumentMetadata(
    val displayName: String?,
    val size: Long?
)

/** Shared Android SAF boundary for bounded UTF-8 text documents. */
internal object TextDocumentFileAccess {
    const val MAX_DOCUMENT_BYTES: Int = 8 * 1024 * 1024
    const val MAX_DISPLAY_NAME_CHARS: Int = 255
    private const val FALLBACK_NAME = "document.txt"
    private const val BUFFER_BYTES = 16 * 1024
    private const val MAX_PRESERVED_EXTENSION_CHARS = 16

    suspend fun import(
        context: Context,
        uri: Uri,
        fallbackName: String = FALLBACK_NAME
    ): OpenedTextDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val metadata = queryMetadata(resolver, uri)
        val providerDisplayName = metadata.displayName?.trim()?.takeIf(String::isNotEmpty)
        val bytes = readBoundedBytes(resolver, uri, metadata.size)
        OpenedTextDocument(
            document = TextDocumentCodec.decodeUtf8(bytes),
            displayName = resolveDisplayName(providerDisplayName, fallbackName),
            hasProviderDisplayName = providerDisplayName != null,
            mimeType = runCatching { resolver.getType(uri) }
                .getOrNull()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        )
    }

    suspend fun export(context: Context, uri: Uri, document: TextDocument) = withContext(Dispatchers.IO) {
        val bytes = TextDocumentCodec.encodeUtf8(document)
        ensureWithinDocumentLimit(bytes.size)
        context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IOException("Could not open the export destination for writing.")
    }

    fun displayName(context: Context, uri: Uri, fallback: String = FALLBACK_NAME): String {
        val providerName = runCatching { queryMetadata(context.contentResolver, uri).displayName }
        return providerName.fold(
            onSuccess = { resolveDisplayName(it, fallback) },
            onFailure = { resolveDisplayName(null, fallback) }
        )
    }

    internal fun resolveDisplayName(providerName: String?, fallback: String = FALLBACK_NAME): String =
        normalizeDisplayName(providerName ?: fallback, fallback)

    internal fun normalizeDisplayName(name: String, fallback: String = FALLBACK_NAME): String {
        val safeFallback = fallback.trim().ifBlank { FALLBACK_NAME }
        val candidate = name.trim().ifBlank { safeFallback }
        if (candidate.length <= MAX_DISPLAY_NAME_CHARS) return candidate

        val extensionStart = candidate.lastIndexOf('.')
        val extension = extensionStart
            .takeIf { it > 0 && candidate.length - it - 1 in 1..MAX_PRESERVED_EXTENSION_CHARS }
            ?.let(candidate::substring)
            .orEmpty()
        return candidate.take(MAX_DISPLAY_NAME_CHARS - extension.length) + extension
    }

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): TextDocumentMetadata =
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use TextDocumentMetadata(null, null)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = nameIndex
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let(cursor::getString)
            val size = sizeIndex
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let(cursor::getLong)
            TextDocumentMetadata(name, size)
        } ?: TextDocumentMetadata(null, null)

    private fun readBoundedBytes(
        resolver: ContentResolver,
        uri: Uri,
        declaredSize: Long?
    ): ByteArray {
        if (declaredSize != null && declaredSize > MAX_DOCUMENT_BYTES) throw oversizedDocument()
        val initialCapacity = declaredSize
            ?.takeIf { it in 0..MAX_DOCUMENT_BYTES.toLong() }
            ?.toInt()
            ?: BUFFER_BYTES

        return resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DOCUMENT_BYTES) throw oversizedDocument()
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException("Could not open the selected document.")
    }

    private fun ensureWithinDocumentLimit(byteCount: Int) {
        if (byteCount > MAX_DOCUMENT_BYTES) throw oversizedDocument()
    }

    private fun oversizedDocument(): IOException = IOException(
        "Document is larger than the ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MiB workspace limit."
    )
}
