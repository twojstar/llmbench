package com.twojstar.llmbench.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRecentDocumentsCodecTest {
    @Test
    fun moveToFrontDeduplicatesAndPromotesExistingUri() {
        val result = MarkdownRecentDocumentsCodec.moveToFront(
            listOf("content://one", "content://two", "content://three"),
            "content://two"
        )

        assertEquals(listOf("content://two", "content://one", "content://three"), result)
    }

    @Test
    fun moveToFrontCapsTheRecentList() {
        val existing = (1..12).map { "content://doc/$it" }

        val result = MarkdownRecentDocumentsCodec.moveToFront(existing, "content://new")

        assertEquals(MarkdownRecentDocumentsCodec.MAX_RECENT_DOCUMENTS, result.size)
        assertEquals("content://new", result.first())
        assertTrue("content://doc/8" !in result)
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
}
