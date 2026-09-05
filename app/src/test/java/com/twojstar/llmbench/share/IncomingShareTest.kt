package com.twojstar.llmbench.share

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingShareTest {
    private companion object {
        const val CLIP_TEXT = "clip text"
        const val SHARED_TEXT = "shared text"
        const val CONTENT_URI = "content://example/document/1"
    }

    @Test
    fun normalizesTextAndKeepsOnlyDistinctContentUris() {
        val payload = normalizeIncomingSharePayload(
            text = "  $SHARED_TEXT  ",
            uriStrings = listOf(
                CONTENT_URI,
                " $CONTENT_URI ",
                "CONTENT://example/document/2",
                "file:///sdcard/private.txt",
                "https://example.com/file.pdf"
            )
        )

        requireNotNull(payload)
        assertEquals(SHARED_TEXT, payload.text)
        assertEquals(
            listOf(CONTENT_URI),
            payload.uriStrings
        )
        assertEquals(1, payload.attachmentCount)
    }

    @Test
    fun blankPrimaryTextFallsBackToClipText() {
        assertEquals(
            CLIP_TEXT,
            selectIncomingShareText("   ", listOf(CLIP_TEXT))
        )
        assertEquals(
            "primary",
            selectIncomingShareText("primary", listOf(CLIP_TEXT))
        )
    }

    @Test
    fun textClaimKeepsTextUntilCompletionAndCanBeReleased() {
        val pending = PendingWebShare(
            id = 7L,
            service = WebAiService.CLAUDE,
            payload = IncomingSharePayload(
                text = SHARED_TEXT,
                uriStrings = listOf(CONTENT_URI)
            )
        )

        val claimed = requireNotNull(pending.claimText())
        assertTrue(claimed.isTextClaimed)
        assertEquals(SHARED_TEXT, claimed.payload.text)
        assertNull(claimed.claimText())

        val released = claimed.releaseTextClaim()
        assertFalse(released.isTextClaimed)
        assertEquals(SHARED_TEXT, released.payload.text)

        val completed = requireNotNull(requireNotNull(released.claimText()).completeTextClaim())
        assertFalse(completed.isTextClaimed)
        assertNull(completed.payload.text)
        assertEquals(listOf(CONTENT_URI), completed.payload.uriStrings)
    }

    @Test
    fun completingClaimRemovesTextOnlyPendingShare() {
        val pending = PendingWebShare(
            id = 8L,
            service = WebAiService.CLAUDE,
            payload = IncomingSharePayload(text = SHARED_TEXT)
        )

        assertNull(requireNotNull(pending.claimText()).completeTextClaim())
    }

    @Test
    fun rejectsEmptyOrUnsupportedSharePayload() {
        assertNull(normalizeIncomingSharePayload("   ", emptyList()))
        assertNull(normalizeIncomingSharePayload(null, listOf("file:///tmp/nope")))
        assertNull(normalizeIncomingSharePayload(null, listOf("CONTENT://example/document/2")))
    }
}
