package com.twojstar.llmbench.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingShareTest {
    @Test
    fun normalizesTextAndKeepsOnlyDistinctContentUris() {
        val payload = normalizeIncomingSharePayload(
            text = "  shared text  ",
            uriStrings = listOf(
                "content://example/document/1",
                " content://example/document/1 ",
                "CONTENT://example/document/2",
                "file:///sdcard/private.txt",
                "https://example.com/file.pdf"
            )
        )

        requireNotNull(payload)
        assertEquals("shared text", payload.text)
        assertEquals(
            listOf("content://example/document/1", "CONTENT://example/document/2"),
            payload.uriStrings
        )
        assertEquals(2, payload.attachmentCount)
    }

    @Test
    fun rejectsEmptyOrUnsupportedSharePayload() {
        assertNull(normalizeIncomingSharePayload("   ", emptyList()))
        assertNull(normalizeIncomingSharePayload(null, listOf("file:///tmp/nope")))
    }
}
