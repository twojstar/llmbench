package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMarkdownImportTest {
    @Test
    fun roundTripsBodiesThatLookLikeChatFraming() {
        val userBody = "Zażółć 😀\n\n## You\n\nnot a new turn\n$CHAT_MARKDOWN_MESSAGE_END"
        val assistantBody = "Answer with trailing newline\n"
        val markdown = assertNotNull(
            renderChatMarkdown(
                listOf(
                    ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = userBody),
                    ModelChatMessage(
                        id = "a1",
                        sender = CHAT_ROLE_ASSISTANT,
                        provider = AiProvider.CHATGPT,
                        modelName = "gpt-5.6",
                        text = assistantBody
                    )
                )
            )
        )

        val result = parseChatMarkdown(markdown)
        val chat = assertNotNull(result.chat)

        assertTrue(result.isValid)
        assertEquals(CHAT_MARKDOWN_VERSION, chat.version)
        assertNotSame(chat.turns, chat.turns)
        assertEquals(2, chat.turns.size)
        assertEquals(CHAT_ROLE_USER, chat.turns[0].role)
        assertEquals("You", chat.turns[0].displayHeading)
        assertEquals(userBody, chat.turns[0].text)
        assertFalse(userBody in chat.turns[0].toString())
        assertTrue("text=<redacted>" in chat.turns[0].toString())
        assertEquals(CHAT_ROLE_ASSISTANT, chat.turns[1].role)
        assertEquals("OpenAI · gpt-5.6", chat.turns[1].displayHeading)
        assertEquals(assistantBody, chat.turns[1].text)
    }

    @Test
    fun rejectsLegacyUnframedMarkdownInsteadOfGuessingTurnBoundaries() {
        val legacy = "# LlmBench chat\n\n## You\n\nQuestion\n\n## Assistant\n\nAnswer\n"

        val result = parseChatMarkdown(legacy)

        assertFalse(result.isValid)
        assertNull(result.chat)
        assertNotNull(result.error)
    }

    @Test
    fun rejectsTamperedBodyLengthWithoutScanningInsideBodyForRecovery() {
        val body = "safe body"
        val bodyUtf8Bytes = body.encodeToByteArray().size
        val markdown = assertNotNull(
            renderChatMarkdown(
                listOf(ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = body))
            )
        )
        val tampered = markdown.replace(
            "bytes=$bodyUtf8Bytes",
            "bytes=${bodyUtf8Bytes + 1}"
        )

        val result = parseChatMarkdown(tampered)

        assertFalse(result.isValid)
        assertNull(result.chat)
        assertNotNull(result.error)
    }

    @Test
    fun rejectsUnexpectedTrailingContentAfterACompleteFrame() {
        val markdown = assertNotNull(
            renderChatMarkdown(
                listOf(ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = "Question"))
            )
        )

        val result = parseChatMarkdown(markdown + "trailing")

        assertFalse(result.isValid)
        assertNull(result.chat)
        assertNotNull(result.error)
    }
}
