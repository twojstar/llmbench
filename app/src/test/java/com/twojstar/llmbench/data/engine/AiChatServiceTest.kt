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
        val history = listOf(
            ModelChatMessage(id = "u1", sender = "user", text = "first question"),
            ModelChatMessage(
                id = "live", sender = "assistant", provider = AiProvider.KIMI,
                modelName = "kimi-k2.6", text = "live answer"
            ),
            ModelChatMessage(
                id = "error", sender = "assistant", provider = AiProvider.KIMI,
                modelName = "kimi-k2.6", text = "API failed", isError = true
            ),
            ModelChatMessage(
                id = "sim", sender = "assistant", provider = AiProvider.KIMI,
                modelName = "kimi-k2.6", text = "simulated answer", isSimulated = true
            ),
            ModelChatMessage(
                id = "other", sender = "assistant", provider = AiProvider.DEEPSEEK,
                modelName = "deepseek-v4-flash", text = "other provider answer"
            ),
            ModelChatMessage(id = "u2", sender = "user", text = "next question")
        )

        val messages = AiChatService().buildOpenAiCompatibleMessages(
            prompt = "next question",
            systemInstruction = null,
            conversationHistory = history,
            provider = AiProvider.KIMI
        )
        val contents = messages.map { it.jsonObject.getValue("content").jsonPrimitive.content }
        val roles = messages.map { it.jsonObject.getValue("role").jsonPrimitive.content }

        assertEquals(listOf("user", "assistant", "user"), roles)
        assertEquals(listOf("first question", "live answer", "next question"), contents)
        assertFalse("API failed" in contents)
        assertFalse("simulated answer" in contents)
        assertFalse("other provider answer" in contents)
        assertTrue("live answer" in contents)
    }
}
