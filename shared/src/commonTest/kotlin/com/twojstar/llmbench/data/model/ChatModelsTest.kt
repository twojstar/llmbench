package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatModelsTest {
    @Test
    fun vibeWebProviderUsesCanonicalMistralChatEndpoint() {
        assertEquals("https://chat.mistral.ai", WebAiService.VIBE.url)
        assertEquals("Vibe", WebAiService.VIBE.shortName)
    }

    @Test
    fun browserPlatformUsesCanonicalAiStudioEndpoint() {
        assertEquals("https://aistudio.google.com", BrowserAiPlatform.GEMINI_AI_STUDIO.url)
        assertEquals("AI Studio", BrowserAiPlatform.GEMINI_AI_STUDIO.shortName)
    }

    @Test
    fun concreteProviderDefaultsAreSelectable() {
        AiProvider.concreteProviders.forEach { provider ->
            assertTrue(
                provider.defaultModel in provider.availableModels,
                "${provider.id} default model must be present in availableModels"
            )
        }
    }

    @Test
    fun defaultCompareUsesDirectProvidersOnly() {
        assertFalse(AiProvider.ALL in AiProvider.concreteProviders)
        assertFalse(AiProvider.OPENROUTER in AiProvider.concreteProviders)
        assertFalse(AiProvider.AIHUBMIX in AiProvider.concreteProviders)
        assertEquals(
            setOf(
                AiProvider.CLAUDE,
                AiProvider.CHATGPT,
                AiProvider.GEMINI,
                AiProvider.DEEPSEEK,
                AiProvider.KIMI
            ),
            AiProvider.concreteProviders.toSet()
        )
    }

    @Test
    fun openRouterDefaultsToFreeModelRouter() {
        assertEquals("openrouter/free", AiProvider.OPENROUTER.defaultModel)
        assertTrue(AiProvider.OPENROUTER.defaultModel in AiProvider.OPENROUTER.availableModels)
    }

    @Test
    fun aiHubMixDefaultsToFreeCatalogModel() {
        assertEquals("hy3-free", AiProvider.AIHUBMIX.defaultModel)
        assertTrue(AiProvider.AIHUBMIX.availableModels.all { it.endsWith("-free") })
    }

    @Test
    fun configuredDirectProvidersIncludeOnlyKeyedDirectProviders() {
        val config = ApiKeyConfig(
            geminiKey = "   ",
            openAiKey = "sk-live",
            kimiKey = "  kimi-key  ",
            openRouterKey = "sk-or-v1-gateway"
        )

        assertEquals(
            listOf(AiProvider.CHATGPT, AiProvider.KIMI),
            config.configuredDirectProviders()
        )
    }

    @Test
    fun liveGatewayCatalogKeepsOnlyFreeTextModels() {
        val catalog = listOf(
            GatewayModelCatalogEntry("free-a", 0.0, 0.0, supportsTextOutput = true),
            GatewayModelCatalogEntry("paid", 0.0, 0.1, supportsTextOutput = true),
            GatewayModelCatalogEntry("image-free", 0.0, 0.0, supportsTextOutput = false),
            GatewayModelCatalogEntry("free-a", 0.0, 0.0, supportsTextOutput = true),
            GatewayModelCatalogEntry("free-b", 0.0, 0.0, supportsTextOutput = true)
        )

        assertEquals(
            listOf("openrouter/free", "free-a", "free-b"),
            freeGatewayModelOptions(AiProvider.OPENROUTER, catalog)
        )
        assertEquals(
            listOf("free-a", "free-b"),
            freeGatewayModelOptions(AiProvider.AIHUBMIX, catalog)
        )
    }

    @Test
    fun successfulEmptyLiveGatewayCatalogStaysEmpty() {
        assertEquals(
            emptyList<String>(),
            freeGatewayModelOptions(AiProvider.AIHUBMIX, emptyList())
        )
    }

    @Test
    fun gatewayKeysDoNotMakeDefaultCompareConfigured() {
        val config = ApiKeyConfig(
            openRouterKey = "sk-or-v1-gateway",
            aiHubMixKey = "gateway-key"
        )

        assertTrue(config.hasKeyFor(AiProvider.OPENROUTER))
        assertTrue(config.hasKeyFor(AiProvider.AIHUBMIX))
        assertFalse(config.hasKeyFor(AiProvider.ALL))
        assertTrue(config.configuredDirectProviders().isEmpty())
    }

}
