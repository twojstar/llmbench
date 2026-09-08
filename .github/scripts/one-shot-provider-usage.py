from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# Shared portable response metadata.
Path("shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ProviderUsage.kt").write_text(
    '''package com.twojstar.llmbench.data.model

import kotlinx.serialization.Serializable

/** Provider-reported usage for one native/API response. Null fields mean the provider omitted them. */
@Serializable
data class ProviderUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val costUsd: Double? = null
)
''',
    encoding="utf-8"
)

replace_once(
    "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ChatModels.kt",
    '''    val isPartial: Boolean = false,\n    val latencyMs: Long? = null,\n    val activeProfileNotes: List<String> = emptyList(),''',
    '''    val isPartial: Boolean = false,\n    val latencyMs: Long? = null,\n    val usage: ProviderUsage? = null,\n    val activeProfileNotes: List<String> = emptyList(),'''
)

engine = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace_once(
    engine,
    '''import com.twojstar.llmbench.data.model.ModelChatMessage\nimport com.twojstar.llmbench.data.model.buildBoundedProviderTextTurns''',
    '''import com.twojstar.llmbench.data.model.ModelChatMessage\nimport com.twojstar.llmbench.data.model.ProviderUsage\nimport com.twojstar.llmbench.data.model.buildBoundedProviderTextTurns'''
)
replace_once(
    engine,
    '''private const val JSON_INPUT_KEY = "input"\nprivate const val JSON_OUTPUT_KEY = "output"\nprivate const val JSON_STATUS_KEY = "status"''',
    '''private const val JSON_INPUT_KEY = "input"\nprivate const val JSON_OUTPUT_KEY = "output"\nprivate const val JSON_USAGE_KEY = "usage"\nprivate const val JSON_TOTAL_TOKENS_KEY = "total_tokens"\nprivate const val JSON_PROMPT_TOKENS_KEY = "prompt_tokens"\nprivate const val JSON_COMPLETION_TOKENS_KEY = "completion_tokens"\nprivate const val JSON_INPUT_TOKENS_KEY = "input_tokens"\nprivate const val JSON_OUTPUT_TOKENS_KEY = "output_tokens"\nprivate const val JSON_INPUT_TOKEN_DETAILS_KEY = "input_tokens_details"\nprivate const val JSON_OUTPUT_TOKEN_DETAILS_KEY = "output_tokens_details"\nprivate const val JSON_PROMPT_TOKEN_DETAILS_KEY = "prompt_tokens_details"\nprivate const val JSON_COMPLETION_TOKEN_DETAILS_KEY = "completion_tokens_details"\nprivate const val JSON_CACHED_TOKENS_KEY = "cached_tokens"\nprivate const val JSON_REASONING_TOKENS_KEY = "reasoning_tokens"\nprivate const val JSON_COST_KEY = "cost"\nprivate const val GEMINI_USAGE_METADATA_KEY = "usageMetadata"\nprivate const val GEMINI_PROMPT_TOKENS_KEY = "promptTokenCount"\nprivate const val GEMINI_CANDIDATE_TOKENS_KEY = "candidatesTokenCount"\nprivate const val GEMINI_TOTAL_TOKENS_KEY = "totalTokenCount"\nprivate const val GEMINI_CACHED_TOKENS_KEY = "cachedContentTokenCount"\nprivate const val GEMINI_THOUGHT_TOKENS_KEY = "thoughtsTokenCount"\nprivate const val CLAUDE_CACHE_CREATION_TOKENS_KEY = "cache_creation_input_tokens"\nprivate const val CLAUDE_CACHE_READ_TOKENS_KEY = "cache_read_input_tokens"\nprivate const val CLAUDE_THINKING_TOKENS_KEY = "thinking_tokens"\nprivate const val DEEPSEEK_CACHE_HIT_TOKENS_KEY = "prompt_cache_hit_tokens"\nprivate const val JSON_STATUS_KEY = "status"'''
)
replace_once(
    engine,
    '''    private data class GeminiGenerationResult(\n        val text: String,\n        val replayState: String?\n    )\n\n    private data class OpenAiGenerationResult(\n        val text: String,\n        val replayState: String?\n    )\n\n    private data class ClaudeGenerationResult(\n        val text: String,\n        val replayState: String?,\n        val resolvedModel: String,\n        val isPartial: Boolean\n    )''',
    '''    private data class GeminiGenerationResult(\n        val text: String,\n        val replayState: String?,\n        val usage: ProviderUsage?\n    )\n\n    private data class OpenAiGenerationResult(\n        val text: String,\n        val replayState: String?,\n        val usage: ProviderUsage?\n    )\n\n    private data class ClaudeGenerationResult(\n        val text: String,\n        val replayState: String?,\n        val resolvedModel: String,\n        val isPartial: Boolean,\n        val usage: ProviderUsage?\n    )\n\n    private data class OpenAiCompatibleGenerationResult(\n        val text: String,\n        val usage: ProviderUsage?\n    )'''
)
replace_once(
    engine,
    '''    private fun JsonElement?.asDoubleOrNull(): Double? = when (this) {\n        is JsonPrimitive -> contentOrNull?.toDoubleOrNull()\n        else -> null\n    }\n\n    private fun bearerToken(apiKey: String): String = "Bearer $apiKey"''',
    '''    private fun JsonElement?.asDoubleOrNull(): Double? = when (this) {\n        is JsonPrimitive -> contentOrNull?.toDoubleOrNull()\n        else -> null\n    }\n\n    private fun JsonElement?.asUsageLongOrNull(): Long? =\n        (this as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0L }\n\n    private fun sumUsageTokens(vararg values: Long?): Long? {\n        val present = values.filterNotNull()\n        return present.takeIf { it.isNotEmpty() }?.sum()\n    }\n\n    private fun providerUsage(\n        inputTokens: Long? = null,\n        outputTokens: Long? = null,\n        totalTokens: Long? = null,\n        cachedInputTokens: Long? = null,\n        reasoningTokens: Long? = null,\n        costUsd: Double? = null\n    ): ProviderUsage? {\n        val normalizedCost = costUsd?.takeIf { it >= 0.0 && it.isFinite() }\n        return ProviderUsage(\n            inputTokens = inputTokens,\n            outputTokens = outputTokens,\n            totalTokens = totalTokens ?: if (inputTokens != null && outputTokens != null) {\n                inputTokens + outputTokens\n            } else null,\n            cachedInputTokens = cachedInputTokens,\n            reasoningTokens = reasoningTokens,\n            costUsd = normalizedCost\n        ).takeIf { usage ->\n            usage.inputTokens != null || usage.outputTokens != null || usage.totalTokens != null ||\n                usage.cachedInputTokens != null || usage.reasoningTokens != null || usage.costUsd != null\n        }\n    }\n\n    internal fun mergeProviderUsage(current: ProviderUsage?, update: ProviderUsage?): ProviderUsage? {\n        if (update == null) return current\n        if (current == null) return update\n        val input = update.inputTokens ?: current.inputTokens\n        val output = update.outputTokens ?: current.outputTokens\n        val tokensChanged = update.inputTokens != null || update.outputTokens != null\n        val total = update.totalTokens ?: if (tokensChanged && input != null && output != null) {\n            input + output\n        } else {\n            current.totalTokens\n        }\n        return ProviderUsage(\n            inputTokens = input,\n            outputTokens = output,\n            totalTokens = total,\n            cachedInputTokens = update.cachedInputTokens ?: current.cachedInputTokens,\n            reasoningTokens = update.reasoningTokens ?: current.reasoningTokens,\n            costUsd = update.costUsd ?: current.costUsd\n        )\n    }\n\n    private fun bearerToken(apiKey: String): String = "Bearer $apiKey"'''
)
replace_once(
    engine,
    '''        var resolvedModel = effectiveModel\n        var providerReplayState: String? = null\n        var isPartial = false''',
    '''        var resolvedModel = effectiveModel\n        var providerReplayState: String? = null\n        var providerUsage: ProviderUsage? = null\n        var isPartial = false'''
)
replace_once(
    engine,
    '''                        providerReplayState = result.replayState\n                        result.text\n                    }\n                    AiProvider.CHATGPT -> {''',
    '''                        providerReplayState = result.replayState\n                        providerUsage = result.usage\n                        result.text\n                    }\n                    AiProvider.CHATGPT -> {'''
)
replace_once(
    engine,
    '''                        providerReplayState = result.replayState\n                        result.text\n                    }\n                    AiProvider.CLAUDE -> {''',
    '''                        providerReplayState = result.replayState\n                        providerUsage = result.usage\n                        result.text\n                    }\n                    AiProvider.CLAUDE -> {'''
)
replace_once(
    engine,
    '''                        providerReplayState = result.replayState\n                        resolvedModel = result.resolvedModel\n                        isPartial = result.isPartial\n                        result.text''',
    '''                        providerReplayState = result.replayState\n                        resolvedModel = result.resolvedModel\n                        isPartial = result.isPartial\n                        providerUsage = result.usage\n                        result.text'''
)
replace_once(
    engine,
    '''                        if (onTextDelta != null) {\n                            callOpenAiCompatibleStreamApi(\n                                config, prompt, effectiveModel, key, systemInstruction,\n                                conversationHistory, provider, onTextDelta,\n                                onResolvedModel = { resolvedModel = it }\n                            )\n                        } else {\n                            callOpenAiCompatibleApi(\n                                config, prompt, effectiveModel, key, systemInstruction,\n                                conversationHistory, provider,\n                                onResolvedModel = { resolvedModel = it }\n                            )\n                        }''',
    '''                        val result = if (onTextDelta != null) {\n                            callOpenAiCompatibleStreamApi(\n                                config, prompt, effectiveModel, key, systemInstruction,\n                                conversationHistory, provider, onTextDelta,\n                                onResolvedModel = { resolvedModel = it }\n                            )\n                        } else {\n                            callOpenAiCompatibleApi(\n                                config, prompt, effectiveModel, key, systemInstruction,\n                                conversationHistory, provider,\n                                onResolvedModel = { resolvedModel = it }\n                            )\n                        }\n                        providerUsage = result.usage\n                        result.text'''
)
replace_once(
    engine,
    '''                        isPartial = isPartial,\n                        latencyMs = latency,\n                        activeProfileNotes = activeNotes,''',
    '''                        isPartial = isPartial,\n                        latencyMs = latency,\n                        usage = providerUsage,\n                        activeProfileNotes = activeNotes,'''
)

# Usage parsers live beside provider request builders so buffered and streaming paths share them.
marker = '''    // --- Google Gemini REST API ---\n    private suspend fun callGeminiApi('''
usage_parsers = '''    internal fun extractGeminiUsage(response: JsonObject): ProviderUsage? {\n        val usage = response[GEMINI_USAGE_METADATA_KEY] as? JsonObject ?: return null\n        return providerUsage(\n            inputTokens = usage[GEMINI_PROMPT_TOKENS_KEY].asUsageLongOrNull(),\n            outputTokens = usage[GEMINI_CANDIDATE_TOKENS_KEY].asUsageLongOrNull(),\n            totalTokens = usage[GEMINI_TOTAL_TOKENS_KEY].asUsageLongOrNull(),\n            cachedInputTokens = usage[GEMINI_CACHED_TOKENS_KEY].asUsageLongOrNull(),\n            reasoningTokens = usage[GEMINI_THOUGHT_TOKENS_KEY].asUsageLongOrNull()\n        )\n    }\n\n    internal fun extractOpenAiUsage(response: JsonObject): ProviderUsage? {\n        val usage = response[JSON_USAGE_KEY] as? JsonObject ?: return null\n        val inputDetails = usage[JSON_INPUT_TOKEN_DETAILS_KEY] as? JsonObject\n        val outputDetails = usage[JSON_OUTPUT_TOKEN_DETAILS_KEY] as? JsonObject\n        return providerUsage(\n            inputTokens = usage[JSON_INPUT_TOKENS_KEY].asUsageLongOrNull(),\n            outputTokens = usage[JSON_OUTPUT_TOKENS_KEY].asUsageLongOrNull(),\n            totalTokens = usage[JSON_TOTAL_TOKENS_KEY].asUsageLongOrNull(),\n            cachedInputTokens = inputDetails?.get(JSON_CACHED_TOKENS_KEY).asUsageLongOrNull(),\n            reasoningTokens = outputDetails?.get(JSON_REASONING_TOKENS_KEY).asUsageLongOrNull()\n        )\n    }\n\n    internal fun extractOpenAiCompletedUsage(event: JsonObject): ProviderUsage? =\n        if (event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull == OPENAI_RESPONSE_COMPLETED) {\n            (event[STREAM_RESPONSE_KEY] as? JsonObject)?.let(::extractOpenAiUsage)\n        } else null\n\n    private fun parseClaudeUsageObject(usage: JsonObject?): ProviderUsage? {\n        usage ?: return null\n        val directInput = usage[JSON_INPUT_TOKENS_KEY].asUsageLongOrNull()\n        val cacheCreation = usage[CLAUDE_CACHE_CREATION_TOKENS_KEY].asUsageLongOrNull()\n        val cacheRead = usage[CLAUDE_CACHE_READ_TOKENS_KEY].asUsageLongOrNull()\n        val output = usage[JSON_OUTPUT_TOKENS_KEY].asUsageLongOrNull()\n        val outputDetails = usage[JSON_OUTPUT_TOKEN_DETAILS_KEY] as? JsonObject\n        return providerUsage(\n            inputTokens = sumUsageTokens(directInput, cacheCreation, cacheRead),\n            outputTokens = output,\n            cachedInputTokens = cacheRead,\n            reasoningTokens = outputDetails?.get(CLAUDE_THINKING_TOKENS_KEY).asUsageLongOrNull()\n        )\n    }\n\n    internal fun extractClaudeUsage(response: JsonObject): ProviderUsage? =\n        parseClaudeUsageObject(response[JSON_USAGE_KEY] as? JsonObject)\n\n    internal fun extractClaudeStreamUsage(event: JsonObject): ProviderUsage? =\n        when (event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull) {\n            CLAUDE_MESSAGE_START -> (event[STREAM_MESSAGE_KEY] as? JsonObject)\n                ?.get(JSON_USAGE_KEY)\n                ?.let { it as? JsonObject }\n                ?.let(::parseClaudeUsageObject)\n            CLAUDE_MESSAGE_DELTA -> parseClaudeUsageObject(event[JSON_USAGE_KEY] as? JsonObject)\n            else -> null\n        }\n\n    internal fun extractOpenAiCompatibleUsage(response: JsonObject): ProviderUsage? {\n        val usage = response[JSON_USAGE_KEY] as? JsonObject ?: return null\n        val promptDetails = usage[JSON_PROMPT_TOKEN_DETAILS_KEY] as? JsonObject\n        val completionDetails = usage[JSON_COMPLETION_TOKEN_DETAILS_KEY] as? JsonObject\n        return providerUsage(\n            inputTokens = usage[JSON_PROMPT_TOKENS_KEY].asUsageLongOrNull(),\n            outputTokens = usage[JSON_COMPLETION_TOKENS_KEY].asUsageLongOrNull(),\n            totalTokens = usage[JSON_TOTAL_TOKENS_KEY].asUsageLongOrNull(),\n            cachedInputTokens = promptDetails?.get(JSON_CACHED_TOKENS_KEY).asUsageLongOrNull()\n                ?: usage[DEEPSEEK_CACHE_HIT_TOKENS_KEY].asUsageLongOrNull(),\n            reasoningTokens = completionDetails?.get(JSON_REASONING_TOKENS_KEY).asUsageLongOrNull(),\n            costUsd = usage[JSON_COST_KEY].asDoubleOrNull()\n        )\n    }\n\n    internal fun buildOpenAiCompatibleRequestPayload(\n        provider: AiProvider,\n        model: String,\n        messages: JsonArray,\n        stream: Boolean\n    ): JsonObject = buildJsonObject {\n        put(JSON_MODEL_KEY, model)\n        put(JSON_MESSAGES_KEY, messages)\n        if (stream) put(JSON_STREAM_KEY, true)\n        if (provider == AiProvider.OPENROUTER) {\n            putJsonObject(JSON_USAGE_KEY) {\n                put(JSON_INCLUDE_KEY, true)\n            }\n        }\n    }\n\n    // --- Google Gemini REST API ---\n    private suspend fun callGeminiApi('''
replace_once(engine, marker, usage_parsers)

replace_once(
    engine,
    '''            replayState = content?.let { encodeGeminiReplayState(listOf(it)) }\n        )''',
    '''            replayState = content?.let { encodeGeminiReplayState(listOf(it)) },\n            usage = extractGeminiUsage(parsed)\n        )'''
)
replace_once(
    engine,
    '''            replayState = extractOpenAiReplayState(parsed)\n        )''',
    '''            replayState = extractOpenAiReplayState(parsed),\n            usage = extractOpenAiUsage(parsed)\n        )'''
)
replace_once(
    engine,
    '''            replayState = if (isPartial) null else extractClaudeReplayState(parsed),\n            resolvedModel = resolvedModel,\n            isPartial = isPartial\n        )''',
    '''            replayState = if (isPartial) null else extractClaudeReplayState(parsed),\n            resolvedModel = resolvedModel,\n            isPartial = isPartial,\n            usage = extractClaudeUsage(parsed)\n        )'''
)
replace_once(
    engine,
    '''        val replayContents = mutableListOf<JsonObject>()\n        val text = executeSse(''',
    '''        val replayContents = mutableListOf<JsonObject>()\n        var usage: ProviderUsage? = null\n        val text = executeSse('''
)
replace_once(
    engine,
    '''            onEvent = { event ->\n                extractGeminiReplayContent(event)?.let(replayContents::add)\n            }\n        )\n        return GeminiGenerationResult(\n            text = text.ifEmpty { "Received empty content response from Gemini." },\n            replayState = encodeGeminiReplayState(replayContents)\n        )''',
    '''            onEvent = { event ->\n                extractGeminiReplayContent(event)?.let(replayContents::add)\n                usage = mergeProviderUsage(usage, extractGeminiUsage(event))\n            }\n        )\n        return GeminiGenerationResult(\n            text = text.ifEmpty { "Received empty content response from Gemini." },\n            replayState = encodeGeminiReplayState(replayContents),\n            usage = usage\n        )'''
)
replace_once(
    engine,
    '''        var replayState: String? = null\n        val text = executeSse(''',
    '''        var replayState: String? = null\n        var usage: ProviderUsage? = null\n        val text = executeSse('''
)
replace_once(
    engine,
    '''            onEvent = { event ->\n                extractOpenAiCompletedReplayState(event)?.let { replayState = it }\n            }\n        )\n        return OpenAiGenerationResult(\n            text = text.ifEmpty { "Received empty message content from OpenAI." },\n            replayState = replayState\n        )''',
    '''            onEvent = { event ->\n                extractOpenAiCompletedReplayState(event)?.let { replayState = it }\n                usage = mergeProviderUsage(usage, extractOpenAiCompletedUsage(event))\n            }\n        )\n        return OpenAiGenerationResult(\n            text = text.ifEmpty { "Received empty message content from OpenAI." },\n            replayState = replayState,\n            usage = usage\n        )'''
)
replace_once(
    engine,
    '''        val replayBlocks = mutableMapOf<Int, JsonObject>()\n        var resolvedModel = metadata.resolvedModel\n        var stopReason: String? = null\n        val text = executeSse(''',
    '''        val replayBlocks = mutableMapOf<Int, JsonObject>()\n        var resolvedModel = metadata.resolvedModel\n        var stopReason: String? = null\n        var usage: ProviderUsage? = null\n        val text = executeSse('''
)
replace_once(
    engine,
    '''                extractClaudeStreamStopReason(event)?.let { stopReason = it }\n                applyClaudeReplayEvent(replayBlocks, event)\n            }''',
    '''                extractClaudeStreamStopReason(event)?.let { stopReason = it }\n                usage = mergeProviderUsage(usage, extractClaudeStreamUsage(event))\n                applyClaudeReplayEvent(replayBlocks, event)\n            }'''
)
replace_once(
    engine,
    '''            replayState = if (isPartial) null else encodeClaudeStreamReplayState(replayBlocks),\n            resolvedModel = resolvedModel,\n            isPartial = isPartial\n        )''',
    '''            replayState = if (isPartial) null else encodeClaudeStreamReplayState(replayBlocks),\n            resolvedModel = resolvedModel,\n            isPartial = isPartial,\n            usage = usage\n        )'''
)

# Replace compatible request construction and make both paths usage-aware.
replace_once(
    engine,
    '''    ): String {\n        val requestPayload = buildJsonObject {\n            put(JSON_MODEL_KEY, model)\n            put(JSON_MESSAGES_KEY, buildOpenAiCompatibleMessages(\n                prompt, systemInstruction, conversationHistory, provider\n            ))\n            put(JSON_STREAM_KEY, true)\n        }''',
    '''    ): OpenAiCompatibleGenerationResult {\n        val requestPayload = buildOpenAiCompatibleRequestPayload(\n            provider = provider,\n            model = model,\n            messages = buildOpenAiCompatibleMessages(\n                prompt, systemInstruction, conversationHistory, provider\n            ),\n            stream = true\n        )'''
)
replace_once(
    engine,
    '''        return executeSse(\n            request,\n            extractText = { event ->\n                extractOpenAiCompatibleModel(event)?.let(onResolvedModel)\n                extractOpenAiCompatibleStreamText(event)\n            },\n            isComplete = ::isOpenAiCompatibleStreamComplete,\n            onTextDelta = onTextDelta,\n            completeOnDoneSentinel = true\n        ).ifEmpty { "Received empty message content." }''',
    '''        var usage: ProviderUsage? = null\n        val waitForDoneSentinel = provider == AiProvider.OPENROUTER\n        val text = executeSse(\n            request,\n            extractText = ::extractOpenAiCompatibleStreamText,\n            isComplete = if (waitForDoneSentinel) { { false } } else ::isOpenAiCompatibleStreamComplete,\n            onTextDelta = onTextDelta,\n            completeOnDoneSentinel = true,\n            onEvent = { event ->\n                extractOpenAiCompatibleModel(event)?.let(onResolvedModel)\n                usage = mergeProviderUsage(usage, extractOpenAiCompatibleUsage(event))\n            }\n        ).ifEmpty { "Received empty message content." }\n        return OpenAiCompatibleGenerationResult(text = text, usage = usage)'''
)
replace_once(
    engine,
    '''    ): String {\n        val messagesArray = buildOpenAiCompatibleMessages(''',
    '''    ): OpenAiCompatibleGenerationResult {\n        val messagesArray = buildOpenAiCompatibleMessages('''
)
replace_once(
    engine,
    '''        val requestPayload = buildJsonObject {\n            put(JSON_MODEL_KEY, model)\n            put(JSON_MESSAGES_KEY, messagesArray)\n        }''',
    '''        val requestPayload = buildOpenAiCompatibleRequestPayload(\n            provider = provider,\n            model = model,\n            messages = messagesArray,\n            stream = false\n        )'''
)
replace_once(
    engine,
    '''        return content ?: "Received empty message content."''',
    '''        return OpenAiCompatibleGenerationResult(\n            text = content ?: "Received empty message content.",\n            usage = extractOpenAiCompatibleUsage(parsed)\n        )'''
)

# Chat diagnostics formatting and rendering.
Path("app/src/main/java/com/twojstar/llmbench/ui/screens/ChatUsageMetadata.kt").write_text(
    '''package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.ModelChatMessage
import java.util.Locale

internal fun formatChatResponseDiagnostics(message: ModelChatMessage): String? = buildList {
    message.latencyMs?.let { add("${it}ms") }
    message.usage?.let { usage ->
        usage.inputTokens?.let { add("${formatTokenCount(it)} in") }
        usage.outputTokens?.let { add("${formatTokenCount(it)} out") }
        usage.totalTokens?.let { add("${formatTokenCount(it)} total") }
        usage.cachedInputTokens?.let { add("${formatTokenCount(it)} cached") }
        usage.reasoningTokens?.let { add("${formatTokenCount(it)} reasoning") }
        usage.costUsd?.let { add(formatReportedCost(it)) }
    }
}.joinToString(" • ").takeIf { it.isNotBlank() }

private fun formatTokenCount(tokens: Long): String = String.format(Locale.US, "%,d", tokens)

private fun formatReportedCost(costUsd: Double): String {
    val fixed = String.format(Locale.US, "%.6f", costUsd).trimEnd('0').trimEnd('.')
    return "$$fixed"
}
''',
    encoding="utf-8"
)

chat = "app/src/main/java/com/twojstar/llmbench/ui/screens/ChatScreen.kt"
replace_once(
    chat,
    '''                if (message.latencyMs != null) {\n                    Text(\n                        text = "(${message.latencyMs}ms)",\n                        style = MaterialTheme.typography.labelSmall,\n                        fontSize = 10.sp,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)\n                    )\n                }\n''',
    ''''''
)
replace_once(
    chat,
    '''                // Active style profile or system prompt notes badge\n                if (!isUser && message.activeProfileNotes.isNotEmpty()) {''',
    '''                if (!isUser) {\n                    formatChatResponseDiagnostics(message)?.let { diagnostics ->\n                        Spacer(Modifier.height(6.dp))\n                        Text(\n                            text = diagnostics,\n                            style = MaterialTheme.typography.labelSmall,\n                            fontSize = 10.sp,\n                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),\n                            lineHeight = 14.sp\n                        )\n                    }\n                }\n\n                // Active style profile or system prompt notes badge\n                if (!isUser && message.activeProfileNotes.isNotEmpty()) {'''
)

# Focused JVM tests for provider normalization and UI formatting.
Path("app/src/test/java/com/twojstar/llmbench/data/engine/ProviderUsageMetadataTest.kt").write_text(
    '''package com.twojstar.llmbench.data.engine

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
''',
    encoding="utf-8"
)

Path("app/src/test/java/com/twojstar/llmbench/ui/screens/ChatUsageMetadataTest.kt").write_text(
    '''package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.ModelChatMessage
import com.twojstar.llmbench.data.model.ProviderUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUsageMetadataTest {
    @Test
    fun formatsLatencyProviderTokensAndReportedCost() {
        val message = ModelChatMessage(
            id = "m1",
            sender = "assistant",
            text = "answer",
            latencyMs = 842,
            usage = ProviderUsage(
                inputTokens = 1_234,
                outputTokens = 456,
                totalTokens = 1_690,
                cachedInputTokens = 120,
                reasoningTokens = 80,
                costUsd = 0.00125
            )
        )
        assertEquals(
            "842ms • 1,234 in • 456 out • 1,690 total • 120 cached • 80 reasoning • $0.00125",
            formatChatResponseDiagnostics(message)
        )
    }

    @Test
    fun keepsLatencyOnlyResponsesCompact() {
        val message = ModelChatMessage(id = "m2", sender = "assistant", text = "answer", latencyMs = 120)
        assertEquals("120ms", formatChatResponseDiagnostics(message))
    }
}
''',
    encoding="utf-8"
)

# Document the normalization boundary and close the tracked TODO.
docs = "docs/provider-runtime.md"
replace_once(
    docs,
    '''## Web/account-chat TODO\n''',
    '''## Usage and comparison metadata\n\nNative/API responses retain provider-reported usage next to the existing local wall-clock latency. The portable message metadata normalizes input, output and total tokens while preserving optional cached-input and reasoning-token counts. Claude input includes direct, cache-creation and cache-read tokens so its normalized input matches Anthropic's billing/accounting semantics; Gemini keeps `thoughtsTokenCount` separate while preserving the provider's `totalTokenCount`.\n\nCosts are recorded only when the response reports them. LlmBench does not estimate provider prices in this path. OpenRouter requests usage accounting explicitly and may therefore supply a reported USD cost; other OpenAI-compatible gateways are parsed opportunistically when they return compatible usage fields. Hidden reasoning content remains opaque and is never exposed by these counters.\n\n## Web/account-chat TODO\n'''
)
replace_once(
    docs,
    '''- [ ] Parse provider usage/token metadata so comparisons can include latency and token/cost information when the API returns it.''',
    '''- [x] Parse provider usage/token metadata so comparisons can include latency and provider-reported token/cost information when the API returns it.'''
)

print("provider usage metadata patch applied")
