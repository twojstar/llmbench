package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Test

class WebChatPreferencesStoreTest {
    @Test
    fun selectedProviderFallsBackSafelyWhenStoredIdIsUnknown() {
        assertEquals(WebAiService.CHATGPT, resolveWebService("CHATGPT"))
        assertEquals(WebAiService.CLAUDE, resolveWebService("retired-provider"))
        assertEquals(WebAiService.CLAUDE, resolveWebService(null))
    }

    @Test
    fun favoriteIdsIgnoreUnknownProvidersAndKeepCanonicalOrder() {
        assertEquals(
            linkedSetOf(WebAiService.CHATGPT, WebAiService.QWEN),
            resolveFavoriteWebServices(setOf("qwen", "CHATGPT", "retired-provider"))
        )
    }
}
