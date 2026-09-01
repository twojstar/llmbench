package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatModelsTest {
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
    fun compareProviderListContainsEveryNativeProviderExactlyOnce() {
        assertFalse(AiProvider.ALL in AiProvider.concreteProviders)
        assertEquals(AiProvider.entries.size - 1, AiProvider.concreteProviders.distinct().size)
        assertEquals(
            AiProvider.entries.filterNot { it == AiProvider.ALL }.toSet(),
            AiProvider.concreteProviders.toSet()
        )
    }
}
