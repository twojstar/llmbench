package com.twojstar.llmbench.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDocumentFileAccessTest {
    @Test
    fun genericAndMarkdownFallbacksStayDistinct() {
        assertEquals("document.txt", TextDocumentFileAccess.normalizeDisplayName("   "))
        assertEquals("document.md", MarkdownDocumentFileAccess.normalizeDisplayName("   "))
        assertEquals(
            "document.md",
            MarkdownDocumentFileAccess.normalizeDisplayName("   ", fallback = "   ")
        )
    }

    @Test
    fun normalizationPreservesShortExtensionWithinDisplayLimit() {
        val normalized = TextDocumentFileAccess.normalizeDisplayName(
            "a".repeat(300) + ".json"
        )

        assertEquals(TextDocumentFileAccess.MAX_DISPLAY_NAME_CHARS, normalized.length)
        assertTrue(normalized.endsWith(".json"))
    }

    @Test
    fun markdownAdapterUsesTheSameDocumentLimits() {
        assertEquals(TextDocumentFileAccess.MAX_DOCUMENT_BYTES, MarkdownDocumentFileAccess.MAX_DOCUMENT_BYTES)
        assertEquals(
            TextDocumentFileAccess.MAX_DISPLAY_NAME_CHARS,
            MarkdownDocumentFileAccess.MAX_DISPLAY_NAME_CHARS
        )
    }
}
