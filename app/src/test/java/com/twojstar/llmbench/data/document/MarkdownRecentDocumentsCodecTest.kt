package com.twojstar.llmbench.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.IOException

class MarkdownRecentDocumentsCodecTest {
    private companion object {
        const val URI_ONE = "content://one"
        const val URI_TWO = "content://two"
        const val URI_THREE = "content://three"
        const val URI_NEW = "content://new"
        const val LEGACY_MAGIC = 0x4C4D4252
    }

    @Test
    fun promoteDeduplicatesAndPromotesExistingUri() {
        val result = MarkdownRecentDocumentsCodec.promote(
            listOf(entry(URI_ONE), entry(URI_TWO), entry(URI_THREE)),
            URI_TWO
        )

        assertEquals(listOf(entry(URI_TWO), entry(URI_ONE), entry(URI_THREE)), result)
    }

    @Test
    fun promoteCapsTheRecentListAndReportsEviction() {
        val existing = (1..MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS)
            .map { entry("content://doc/$it") }

        val result = MarkdownRecentDocumentsCodec.promote(existing, URI_NEW)

        assertEquals(MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS, result.size)
        assertEquals(URI_NEW, result.first().uriString)
        assertEquals(
            listOf("content://doc/${MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS}"),
            MarkdownRecentDocumentsCodec.evictedFrom(existing, result)
        )
    }

    @Test
    fun pinnedShortcutsStayAheadAndSurviveRecentEviction() {
        val existing = listOf(entry("content://pinned", isPinned = true)) +
            (1 until MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS).map { entry("content://doc/$it") }

        val result = MarkdownRecentDocumentsCodec.promote(existing, URI_NEW)

        assertEquals("content://pinned", result.first().uriString)
        assertTrue(result.first().isPinned)
        assertEquals(URI_NEW, result[1].uriString)
        assertFalse(result.any { it.uriString == "content://doc/7" })
    }

    @Test
    fun allPinnedShortcutsRejectAnotherRecentEntry() {
        val existing = (1..MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS)
            .map { entry("content://pinned/$it", isPinned = true) }

        assertEquals(existing, MarkdownRecentDocumentsCodec.promote(existing, URI_NEW))
    }

    @Test
    fun setPinnedMovesShortcutIntoPinnedGroup() {
        val existing = listOf(entry(URI_ONE), entry(URI_TWO), entry(URI_THREE))

        val result = MarkdownRecentDocumentsCodec.setPinned(existing, URI_TWO, isPinned = true)

        assertEquals(URI_TWO, result.first().uriString)
        assertTrue(result.first().isPinned)
        assertEquals(listOf(URI_ONE, URI_THREE), result.drop(1).map { it.uriString })
    }

    @Test
    fun staleRapidPinTapsReconcileAsSeparateToggles() {
        val snapshotPinned = false
        val requestedPinned = true
        val afterFirstTap = reconcilePinnedToggle(
            storedPinned = false,
            snapshotPinned = snapshotPinned,
            requestedPinned = requestedPinned
        )
        val afterSecondTap = reconcilePinnedToggle(
            storedPinned = afterFirstTap,
            snapshotPinned = snapshotPinned,
            requestedPinned = requestedPinned
        )

        assertTrue(afterFirstTap)
        assertFalse(afterSecondTap)
    }

    @Test
    fun codecRoundTripsPinnedStateWithoutDelimiterAssumptions() {
        val values = listOf(
            entry("content://provider/document/a%2Fb", isPinned = true),
            entry("content://provider/document/name?query=a%20b&x=1"),
            entry("content://provider/document/unicode-%E2%98%85")
        )

        assertEquals(values, MarkdownRecentDocumentsCodec.decode(MarkdownRecentDocumentsCodec.encode(values)))
    }

    @Test
    fun legacyCodecEntriesMigrateAsUnpinned() {
        val values = listOf(URI_ONE, URI_TWO)

        assertEquals(values.map(::entry), MarkdownRecentDocumentsCodec.decode(legacyPayload(values)))
    }

    @Test
    fun decodeRejectsMalformedPayload() {
        assertTrue(MarkdownRecentDocumentsCodec.decode(byteArrayOf(1, 2, 3, 4)).isEmpty())
    }

    @Test
    fun onlyDefinitiveAccessFailuresForgetRecentDocuments() {
        assertTrue(shouldForgetRecentDocumentAfterOpenFailure(FileNotFoundException()))
        assertTrue(shouldForgetRecentDocumentAfterOpenFailure(SecurityException()))
        assertFalse(shouldForgetRecentDocumentAfterOpenFailure(IOException("temporary provider failure")))
    }

    private fun entry(uriString: String, isPinned: Boolean = false) =
        MarkdownRecentDocumentEntry(uriString = uriString, isPinned = isPinned)

    private fun legacyPayload(values: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(LEGACY_MAGIC)
            data.writeInt(1)
            data.writeInt(values.size)
            values.forEach { value ->
                val bytes = value.encodeToByteArray()
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }
}
