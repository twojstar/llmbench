package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.ProviderUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderUsageMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun normalizesGeminiUsageIncludingCachedAndThoughtTokens() {
        val response = obj("""{"usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":40,"totalTokenCount":190,"cachedContentTokenCount":20,"thoughtsTokenCount":30}}""")
        assertEquals(
            ProviderUsage(120, 40, 190, 20, 30),
            AiChatService().extractGeminiUsage(response)
        )
    }

    @Test
    fun normalizesOpenAiUsageFromBufferedAndCompletedResponses() {
        val response = obj("""{"usage":{"input_tokens":100,"input_tokens_details":{"cached_tokens":25},"output_tokens":50,"output_tokens_details":{"reasoning_tokens":20},"total_tokens":150}}""")
        val expected = ProviderUsage(100, 50, 150, 25, 20)
        val service = AiChatService()
        assertEquals(expected, service.extractOpenAiUsage(response))
        val completed = obj("""{"type":"response.completed","response":${response}}""")
        assertEquals(expected, service.extractOpenAiCompletedUsage(completed))
    }

    @Test
    fun mergesClaudeCumulativeStreamUsageAndCountsCacheInput() {
        val service = AiChatService()
        val start = obj("""{"type":"message_start","message":{"usage":{"input_tokens":10,"cache_creation_input_tokens":20,"cache_read_input_tokens":30}}}""")
        val delta = obj("""{"type":"message_delta","usage":{"output_tokens":40,"output_tokens_details":{"thinking_tokens":15}}}""")
        val merged = service.mergeProviderUsage(
            service.extractClaudeStreamUsage(start),
            service.extractClaudeStreamUsage(delta)
        )
        assertEquals(ProviderUsage(60, 40, 100, 30, 15), merged)
        val buffered = obj("""{"usage":{"input_tokens":10,"cache_creation_input_tokens":20,"cache_read_input_tokens":30,"output_tokens":40,"output_tokens_details":{"thinking_tokens":15}}}""")
        assertEquals(merged, service.extractClaudeUsage(buffered))
    }

    @Test
    fun normalizesCompatibleUsageIncludingDeepSeekCacheAndOpenRouterCost() {
        val service = AiChatService()
        val openRouter = obj("""{"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150,"prompt_tokens_details":{"cached_tokens":20},"completion_tokens_details":{"reasoning_tokens":15},"cost":0.00125}}""")
        assertEquals(ProviderUsage(100, 50, 150, 20, 15, 0.00125), service.extractOpenAiCompatibleUsage(openRouter))
        val deepSeek = obj("""{"usage":{"prompt_tokens":80,"completion_tokens":20,"total_tokens":100,"prompt_cache_hit_tokens":35}}""")
        assertEquals(35L, service.extractOpenAiCompatibleUsage(deepSeek)?.cachedInputTokens)
    }

    @Test
    fun requestsUsageOnlyForOpenRouterCompatiblePayloads() {
        val service = AiChatService()
        val openRouter = service.buildOpenAiCompatibleRequestPayload(
            AiProvider.OPENROUTER, "openrouter/free", buildJsonArray {}, stream = true
        )
        val deepSeek = service.buildOpenAiCompatibleRequestPayload(
            AiProvider.DEEPSEEK, "deepseek-v4-flash", buildJsonArray {}, stream = true
        )
        assertEquals("true", openRouter.getValue("usage").jsonObject.getValue("include").jsonPrimitive.content)
        assertFalse("usage" in deepSeek)
    }

    private fun obj(raw: String) = json.parseToJsonElement(raw).jsonObject
}
