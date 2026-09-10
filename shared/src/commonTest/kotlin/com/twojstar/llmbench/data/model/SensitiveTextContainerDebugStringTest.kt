package com.twojstar.llmbench.data.model

import com.twojstar.llmbench.data.document.LineEndingCounts
import com.twojstar.llmbench.data.document.TextDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveTextContainerDebugStringTest {
    @Test
    fun providerTurnDebugStringRedactsConversationAndReplayPayloads() {
        val turn = ProviderTextTurn(
            role = CHAT_ROLE_ASSISTANT,
            text = PRIVATE_TEXT,
            providerReplayState = PRIVATE_REPLAY,
            modelName = PRIVATE_MODEL
        )

        val debug = turn.toString()

        assertFalse(PRIVATE_TEXT in debug)
        assertFalse(PRIVATE_REPLAY in debug)
        assertFalse(PRIVATE_MODEL in debug)
        assertTrue("role=assistant" in debug)
        assertTrue("text=<redacted>" in debug)
        assertTrue("providerReplayState=<redacted>" in debug)
    }

    @Test
    fun providerTurnDebugStringDoesNotExposeArbitraryRoleValues() {
        val debug = ProviderTextTurn(
            role = PRIVATE_ROLE,
            text = PRIVATE_TEXT
        ).toString()

        assertFalse(PRIVATE_ROLE in debug)
        assertTrue("role=other" in debug)
    }

    @Test
    fun textDocumentDebugStringKeepsOnlyStructuralMetadata() {
        val document = TextDocument(
            text = PRIVATE_DOCUMENT,
            hadUtf8Bom = true,
            lineEndings = LineEndingCounts(lf = 2, crlf = 1, cr = 0)
        )

        val debug = document.toString()

        assertFalse(PRIVATE_DOCUMENT in debug)
        assertTrue("text=<redacted>" in debug)
        assertTrue("hadUtf8Bom=true" in debug)
        assertTrue("LineEndingCounts(lf=2, crlf=1, cr=0)" in debug)
    }

    private companion object {
        const val PRIVATE_TEXT = "customer private conversation"
        const val PRIVATE_REPLAY = "opaque replay state"
        const val PRIVATE_MODEL = "private model route"
        const val PRIVATE_ROLE = "secret-role-payload"
        const val PRIVATE_DOCUMENT = "private document body\nsecond line"
    }
}
