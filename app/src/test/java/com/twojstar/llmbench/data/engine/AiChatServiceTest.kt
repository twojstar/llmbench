package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.ModelChatMessage
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
}
