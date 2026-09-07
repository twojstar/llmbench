package com.twojstar.llmbench.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class MarkdownRecentDocumentsCodecTest {
    private companion object {
        const val URI_TWO = "content://two"
    }

    @Test
    fun moveToFrontDeduplicatesAndPromotesExistingUri() {
        val result = MarkdownRecentDocumentsCodec.moveToFront(
            listOf("content://one", URI_TWO, "content://three"),
            URI_TWO
        )

        assertEquals(listOf(URI_TWO, "content://one", "content://three"), result)
    }

    @Test
    fun moveToFrontCapsTheRecentListAndReportsEviction() {
        val existing = (1..MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS)
            .map { "content://doc/$it" }

        val result = MarkdownRecentDocumentsCodec.moveToFront(existing, "content://new")

        assertEquals(MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS, result.size)
        assertEquals("content://new", result.first())
        assertEquals(
            listOf("content://doc/${MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS}"),
            MarkdownRecentDocumentsCodec.evictedFrom(existing, result)
        )
    }

    @Test
    fun codecRoundTripsUriStringsWithoutDelimiterAssumptions() {
        val values = listOf(
            "content://provider/document/a%2Fb",
            "content://provider/document/name?query=a%20b&x=1",
            "content://provider/document/unicode-%E2%98%85"
        )

        assertEquals(values, MarkdownRecentDocumentsCodec.decode(MarkdownRecentDocumentsCodec.encode(values)))
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
}
