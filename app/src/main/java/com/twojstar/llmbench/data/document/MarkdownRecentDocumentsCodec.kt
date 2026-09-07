package com.twojstar.llmbench.data.document

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

internal object MarkdownRecentDocumentsCodec {
    const val MAX_RECENT_DOCUMENTS = 8
    private const val MAGIC = 0x4C4D4252
    private const val VERSION = 1
    private const val MAX_URI_BYTES = 16 * 1024

    fun moveToFront(
        existing: List<String>,
        uriString: String,
        limit: Int = MAX_RECENT_DOCUMENTS
    ): List<String> {
        require(limit > 0)
        val result = ArrayList<String>(limit)
        if (uriString.isNotBlank()) result += uriString
        for (candidate in existing) {
            if (candidate.isBlank() || candidate in result) continue
            result += candidate
            if (result.size >= limit) break
        }
        return result
    }

    fun normalize(
        uriStrings: List<String>,
        limit: Int = MAX_RECENT_DOCUMENTS
    ): List<String> {
        require(limit > 0)
        val result = ArrayList<String>(limit)
        for (candidate in uriStrings) {
            if (candidate.isBlank() || candidate in result) continue
            result += candidate
            if (result.size >= limit) break
        }
        return result
    }

    fun encode(uriStrings: List<String>): ByteArray {
        val normalized = normalize(uriStrings)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(normalized.size)
            normalized.forEach { value ->
                val bytes = value.encodeToByteArray()
                if (bytes.size > MAX_URI_BYTES) throw IOException("Recent document URI is too large.")
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<String> = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return emptyList()
            val count = input.readInt()
            if (count !in 0..MAX_RECENT_DOCUMENTS) return emptyList()
            val values = buildList(count) {
                repeat(count) {
                    val size = input.readInt()
                    if (size !in 1..MAX_URI_BYTES) return emptyList()
                    val value = ByteArray(size)
                    input.readFully(value)
                    add(value.decodeToString(throwOnInvalidSequence = true))
                }
            }
            if (input.available() != 0) return emptyList()
            normalize(values)
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}
