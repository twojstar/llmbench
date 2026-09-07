package com.twojstar.llmbench.data.document

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

internal data class MarkdownRecentDocumentEntry(
    val uriString: String,
    val isPinned: Boolean = false
)

internal object MarkdownRecentDocumentsCodec {
    const val MAX_RECENT_DOCUMENTS = 8
    private const val MAGIC = 0x4C4D4252
    private const val LEGACY_VERSION = 1
    private const val VERSION = 2
    private const val MAX_URI_BYTES = 16 * 1024

    fun promote(
        existing: List<MarkdownRecentDocumentEntry>,
        uriString: String,
        limit: Int = MAX_RECENT_DOCUMENTS
    ): List<MarkdownRecentDocumentEntry> {
        require(limit > 0)
        val current = normalize(existing, limit)
        if (uriString.isBlank()) return current
        val promoted = MarkdownRecentDocumentEntry(
            uriString = uriString,
            isPinned = current.firstOrNull { it.uriString == uriString }?.isPinned == true
        )
        return normalize(
            listOf(promoted) + current.filterNot { it.uriString == uriString },
            limit
        )
    }

    fun setPinned(
        existing: List<MarkdownRecentDocumentEntry>,
        uriString: String,
        isPinned: Boolean,
        limit: Int = MAX_RECENT_DOCUMENTS
    ): List<MarkdownRecentDocumentEntry> {
        require(limit > 0)
        val current = normalize(existing, limit)
        val target = current.firstOrNull { it.uriString == uriString } ?: return current
        if (target.isPinned == isPinned) return current
        val updated = target.copy(isPinned = isPinned)
        return normalize(
            listOf(updated) + current.filterNot { it.uriString == uriString },
            limit
        )
    }

    fun normalize(
        entries: List<MarkdownRecentDocumentEntry>,
        limit: Int = MAX_RECENT_DOCUMENTS
    ): List<MarkdownRecentDocumentEntry> {
        require(limit > 0)
        val seen = HashSet<String>()
        val unique = entries.filter { entry ->
            entry.uriString.isNotBlank() && seen.add(entry.uriString)
        }
        return buildList(limit) {
            unique.filter(MarkdownRecentDocumentEntry::isPinned).forEach { entry ->
                if (size < limit) add(entry)
            }
            unique.filterNot(MarkdownRecentDocumentEntry::isPinned).forEach { entry ->
                if (size < limit) add(entry)
            }
        }
    }

    fun evictedFrom(
        previous: List<MarkdownRecentDocumentEntry>,
        next: List<MarkdownRecentDocumentEntry>
    ): List<String> {
        val retained = next.mapTo(HashSet(), MarkdownRecentDocumentEntry::uriString)
        return normalize(previous)
            .map(MarkdownRecentDocumentEntry::uriString)
            .filterNot(retained::contains)
    }

    fun encode(entries: List<MarkdownRecentDocumentEntry>): ByteArray {
        val normalized = normalize(entries)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(normalized.size)
            normalized.forEach { entry ->
                data.writeBoolean(entry.isPinned)
                writeUri(data, entry.uriString)
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<MarkdownRecentDocumentEntry> = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) return emptyList()
            when (input.readInt()) {
                LEGACY_VERSION -> decodeLegacy(input)
                VERSION -> decodeCurrent(input)
                else -> emptyList()
            }
        }
    } catch (_: IOException) {
        emptyList()
    }

    private fun decodeLegacy(input: DataInputStream): List<MarkdownRecentDocumentEntry> {
        val entries = buildList(readCount(input)) {
            repeat(capacity) {
                add(MarkdownRecentDocumentEntry(readUri(input)))
            }
        }
        if (input.available() != 0) return emptyList()
        return normalize(entries)
    }

    private fun decodeCurrent(input: DataInputStream): List<MarkdownRecentDocumentEntry> {
        val entries = buildList(readCount(input)) {
            repeat(capacity) {
                add(
                    MarkdownRecentDocumentEntry(
                        uriString = readUri(input),
                        isPinned = input.readBoolean()
                    )
                )
            }
        }
        if (input.available() != 0) return emptyList()
        return normalize(entries)
    }

    private fun readCount(input: DataInputStream): Int {
        val count = input.readInt()
        if (count !in 0..MAX_RECENT_DOCUMENTS) throw IOException("Invalid recent document count.")
        return count
    }

    private fun writeUri(output: DataOutputStream, value: String) {
        val bytes = value.encodeToByteArray()
        if (bytes.size > MAX_URI_BYTES) throw IOException("Recent document URI is too large.")
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readUri(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 1..MAX_URI_BYTES) throw IOException("Invalid recent document URI size.")
        val value = ByteArray(size)
        input.readFully(value)
        return value.decodeToString(throwOnInvalidSequence = true)
    }
}
