package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConversationHistoryTest {
    @Test
    fun keepsOnlyCurrentProviderLiveAssistantTurns() {
        val prompt = "follow up"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = "first"),
            ModelChatMessage(
                id = "gpt", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CHATGPT,
                text = "openai answer"
            ),
            ModelChatMessage(
                id = "claude", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CLAUDE,
                text = "claude answer"
            ),
            ModelChatMessage(
                id = "sim", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CHATGPT,
                text = "simulated", isSimulated = true
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = prompt)
        )

        val turns = buildBoundedProviderTextTurns(prompt, history, AiProvider.CHATGPT)

        assertEquals(
            listOf(CHAT_ROLE_USER, CHAT_ROLE_ASSISTANT, CHAT_ROLE_USER),
            turns.map { it.role }
        )
        assertEquals(listOf("first", "openai answer", prompt), turns.map { it.text })
        assertFalse(turns.any { it.text == "claude answer" || it.text == "simulated" })
    }

    @Test
    fun switchingProviderDoesNotReplayUnseenUserPrompts() {
        val prompt = "ask claude now"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = "private chatgpt prompt"),
            ModelChatMessage(
                id = "gpt", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CHATGPT,
                text = "chatgpt answer"
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = prompt)
        )

        val turns = buildBoundedProviderTextTurns(prompt, history, AiProvider.CLAUDE)

        assertEquals(listOf(prompt), turns.map { it.text })
    }

    @Test
    fun truncationKeepsNewestCompleteTurnsWithoutOrphanAssistant() {
        val prompt = "new prompt"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = "old user"),
            ModelChatMessage(
                id = "a1", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CLAUDE,
                text = "old assistant"
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = "recent user"),
            ModelChatMessage(
                id = "a2", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CLAUDE,
                text = "recent assistant"
            ),
            ModelChatMessage(id = "u3", sender = CHAT_ROLE_USER, text = prompt)
        )

        val recentSegmentCost = "recent user".length + "recent assistant".length + (2 * 32)
        val turns = buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = history,
            provider = AiProvider.CLAUDE,
            maxHistoryCharacters = prompt.length + recentSegmentCost,
            maxHistoryTurns = 8
        )

        assertEquals(
            listOf("recent user", "recent assistant", prompt),
            turns.map { it.text }
        )
        assertEquals(CHAT_ROLE_USER, turns.first().role)
    }

    @Test
    fun systemInstructionConsumesHistoryBudget() {
        val prompt = "next"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = "previous"),
            ModelChatMessage(
                id = "a1", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.GEMINI,
                text = "answer"
            )
        )
        val segmentCost = "previous".length + "answer".length + (2 * 32)

        val turns = buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = history,
            provider = AiProvider.GEMINI,
            systemInstruction = "system",
            maxHistoryCharacters = prompt.length + segmentCost,
            maxHistoryTurns = 8
        )

        assertEquals(listOf(prompt), turns.map { it.text })
    }
}
