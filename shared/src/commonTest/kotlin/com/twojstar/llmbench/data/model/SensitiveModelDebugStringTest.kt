package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveModelDebugStringTest {
    @Test
    fun apiKeyConfigDebugStringNeverIncludesCredentials() {
        val secrets = listOf(
            "gemini-secret-demo",
            "sk-proj-demo-never-log",
            "claude-secret-demo",
            "deepseek-secret-demo",
            "kimi-secret-demo",
            "openrouter-secret-demo",
            "aihubmix-secret-demo"
        )
        val config = ApiKeyConfig(
            geminiKey = secrets[0],
            openAiKey = secrets[1],
            claudeKey = secrets[2],
            deepseekKey = secrets[3],
            kimiKey = secrets[4],
            openRouterKey = secrets[5],
            aiHubMixKey = secrets[6]
        )

        val debug = config.toString()

        secrets.forEach { secret -> assertFalse(secret in debug) }
        assertTrue("<redacted>" in debug)
    }

    @Test
    fun chatMessageDebugStringRedactsUserAndOpaqueProviderState() {
        val message = ModelChatMessage(
            id = "message-123",
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.CHATGPT,
            modelName = "private-model-route",
            text = "customer confidential prompt response",
            activeProfileNotes = listOf("private profile instructions"),
            providerReplayState = "opaque encrypted reasoning payload"
        )

        val debug = message.toString()

        assertFalse(message.text in debug)
        assertFalse(message.activeProfileNotes.single() in debug)
        assertFalse(requireNotNull(message.providerReplayState) in debug)
        assertFalse(requireNotNull(message.modelName) in debug)
        assertTrue("id=message-123" in debug)
        assertTrue("text=<redacted>" in debug)
        assertTrue("providerReplayState=<redacted>" in debug)
    }
}
