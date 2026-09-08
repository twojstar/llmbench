from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected one anchor, found {count}"
    return text.replace(old, new, 1)


app_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
app = app_path.read_text()

app = replace_once(
    app,
    'private const val JSON_CONTENT_BLOCK_KEY = "content_block"\n',
    'private const val JSON_CONTENT_BLOCK_KEY = "content_block"\n'
    'private const val JSON_DATA_KEY = "data"\n'
    'private const val JSON_TO_KEY = "to"\n',
    "Claude replay JSON keys",
)
app = replace_once(
    app,
    'private const val CLAUDE_MESSAGE_STOP = "message_stop"\n',
    'private const val CLAUDE_MESSAGE_START = "message_start"\n'
    'private const val CLAUDE_MESSAGE_STOP = "message_stop"\n',
    "Claude message start constant",
)
app = replace_once(
    app,
    'private const val CLAUDE_REDACTED_THINKING_BLOCK = "redacted_thinking"\n',
    'private const val CLAUDE_REDACTED_THINKING_BLOCK = "redacted_thinking"\n'
    'private const val CLAUDE_FALLBACK_BLOCK = "fallback"\n'
    'private const val CLAUDE_UNREPLAYABLE_BLOCK = "__unreplayable__"\n',
    "Claude replay block constants",
)

app = replace_once(
    app,
    '''    private data class ClaudeGenerationResult(\n        val text: String,\n        val replayState: String?\n    )\n''',
    '''    private data class ClaudeGenerationResult(\n        val text: String,\n        val replayState: String?,\n        val resolvedModel: String\n    )\n\n    private data class ClaudeRuntimeMetadata(\n        val maxTokens: Int,\n        val reasoningCapabilities: ClaudeReasoningCapabilities,\n        val resolvedModel: String\n    )\n''',
    "Claude generation metadata",
)

app = replace_once(
    app,
    '''                        providerReplayState = result.replayState\n                        result.text\n                    }\n                    AiProvider.DEEPSEEK, AiProvider.KIMI, AiProvider.OPENROUTER, AiProvider.AIHUBMIX -> {\n''',
    '''                        providerReplayState = result.replayState\n                        resolvedModel = result.resolvedModel\n                        result.text\n                    }\n                    AiProvider.DEEPSEEK, AiProvider.KIMI, AiProvider.OPENROUTER, AiProvider.AIHUBMIX -> {\n''',
    "store Claude resolved model",
)

cache_old = '''    internal fun rememberClaudeMaxTokens(model: String, apiKey: String, reported: Int?): Int {\n        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)\n        val resolved = reported?.takeIf { it > 0 } ?: CLAUDE_MAX_TOKENS_COMPAT_FALLBACK\n        return claudeMaxTokensByModelAndCredential.putIfAbsent(cacheKey, resolved) ?: resolved\n    }\n\n    private suspend fun resolveClaudeMaxTokens(model: String, apiKey: String): Int {\n        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)\n        claudeMaxTokensByModelAndCredential[cacheKey]?.let { cached ->\n            claudeReasoningByModelAndCredential.putIfAbsent(\n                cacheKey,\n                fallbackClaudeReasoningCapabilities(model)\n            )\n            return cached\n        }\n        return try {\n            val request = buildClaudeModelMetadataRequest(model, apiKey)\n            val responseBody = executeCancellableJson(\n                request,\n                httpErrorContext = "Anthropic model metadata",\n                client = claudeMetadataHttpClient\n            )\n            claudeReasoningByModelAndCredential.putIfAbsent(\n                cacheKey,\n                parseClaudeReasoningCapabilities(responseBody)\n                    ?: fallbackClaudeReasoningCapabilities(model)\n            )\n            rememberClaudeMaxTokens(model, apiKey, parseClaudeModelMaxTokens(responseBody))\n        } catch (_: IOException) {\n            claudeReasoningByModelAndCredential.putIfAbsent(\n                cacheKey,\n                fallbackClaudeReasoningCapabilities(model)\n            )\n            rememberClaudeMaxTokens(model, apiKey, null)\n        }\n    }\n\n    private fun resolveClaudeReasoningCapabilities(model: String, apiKey: String): ClaudeReasoningCapabilities =\n        claudeReasoningByModelAndCredential[claudeMaxTokensCacheKey(model, apiKey)]\n            ?: fallbackClaudeReasoningCapabilities(model)\n'''
cache_new = '''    internal fun rememberClaudeMaxTokens(model: String, apiKey: String, reported: Int?): Int {\n        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)\n        claudeMaxTokensByModelAndCredential[cacheKey]?.let { return it }\n        val resolved = reported?.takeIf { it > 0 } ?: return CLAUDE_MAX_TOKENS_COMPAT_FALLBACK\n        return claudeMaxTokensByModelAndCredential.putIfAbsent(cacheKey, resolved) ?: resolved\n    }\n\n    internal fun parseClaudeModelId(rawJson: String): String? = runCatching {\n        json.parseToJsonElement(rawJson).jsonObject[JSON_MODEL_KEY]\n            ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }\n    }.getOrNull()\n\n    internal fun rememberClaudeMetadata(\n        resolvedModel: String,\n        apiKey: String,\n        reportedMaxTokens: Int?,\n        reportedReasoning: ClaudeReasoningCapabilities?\n    ) {\n        val cacheKey = claudeMaxTokensCacheKey(resolvedModel, apiKey)\n        reportedMaxTokens?.takeIf { it > 0 }?.let {\n            claudeMaxTokensByModelAndCredential.putIfAbsent(cacheKey, it)\n        }\n        reportedReasoning?.let {\n            claudeReasoningByModelAndCredential.putIfAbsent(cacheKey, it)\n        }\n    }\n\n    private fun invalidateClaudeMetadata(model: String, apiKey: String) {\n        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)\n        claudeMaxTokensByModelAndCredential.remove(cacheKey)\n        claudeReasoningByModelAndCredential.remove(cacheKey)\n    }\n\n    private suspend fun resolveClaudeMetadata(model: String, apiKey: String): ClaudeRuntimeMetadata {\n        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)\n        val cachedMaxTokens = claudeMaxTokensByModelAndCredential[cacheKey]\n        val cachedReasoning = claudeReasoningByModelAndCredential[cacheKey]\n        if (cachedMaxTokens != null && cachedReasoning != null) {\n            return ClaudeRuntimeMetadata(cachedMaxTokens, cachedReasoning, model)\n        }\n\n        return try {\n            val request = buildClaudeModelMetadataRequest(model, apiKey)\n            val responseBody = executeCancellableJson(\n                request,\n                httpErrorContext = "Anthropic model metadata",\n                client = claudeMetadataHttpClient\n            )\n            val resolvedModel = parseClaudeModelId(responseBody) ?: model\n            val reportedMaxTokens = parseClaudeModelMaxTokens(responseBody)\n            val reportedReasoning = parseClaudeReasoningCapabilities(responseBody)\n            rememberClaudeMetadata(resolvedModel, apiKey, reportedMaxTokens, reportedReasoning)\n            ClaudeRuntimeMetadata(\n                maxTokens = reportedMaxTokens ?: CLAUDE_MAX_TOKENS_COMPAT_FALLBACK,\n                reasoningCapabilities = reportedReasoning\n                    ?: fallbackClaudeReasoningCapabilities(resolvedModel),\n                resolvedModel = resolvedModel\n            )\n        } catch (_: IOException) {\n            ClaudeRuntimeMetadata(\n                maxTokens = CLAUDE_MAX_TOKENS_COMPAT_FALLBACK,\n                reasoningCapabilities = fallbackClaudeReasoningCapabilities(model),\n                resolvedModel = model\n            )\n        }\n    }\n'''
app = replace_once(app, cache_old, cache_new, "Claude metadata cache policy")

app = replace_once(
    app,
    '''        val maxTokens = resolveClaudeMaxTokens(model, apiKey)\n        val reasoningCapabilities = resolveClaudeReasoningCapabilities(model, apiKey)\n        val messagesArray = buildClaudeMessages(prompt, conversationHistory, systemInstruction, model)\n        val requestPayload = buildClaudeRequestPayload(\n            model, maxTokens, stream = false, systemInstruction, messagesArray, reasoningCapabilities\n        )\n''',
    '''        val metadata = resolveClaudeMetadata(model, apiKey)\n        val messagesArray = buildClaudeMessages(\n            prompt, conversationHistory, systemInstruction, metadata.resolvedModel\n        )\n        val requestPayload = buildClaudeRequestPayload(\n            model, metadata.maxTokens, stream = false, systemInstruction, messagesArray,\n            metadata.reasoningCapabilities\n        )\n''',
    "buffered Claude metadata",
)
app = replace_once(
    app,
    '''        return ClaudeGenerationResult(\n            text = extractClaudeResponseText(parsed) ?: "Received empty content block from Claude.",\n            replayState = extractClaudeReplayState(parsed)\n        )\n    }\n\n    private fun isValidClaudeReplayBlock(block: JsonObject): Boolean =\n        (block[STREAM_TYPE_KEY] as? JsonPrimitive)\n            ?.takeIf { it.isString }\n            ?.contentOrNull\n            ?.isNotBlank() == true\n''',
    '''        val resolvedModel = extractClaudeResponseModel(parsed) ?: metadata.resolvedModel\n        if (resolvedModel != metadata.resolvedModel) invalidateClaudeMetadata(model, apiKey)\n        return ClaudeGenerationResult(\n            text = extractClaudeResponseText(parsed) ?: "Received empty content block from Claude.",\n            replayState = extractClaudeReplayState(parsed),\n            resolvedModel = resolvedModel\n        )\n    }\n\n    private fun JsonObject.hasClaudeStringField(name: String): Boolean =\n        (this[name] as? JsonPrimitive)?.isString == true\n\n    private fun isValidClaudeReplayBlock(block: JsonObject): Boolean =\n        when ((block[STREAM_TYPE_KEY] as? JsonPrimitive)?.contentOrNull) {\n            JSON_TEXT_KEY -> block.hasClaudeStringField(JSON_TEXT_KEY)\n            CLAUDE_THINKING_BLOCK ->\n                block.hasClaudeStringField(JSON_THINKING_KEY) && block.hasClaudeStringField(JSON_SIGNATURE_KEY)\n            CLAUDE_REDACTED_THINKING_BLOCK -> block.hasClaudeStringField(JSON_DATA_KEY)\n            else -> false\n        }\n''',
    "strict Claude replay validation",
)

app = replace_once(
    app,
    '''            CLAUDE_CONTENT_BLOCK_START -> {\n                val block = event[JSON_CONTENT_BLOCK_KEY] as? JsonObject ?: return\n                if (isValidClaudeReplayBlock(block)) blocks[index] = block\n            }\n''',
    '''            CLAUDE_CONTENT_BLOCK_START -> {\n                val block = event[JSON_CONTENT_BLOCK_KEY] as? JsonObject ?: return\n                blocks[index] = if (isValidClaudeReplayBlock(block)) {\n                    block\n                } else {\n                    buildJsonObject { put(STREAM_TYPE_KEY, CLAUDE_UNREPLAYABLE_BLOCK) }\n                }\n            }\n''',
    "stream unsupported Claude block marker",
)

app = replace_once(
    app,
    '''    internal fun extractClaudeResponseText(response: JsonObject): String? =\n        (response[JSON_CONTENT_KEY] as? JsonArray).orEmpty()\n''',
    '''    internal fun extractClaudeResponseModel(response: JsonObject): String? =\n        response[JSON_MODEL_KEY]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }\n\n    internal fun extractClaudeStreamResolvedModel(event: JsonObject): String? = when (\n        event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull\n    ) {\n        CLAUDE_MESSAGE_START -> (event[STREAM_MESSAGE_KEY] as? JsonObject)\n            ?.get(JSON_MODEL_KEY)?.jsonPrimitive?.contentOrNull\n        CLAUDE_CONTENT_BLOCK_START -> (event[JSON_CONTENT_BLOCK_KEY] as? JsonObject)\n            ?.takeIf { it[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull == CLAUDE_FALLBACK_BLOCK }\n            ?.get(JSON_TO_KEY)?.jsonObject\n            ?.get(JSON_MODEL_KEY)?.jsonPrimitive?.contentOrNull\n        else -> null\n    }?.takeIf { it.isNotBlank() }\n\n    internal fun extractClaudeResponseText(response: JsonObject): String? =\n        (response[JSON_CONTENT_KEY] as? JsonArray).orEmpty()\n''',
    "Claude resolved-model extractors",
)

app = replace_once(
    app,
    '''        val maxTokens = resolveClaudeMaxTokens(model, apiKey)\n        val reasoningCapabilities = resolveClaudeReasoningCapabilities(model, apiKey)\n        val requestPayload = buildClaudeRequestPayload(\n            model, maxTokens, stream = true, systemInstruction,\n            buildClaudeMessages(prompt, conversationHistory, systemInstruction, model),\n            reasoningCapabilities\n        )\n''',
    '''        val metadata = resolveClaudeMetadata(model, apiKey)\n        val requestPayload = buildClaudeRequestPayload(\n            model, metadata.maxTokens, stream = true, systemInstruction,\n            buildClaudeMessages(prompt, conversationHistory, systemInstruction, metadata.resolvedModel),\n            metadata.reasoningCapabilities\n        )\n''',
    "streaming Claude metadata",
)
app = replace_once(
    app,
    '''        val replayBlocks = mutableMapOf<Int, JsonObject>()\n        val text = executeSse(\n            request,\n            ::extractClaudeStreamText,\n            ::isClaudeStreamComplete,\n            onTextDelta,\n            onEvent = { event -> applyClaudeReplayEvent(replayBlocks, event) }\n        )\n        return ClaudeGenerationResult(\n            text = text.ifEmpty { "Received empty content block from Claude." },\n            replayState = encodeClaudeStreamReplayState(replayBlocks)\n        )\n''',
    '''        val replayBlocks = mutableMapOf<Int, JsonObject>()\n        var resolvedModel = metadata.resolvedModel\n        val text = executeSse(\n            request,\n            ::extractClaudeStreamText,\n            ::isClaudeStreamComplete,\n            onTextDelta,\n            onEvent = { event ->\n                extractClaudeStreamResolvedModel(event)?.let { resolvedModel = it }\n                applyClaudeReplayEvent(replayBlocks, event)\n            }\n        )\n        if (resolvedModel != metadata.resolvedModel) invalidateClaudeMetadata(model, apiKey)\n        return ClaudeGenerationResult(\n            text = text.ifEmpty { "Received empty content block from Claude." },\n            replayState = encodeClaudeStreamReplayState(replayBlocks),\n            resolvedModel = resolvedModel\n        )\n''',
    "streaming Claude resolved model",
)

app_path.write_text(app)


test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test = replace_once(
    test,
    '''    fun claudeMetadataLookupUsesShortTimeoutAndCachesFallback() {\n        val metadataClient = buildClaudeMetadataHttpClient(okhttp3.OkHttpClient())\n        assertEquals(2_000L, metadataClient.callTimeoutMillis.toLong())\n\n        val service = AiChatService()\n        assertEquals(2048, service.rememberClaudeMaxTokens(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", null))\n        assertEquals(2048, service.rememberClaudeMaxTokens(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", TEST_CLAUDE_MAX_TOKENS))\n        assertEquals(128000, service.rememberClaudeMaxTokens(TEST_CLAUDE_OUTAGE_MODEL, "fixed-key", TEST_CLAUDE_MAX_TOKENS))\n        assertEquals(TEST_CLAUDE_MAX_TOKENS, service.rememberClaudeMaxTokens("claude-healthy", "same-key", TEST_CLAUDE_MAX_TOKENS))\n        assertEquals(TEST_CLAUDE_MAX_TOKENS, service.rememberClaudeMaxTokens("claude-healthy", "same-key", null))\n    }\n''',
    '''    fun claudeMetadataLookupUsesShortTimeoutAndDoesNotPinFailureFallback() {\n        val metadataClient = buildClaudeMetadataHttpClient(okhttp3.OkHttpClient())\n        assertEquals(2_000L, metadataClient.callTimeoutMillis.toLong())\n\n        val service = AiChatService()\n        assertEquals(2048, service.rememberClaudeMaxTokens(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", null))\n        assertEquals(128000, service.rememberClaudeMaxTokens(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", TEST_CLAUDE_MAX_TOKENS))\n        assertEquals(TEST_CLAUDE_MAX_TOKENS, service.rememberClaudeMaxTokens("claude-healthy", "same-key", TEST_CLAUDE_MAX_TOKENS))\n        assertEquals(TEST_CLAUDE_MAX_TOKENS, service.rememberClaudeMaxTokens("claude-healthy", "same-key", null))\n    }\n\n    @Test\n    fun claudeAliasMetadataCachesOnlyTheResolvedConcreteModel() {\n        val service = AiChatService()\n        val alias = "claude-sonnet-4-5"\n        val concrete = "claude-sonnet-4-5-20250929"\n        val apiKey = "alias-key"\n        val capabilities = ClaudeReasoningCapabilities(supportsEnabled = true)\n\n        service.rememberClaudeMetadata(concrete, apiKey, TEST_CLAUDE_MAX_TOKENS, capabilities)\n\n        assertEquals(2048, service.rememberClaudeMaxTokens(alias, apiKey, null))\n        assertEquals(TEST_CLAUDE_MAX_TOKENS, service.rememberClaudeMaxTokens(concrete, apiKey, null))\n        assertEquals(concrete, service.parseClaudeModelId("""{\"model\":\"$concrete\"}"""))\n    }\n''',
    "Claude cache recovery tests",
)

insert_anchor = '''    @Test\n    fun compatibleHistoryKeepsOnlyCurrentProviderAssistantTurns() {\n'''
new_tests = '''    @Test\n    fun claudeRefusalBlockDisablesOpaqueReplayAndFallsBackToVisibleText() {\n        val service = AiChatService()\n        val refusalState = """[{\"type\":\"refusal\",\"refusal\":\"no\"}]"""\n        val response = Json.parseToJsonElement(\n            """{\"model\":\"$TEST_CLAUDE_MODEL\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}"""\n        ).jsonObject\n        assertEquals(null, service.extractClaudeReplayState(response))\n\n        val history = listOf(\n            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = FIRST_QUESTION),\n            ModelChatMessage(\n                id = "claude",\n                sender = CHAT_ROLE_ASSISTANT,\n                provider = AiProvider.CLAUDE,\n                modelName = TEST_CLAUDE_MODEL,\n                text = CLAUDE_ANSWER,\n                providerReplayState = refusalState\n            ),\n            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = FOLLOW_UP)\n        )\n        val messages = service.buildClaudeMessages(FOLLOW_UP, history, modelName = TEST_CLAUDE_MODEL)\n        assertEquals(CLAUDE_ANSWER, messages[1].jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content)\n    }\n\n    @Test\n    fun claudeStreamingUnsupportedBlockInvalidatesReplay() {\n        val service = AiChatService()\n        val blocks = mutableMapOf<Int, JsonObject>()\n        service.applyClaudeReplayEvent(\n            blocks,\n            Json.parseToJsonElement(\n                """{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"refusal\",\"refusal\":\"no\"}}"""\n            ).jsonObject\n        )\n        assertEquals(null, service.encodeClaudeStreamReplayState(blocks))\n    }\n\n    @Test\n    fun claudeResolvedModelComesFromBufferedAndStreamingResponses() {\n        val service = AiChatService()\n        val concrete = "claude-sonnet-4-5-20250929"\n        val buffered = Json.parseToJsonElement("""{\"model\":\"$concrete\"}""").jsonObject\n        val streamStart = Json.parseToJsonElement(\n            """{\"type\":\"message_start\",\"message\":{\"model\":\"$concrete\"}}"""\n        ).jsonObject\n        val fallback = Json.parseToJsonElement(\n            """{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"fallback\",\"to\":{\"model\":\"claude-haiku-4-5-20251001\"}}}"""\n        ).jsonObject\n\n        assertEquals(concrete, service.extractClaudeResponseModel(buffered))\n        assertEquals(concrete, service.extractClaudeStreamResolvedModel(streamStart))\n        assertEquals(TEST_CLAUDE_LEGACY_MODEL, service.extractClaudeStreamResolvedModel(fallback))\n    }\n\n'''
test = replace_once(test, insert_anchor, new_tests + insert_anchor, "Claude replay hardening tests")
test_path.write_text(test)
