package com.twojstar.llmbench.data.document

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocbenchPendingExportStoreTest {
    @Test
    fun roundTripPreservesTextBomAndLineEndings() {
        val directory = Files.createTempDirectory("docbench-export-test").toFile()
        try {
            val store = DocbenchPendingExportStore(directory)
            val source = TextDocumentCodec.decodeUtf8(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    "a\r\nb\nc".encodeToByteArray()
            )

            val id = store.save(source)
            val restored = requireNotNull(store.load(id))

            assertEquals(source.text, restored.text)
            assertEquals(source.hadUtf8Bom, restored.hadUtf8Bom)
            assertEquals(source.lineEndings, restored.lineEndings)

            store.delete(id)
            assertNull(store.load(id))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pruningKeepsOnlyTheRestoredPendingExport() {
        val directory = Files.createTempDirectory("docbench-export-test").toFile()
        try {
            val store = DocbenchPendingExportStore(directory)
            val firstId = store.save(TextDocumentCodec.decodeUtf8("first".encodeToByteArray()))
            val preservedId = store.save(TextDocumentCodec.decodeUtf8("second".encodeToByteArray()))
            directory.resolve("$firstId.tmp").writeText("stale")

            store.pruneOrphans(preservedId)

            assertNull(store.load(firstId))
            assertEquals("second", requireNotNull(store.load(preservedId)).text)
            assertTrue(directory.listFiles().orEmpty().none { it.extension == "tmp" })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsUntrustedIdentifiersOutsideTheStoreDirectory() {
        val directory = Files.createTempDirectory("docbench-export-test").toFile()
        try {
            val store = DocbenchPendingExportStore(directory)
            val outside = directory.parentFile.resolve("outside.bin")
            outside.writeText("keep")

            assertNull(store.load("../outside"))
            store.delete("../outside")
            assertTrue(outside.isFile)
            assertEquals("keep", outside.readText())
        } finally {
            directory.parentFile.resolve("outside.bin").delete()
            directory.deleteRecursively()
        }
    }
}
