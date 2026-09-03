package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.ModelChatMessage
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatServiceTest {
    @Test
    fun compatibleHistoryKeepsOnlyCurrentProviderAssistantTurns() {
        val history = listOf(
            ModelChatMessage(
                id = "welcome",
                sender = "assistant",
                provider = AiProvider.ALL,
                text = "welcome"
            ),
            ModelChatMessage(id = "user-1", sender = "user", text = "first"),
            ModelChatMessage(
                id = "or-1",
                sender = "assistant",
                provider = AiProvider.OPENROUTER,
                text = "openrouter answer"
            ),
            ModelChatMessage(
                id = "ds-1",
                sender = "assistant",
                provider = AiProvider.DEEPSEEK,
                text = "deepseek answer"
            ),
            ModelChatMessage(id = "user-2", sender = "user", text = "follow up")
        )

        val messages = AiChatService().buildOpenAiCompatibleMessages(
            prompt = "follow up",
            systemInstruction = "system",
            conversationHistory = history,
            provider = AiProvider.OPENROUTER
        )

        assertEquals(listOf("system", "user", "assistant", "user"), messages.map {
            it.jsonObject.getValue("role").jsonPrimitive.content
        })
        assertEquals(listOf("system", "first", "openrouter answer", "follow up"), messages.map {
            it.jsonObject.getValue("content").jsonPrimitive.content
        })
    }

    @Test
    fun providerHistoryExcludesDiagnosticsAndSimulations() {
        val userSender = "user"
        val assistantSender = "assistant"
        val kimiModel = "kimi-k2.6"
        val liveAnswer = "live answer"
        val apiFailure = "API failed"
        val simulatedAnswer = "simulated answer"
        val otherProviderAnswer = "other provider answer"
        val nextQuestion = "next question"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = userSender, text = "first question"),
            ModelChatMessage(
                id = "live", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = liveAnswer
            ),
            ModelChatMessage(
                id = "error", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = apiFailure, isError = true
            ),
            ModelChatMessage(
                id = "sim", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = simulatedAnswer, isSimulated = true
            ),
            ModelChatMessage(
                id = "other", sender = assistantSender, provider = AiProvider.DEEPSEEK,
                modelName = "deepseek-v4-flash", text = otherProviderAnswer
            ),
            ModelChatMessage(id = "u2", sender = userSender, text = nextQuestion)
        )

        val messages = AiChatService().buildOpenAiCompatibleMessages(
            prompt = nextQuestion,
            systemInstruction = null,
            conversationHistory = history,
            provider = AiProvider.KIMI
        )
        val contents = messages.map { it.jsonObject.getValue("content").jsonPrimitive.content }
        val roles = messages.map { it.jsonObject.getValue("role").jsonPrimitive.content }

        assertEquals(listOf(userSender, assistantSender, userSender), roles)
        assertEquals(listOf("first question", liveAnswer, nextQuestion), contents)
        assertFalse(apiFailure in contents)
        assertFalse(simulatedAnswer in contents)
        assertFalse(otherProviderAnswer in contents)
        assertTrue(liveAnswer in contents)
    }
}
