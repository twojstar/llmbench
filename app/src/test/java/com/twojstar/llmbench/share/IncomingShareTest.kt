package com.twojstar.llmbench.share

import android.content.Intent
import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingShareTest {
    private companion object {
        const val BLANK_TEXT = "   "
        const val CLIP_TEXT = "clip text"
        const val SHARED_TEXT = "shared text"
        const val PRIMARY_TEXT = "primary"
        const val FIRST_TEXT = "first"
        const val SECOND_TEXT = "second"
        const val MULTIPLE_TEXT = "$FIRST_TEXT\n$SECOND_TEXT"
        const val CONTENT_URI = "content://example/document/1"
        const val UPPERCASE_CONTENT_URI = "CONTENT://example/document/2"
    }

    @Test
    fun normalizesTextAndKeepsOnlyDistinctContentUris() {
        val payload = normalizeIncomingSharePayload(
            text = "  $SHARED_TEXT  ",
            uriStrings = listOf(
                CONTENT_URI,
                " $CONTENT_URI ",
                UPPERCASE_CONTENT_URI,
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
            selectIncomingShareText(
                action = Intent.ACTION_SEND,
                singleText = BLANK_TEXT,
                multipleTexts = emptyList(),
                clipTexts = listOf(CLIP_TEXT)
            )
        )
        assertEquals(
            PRIMARY_TEXT,
            selectIncomingShareText(
                action = Intent.ACTION_SEND,
                singleText = PRIMARY_TEXT,
                multipleTexts = emptyList(),
                clipTexts = listOf(CLIP_TEXT)
            )
        )
    }

    @Test
    fun sendMultipleKeepsAllSharedText() {
        val text = selectIncomingShareText(
            action = Intent.ACTION_SEND_MULTIPLE,
            singleText = null,
            multipleTexts = listOf(FIRST_TEXT, SECOND_TEXT),
            clipTexts = emptyList()
        )

        assertEquals(MULTIPLE_TEXT, text)
    }

    @Test
    fun sendMultipleKeepsTextAlongsideAttachments() {
        val text = selectIncomingShareText(
            action = Intent.ACTION_SEND_MULTIPLE,
            singleText = null,
            multipleTexts = listOf(FIRST_TEXT, SECOND_TEXT),
            clipTexts = emptyList()
        )
        val payload = normalizeIncomingSharePayload(text, listOf(CONTENT_URI))

        requireNotNull(payload)
        assertEquals(MULTIPLE_TEXT, payload.text)
        assertEquals(listOf(CONTENT_URI), payload.uriStrings)
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
        assertNull(normalizeIncomingSharePayload(BLANK_TEXT, emptyList()))
        assertNull(normalizeIncomingSharePayload(null, listOf("file:///tmp/nope")))
        assertNull(normalizeIncomingSharePayload(null, listOf(UPPERCASE_CONTENT_URI)))
    }
}
