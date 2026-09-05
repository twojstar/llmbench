package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderRuntimeCapabilitiesTest {
    @Test
    fun nativeProvidersKeepNativeSystemInstructionFields() {
        assertEquals(
            SystemInstructionPlacement.NATIVE_FIELD,
            AiProvider.GEMINI.runtimeCapabilities().systemInstructionPlacement
        )
        assertEquals(
            SystemInstructionPlacement.NATIVE_FIELD,
            AiProvider.CHATGPT.runtimeCapabilities().systemInstructionPlacement
        )
        assertEquals(
            SystemInstructionPlacement.NATIVE_FIELD,
            AiProvider.CLAUDE.runtimeCapabilities().systemInstructionPlacement
        )
    }

    @Test
    fun compatibleGatewaysUseSystemMessagesAndExposeResolvedModels() {
        listOf(
            AiProvider.DEEPSEEK,
            AiProvider.KIMI,
            AiProvider.OPENROUTER,
            AiProvider.AIHUBMIX
        ).forEach { provider ->
            val capabilities = provider.runtimeCapabilities()
            assertEquals(
                NativeChatTransport.OPENAI_COMPATIBLE_CHAT_COMPLETIONS,
                capabilities.transport
            )
            assertEquals(SystemInstructionPlacement.SYSTEM_MESSAGE, capabilities.systemInstructionPlacement)
            assertTrue(capabilities.streamsText)
            assertTrue(capabilities.reportsResolvedModel)
        }
    }

    @Test
    fun compareModeIsFanOutRatherThanAProviderTransport() {
        val capabilities = AiProvider.ALL.runtimeCapabilities()

        assertEquals(NativeChatTransport.COMPARE_FAN_OUT, capabilities.transport)
        assertEquals(ConversationStateStrategy.PROVIDER_FAN_OUT, capabilities.conversationStateStrategy)
        assertFalse(capabilities.reportsResolvedModel)
    }
}
