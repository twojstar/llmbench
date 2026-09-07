package com.twojstar.llmbench.data.document

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class MarkdownWorkspaceRecoverySnapshot(
    val text: String,
    val hadUtf8Bom: Boolean,
    val displayName: String,
    val isDirty: Boolean
)

internal class MarkdownWorkspaceRecoveryStore(
    directory: File
) {
    private val atomicFile = AtomicFile(File(directory, FILE_NAME))
    private val mutex = Mutex()

    suspend fun load(): MarkdownWorkspaceRecoverySnapshot? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!atomicFile.baseFile.isFile) return@withContext null
            val bytes = try {
                atomicFile.readFully()
            } catch (_: IOException) {
                return@withContext null
            }
            decode(bytes)
        }
    }

    suspend fun save(snapshot: MarkdownWorkspaceRecoverySnapshot) = mutex.withLock {
        withContext(Dispatchers.IO) {
            atomicFile.baseFile.parentFile?.mkdirs()
            var output: FileOutputStream? = atomicFile.startWrite()
            try {
                val stream = requireNotNull(output)
                val data = DataOutputStream(BufferedOutputStream(stream))
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeSizedString(snapshot.text, MAX_TEXT_BYTES)
                data.writeBoolean(snapshot.hadUtf8Bom)
                data.writeSizedString(snapshot.displayName, MAX_METADATA_BYTES)
                data.writeBoolean(snapshot.isDirty)
                data.flush()
                atomicFile.finishWrite(stream)
                output = null
            } catch (error: IOException) {
                output?.let(atomicFile::failWrite)
                throw error
            }
        }
    }

    private fun decode(bytes: ByteArray): MarkdownWorkspaceRecoverySnapshot? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
            MarkdownWorkspaceRecoverySnapshot(
                text = input.readSizedString(MAX_TEXT_BYTES),
                hadUtf8Bom = input.readBoolean(),
                displayName = input.readSizedString(MAX_METADATA_BYTES),
                isDirty = input.readBoolean()
            )
        }
    } catch (_: IOException) {
        null
    }

    private fun DataOutputStream.writeSizedString(value: String, maxBytes: Int) {
        val bytes = value.encodeToByteArray()
        if (bytes.size > maxBytes) throw IOException("Markdown recovery payload is too large.")
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(maxBytes: Int): String {
        val size = readInt()
        if (size !in 0..maxBytes) throw IOException("Invalid Markdown recovery field size.")
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }

    private companion object {
        const val MAGIC = 0x4C4D4257
        const val VERSION = 2
        const val FILE_NAME = "markdown-workspace-recovery-v2.bin"
        const val MAX_TEXT_BYTES = MarkdownDocumentFileAccess.MAX_DOCUMENT_BYTES
        const val MAX_METADATA_BYTES = 256 * 1024
    }
}
