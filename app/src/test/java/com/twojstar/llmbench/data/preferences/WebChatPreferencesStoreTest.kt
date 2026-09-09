package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.ProviderIdentityMethod
import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun preferredIdentityMethodResolvesCaseInsensitivelyAndRejectsUnknownValues() {
        assertEquals(
            ProviderIdentityMethod.GITHUB,
            resolvePreferredIdentityMethod("github")
        )
        assertEquals(
            ProviderIdentityMethod.MICROSOFT,
            resolvePreferredIdentityMethod("MICROSOFT")
        )
        assertNull(resolvePreferredIdentityMethod("password"))
        assertNull(resolvePreferredIdentityMethod(null))
    }
}
