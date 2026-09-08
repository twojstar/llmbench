from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected one anchor, found {count}"
    return text.replace(old, new, 1)


def replace_section(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    assert text.count(start_marker) == 1, f"{label}: start marker count != 1"
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[:start] + replacement + text[end:]


app_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
app = app_path.read_text()
app = replace_once(
    app,
    "import com.twojstar.llmbench.data.model.fallbackClaudeReasoningCapabilities\n",
    "",
    "remove name fallback import",
)
app = replace_once(
    app,
    "import kotlinx.coroutines.ensureActive\n",
    "import kotlinx.coroutines.ensureActive\nimport kotlinx.coroutines.sync.Mutex\n",
    "mutex import",
)
app = replace_once(
    app,
    'private const val JSON_MAX_TOKENS_KEY = "max_tokens"\n',
    'private const val JSON_MAX_TOKENS_KEY = "max_tokens"\nprivate const val JSON_STOP_REASON_KEY = "stop_reason"\n',
    "stop reason key",
)
app = replace_once(
    app,
    'private const val CLAUDE_MESSAGE_START = "message_start"\n',
    'private const val CLAUDE_MESSAGE_START = "message_start"\nprivate const val CLAUDE_MESSAGE_DELTA = "message_delta"\n',
    "message delta constant",
)
app = replace_once(
    app,
    'private const val CLAUDE_UNREPLAYABLE_BLOCK = "__unreplayable__"\n',
    'private const val CLAUDE_UNREPLAYABLE_BLOCK = "__unreplayable__"\nprivate const val CLAUDE_STOP_MAX_TOKENS = "max_tokens"\n',
    "partial stop constant",
)
app = replace_once(
    app,
    "private const val CLAUDE_METADATA_TIMEOUT_SECONDS = 2L\n",
    "private const val CLAUDE_METADATA_TIMEOUT_SECONDS = 2L\nprivate const val CLAUDE_ALIAS_CACHE_TTL_MILLIS = 5 * 60 * 1000L\nprivate const val CLAUDE_METADATA_FAILURE_TTL_MILLIS = 30 * 1000L\n",
    "metadata ttl constants",
)
app = replace_once(
    app,
    """    private data class ClaudeGenerationResult(
        val text: String,
        val replayState: String?,
        val resolvedModel: String
    )

    private data class ClaudeRuntimeMetadata(
        val maxTokens: Int,
        val reasoningCapabilities: ClaudeReasoningCapabilities,
        val resolvedModel: String
    )

    private data class ClaudeMaxTokensCacheKey(
        val model: String,
        val credentialFingerprint: String
    )
""",
    """    private data class ClaudeGenerationResult(
        val text: String,
        val replayState: String?,
        val resolvedModel: String,
        val isPartial: Boolean
    )

    internal data class ClaudeRuntimeMetadata(
        val maxTokens: Int,
        val reasoningCapabilities: ClaudeReasoningCapabilities,
        val resolvedModel: String
    )

    private data class ClaudeMetadataCacheEntry(
        val metadata: ClaudeRuntimeMetadata,
        val expiresAtMillis: Long?
    )

    private data class ClaudeMetadataCacheKey(
        val model: String,
        val credentialFingerprint: String
    )
""",
    "Claude result/cache data classes",
)
app = replace_once(
    app,
    """    private val claudeMaxTokensByModelAndCredential =
        ConcurrentHashMap<ClaudeMaxTokensCacheKey, Int>()
    private val claudeReasoningByModelAndCredential =
        ConcurrentHashMap<ClaudeMaxTokensCacheKey, ClaudeReasoningCapabilities>()
""",
    """    private val claudeMetadataByModelAndCredential =
        ConcurrentHashMap<ClaudeMetadataCacheKey, ClaudeMetadataCacheEntry>()
    private val claudeMetadataMutex = Mutex()
""",
    "single Claude metadata cache",
)
app = replace_once(
    app,
    """        var resolvedModel = effectiveModel
        var providerReplayState: String? = null
""",
    """        var resolvedModel = effectiveModel
        var providerReplayState: String? = null
        var isPartial = false
""",
    "partial result state",
)
app = replace_once(
    app,
    """                        providerReplayState = result.replayState
                        resolvedModel = result.resolvedModel
                        result.text
""",
    """                        providerReplayState = result.replayState
                        resolvedModel = result.resolvedModel
                        isPartial = result.isPartial
                        result.text
""",
    "Claude result propagation",
)
app = replace_once(
    app,
    """                        isError = false,
                        isSimulated = false,
                        latencyMs = latency,
""",
    """                        isError = false,
                        isSimulated = false,
                        isPartial = isPartial,
                        latencyMs = latency,
""",
    "persist partial state",
)

cache_block = """    private fun claudeMetadataCacheKey(model: String, apiKey: String): ClaudeMetadataCacheKey {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return ClaudeMetadataCacheKey(model, fingerprint)
    }

    internal fun readClaudeMetadataCache(
        model: String,
        apiKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClaudeRuntimeMetadata? {
        val cacheKey = claudeMetadataCacheKey(model, apiKey)
        val entry = claudeMetadataByModelAndCredential[cacheKey] ?: return null
        if (entry.expiresAtMillis != null && nowMillis >= entry.expiresAtMillis) {
            claudeMetadataByModelAndCredential.remove(cacheKey, entry)
            return null
        }
        return entry.metadata
    }

    internal fun rememberClaudeMetadata(
        requestedModel: String,
        resolvedModel: String,
        apiKey: String,
        reportedMaxTokens: Int?,
        reportedReasoning: ClaudeReasoningCapabilities?,
        nowMillis: Long = System.currentTimeMillis()
    ): ClaudeRuntimeMetadata {
        val metadata = ClaudeRuntimeMetadata(
            maxTokens = reportedMaxTokens?.takeIf { it > 0 } ?: CLAUDE_MAX_TOKENS_COMPAT_FALLBACK,
            reasoningCapabilities = reportedReasoning ?: ClaudeReasoningCapabilities(),
            resolvedModel = resolvedModel
        )
        val metadataIncomplete = reportedMaxTokens == null || reportedReasoning == null
        val concreteExpiry = if (metadataIncomplete) nowMillis + CLAUDE_METADATA_FAILURE_TTL_MILLIS else null
        claudeMetadataByModelAndCredential[claudeMetadataCacheKey(resolvedModel, apiKey)] =
            ClaudeMetadataCacheEntry(metadata, concreteExpiry)

        val requestedExpiry = when {
            metadataIncomplete -> nowMillis + CLAUDE_METADATA_FAILURE_TTL_MILLIS
            requestedModel != resolvedModel -> nowMillis + CLAUDE_ALIAS_CACHE_TTL_MILLIS
            else -> null
        }
        claudeMetadataByModelAndCredential[claudeMetadataCacheKey(requestedModel, apiKey)] =
            ClaudeMetadataCacheEntry(metadata, requestedExpiry)
        return metadata
    }

    internal fun rememberClaudeMetadataFailure(
        model: String,
        apiKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClaudeRuntimeMetadata {
        val metadata = ClaudeRuntimeMetadata(
            maxTokens = CLAUDE_MAX_TOKENS_COMPAT_FALLBACK,
            reasoningCapabilities = ClaudeReasoningCapabilities(),
            resolvedModel = model
        )
        claudeMetadataByModelAndCredential[claudeMetadataCacheKey(model, apiKey)] =
            ClaudeMetadataCacheEntry(metadata, nowMillis + CLAUDE_METADATA_FAILURE_TTL_MILLIS)
        return metadata
    }

    private fun invalidateClaudeMetadata(apiKey: String, vararg models: String) {
        models.distinct().forEach { model ->
            claudeMetadataByModelAndCredential.remove(claudeMetadataCacheKey(model, apiKey))
        }
    }

    private suspend fun resolveClaudeMetadata(model: String, apiKey: String): ClaudeRuntimeMetadata {
        readClaudeMetadataCache(model, apiKey)?.let { return it }
        claudeMetadataMutex.lock()
        try {
            readClaudeMetadataCache(model, apiKey)?.let { return it }
            return try {
                val request = buildClaudeModelMetadataRequest(model, apiKey)
                val responseBody = executeCancellableJson(
                    request,
                    httpErrorContext = "Anthropic model metadata",
                    client = claudeMetadataHttpClient
                )
                val resolvedModel = parseClaudeModelId(responseBody) ?: model
                rememberClaudeMetadata(
                    requestedModel = model,
                    resolvedModel = resolvedModel,
                    apiKey = apiKey,
                    reportedMaxTokens = parseClaudeModelMaxTokens(responseBody),
                    reportedReasoning = parseClaudeReasoningCapabilities(responseBody)
                )
            } catch (_: IOException) {
                rememberClaudeMetadataFailure(model, apiKey)
            }
        } finally {
            claudeMetadataMutex.unlock()
        }
    }

"""
app = replace_section(
    app,
    "    private fun claudeMaxTokensCacheKey",
    "    internal fun buildClaudeRequestPayload(",
    cache_block,
    "Claude metadata cache block",
)
app = replace_once(
    app,
    "reasoningCapabilities: ClaudeReasoningCapabilities = fallbackClaudeReasoningCapabilities(model)",
    "reasoningCapabilities: ClaudeReasoningCapabilities",
    "required reasoning capabilities",
)
app = replace_once(
    app,
    """        val requestPayload = buildClaudeRequestPayload(
            model, metadata.maxTokens, stream = false, systemInstruction, messagesArray,
            metadata.reasoningCapabilities
        )
""",
    """        val requestPayload = buildClaudeRequestPayload(
            metadata.resolvedModel, metadata.maxTokens, stream = false, systemInstruction, messagesArray,
            metadata.reasoningCapabilities
        )
""",
    "buffered request pins concrete model",
)
app = replace_once(
    app,
    """        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val resolvedModel = extractClaudeResponseModel(parsed) ?: metadata.resolvedModel
        if (resolvedModel != metadata.resolvedModel) invalidateClaudeMetadata(model, apiKey)
        return ClaudeGenerationResult(
            text = extractClaudeResponseText(parsed) ?: "Received empty content block from Claude.",
            replayState = extractClaudeReplayState(parsed),
            resolvedModel = resolvedModel
        )
""",
    """        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val resolvedModel = extractClaudeResponseModel(parsed) ?: metadata.resolvedModel
        val isPartial = isClaudePartialStopReason(extractClaudeStopReason(parsed))
        if (resolvedModel != metadata.resolvedModel) {
            invalidateClaudeMetadata(apiKey, model, metadata.resolvedModel)
        }
        return ClaudeGenerationResult(
            text = extractClaudeResponseText(parsed) ?: "Received empty content block from Claude.",
            replayState = if (isPartial) null else extractClaudeReplayState(parsed),
            resolvedModel = resolvedModel,
            isPartial = isPartial
        )
""",
    "buffered Claude partial handling",
)
app = replace_once(
    app,
    """    private fun isValidClaudeReplayBlock(block: JsonObject): Boolean =
        when ((block[STREAM_TYPE_KEY] as? JsonPrimitive)?.contentOrNull) {
""",
    """    private fun isSupportedClaudeReplayBlockStart(block: JsonObject): Boolean =
        when ((block[STREAM_TYPE_KEY] as? JsonPrimitive)?.contentOrNull) {
            JSON_TEXT_KEY -> block.hasClaudeStringField(JSON_TEXT_KEY)
            CLAUDE_THINKING_BLOCK -> block.hasClaudeStringField(JSON_THINKING_KEY)
            CLAUDE_REDACTED_THINKING_BLOCK -> block.hasClaudeStringField(JSON_DATA_KEY)
            else -> false
        }

    private fun isValidClaudeReplayBlock(block: JsonObject): Boolean =
        when ((block[STREAM_TYPE_KEY] as? JsonPrimitive)?.contentOrNull) {
""",
    "delay strict stream validation",
)
app = replace_once(
    app,
    """    internal fun extractClaudeResponseModel(response: JsonObject): String? =
        response[JSON_MODEL_KEY]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    internal fun extractClaudeStreamResolvedModel(event: JsonObject): String? = when (
""",
    """    internal fun extractClaudeResponseModel(response: JsonObject): String? =
        response[JSON_MODEL_KEY]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    internal fun extractClaudeStopReason(response: JsonObject): String? =
        (response[JSON_STOP_REASON_KEY] as? JsonPrimitive)?.contentOrNull

    internal fun extractClaudeStreamStopReason(event: JsonObject): String? {
        if (event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull != CLAUDE_MESSAGE_DELTA) return null
        return ((event[JSON_DELTA_KEY] as? JsonObject)?.get(JSON_STOP_REASON_KEY) as? JsonPrimitive)
            ?.contentOrNull
    }

    internal fun isClaudePartialStopReason(stopReason: String?): Boolean =
        stopReason == CLAUDE_STOP_MAX_TOKENS

    internal fun extractClaudeStreamResolvedModel(event: JsonObject): String? = when (
""",
    "Claude stop reason extraction",
)
app = replace_once(
    app,
    """                blocks[index] = if (isValidClaudeReplayBlock(block)) {
                    block
                } else {
""",
    """                blocks[index] = if (isSupportedClaudeReplayBlockStart(block)) {
                    block
                } else {
""",
    "stream start validation",
)
app = replace_once(
    app,
    """        val requestPayload = buildClaudeRequestPayload(
            model, metadata.maxTokens, stream = true, systemInstruction,
            buildClaudeMessages(prompt, conversationHistory, systemInstruction, metadata.resolvedModel),
            metadata.reasoningCapabilities
        )
""",
    """        val requestPayload = buildClaudeRequestPayload(
            metadata.resolvedModel, metadata.maxTokens, stream = true, systemInstruction,
            buildClaudeMessages(prompt, conversationHistory, systemInstruction, metadata.resolvedModel),
            metadata.reasoningCapabilities
        )
""",
    "stream request pins concrete model",
)
app = replace_once(
    app,
    """        val replayBlocks = mutableMapOf<Int, JsonObject>()
        var resolvedModel = metadata.resolvedModel
        val text = executeSse(
""",
    """        val replayBlocks = mutableMapOf<Int, JsonObject>()
        var resolvedModel = metadata.resolvedModel
        var stopReason: String? = null
        val text = executeSse(
""",
    "stream stop state",
)
app = replace_once(
    app,
    """            onEvent = { event ->
                extractClaudeStreamResolvedModel(event)?.let { resolvedModel = it }
                applyClaudeReplayEvent(replayBlocks, event)
            }
        )
        if (resolvedModel != metadata.resolvedModel) invalidateClaudeMetadata(model, apiKey)
        return ClaudeGenerationResult(
            text = text.ifEmpty { "Received empty content block from Claude." },
            replayState = encodeClaudeStreamReplayState(replayBlocks),
            resolvedModel = resolvedModel
        )
""",
    """            onEvent = { event ->
                extractClaudeStreamResolvedModel(event)?.let { resolvedModel = it }
                extractClaudeStreamStopReason(event)?.let { stopReason = it }
                applyClaudeReplayEvent(replayBlocks, event)
            }
        )
        val isPartial = isClaudePartialStopReason(stopReason)
        if (resolvedModel != metadata.resolvedModel) {
            invalidateClaudeMetadata(apiKey, model, metadata.resolvedModel)
        }
        return ClaudeGenerationResult(
            text = text.ifEmpty { "Received empty content block from Claude." },
            replayState = if (isPartial) null else encodeClaudeStreamReplayState(replayBlocks),
            resolvedModel = resolvedModel,
            isPartial = isPartial
        )
""",
    "stream Claude partial handling",
)
app_path.write_text(app)


shared_path = Path("shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilities.kt")
shared = shared_path.read_text()
shared = replace_section(
    shared,
    "fun fallbackClaudeReasoningCapabilities(model: String): ClaudeReasoningCapabilities {",
    "fun resolveClaudeThinkingBudget(maxTokens: Int): Int? {",
    "",
    "remove unverified name fallback",
)
shared_path.write_text(shared)


shared_test_path = Path("shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilitiesTest.kt")
shared_test = shared_test_path.read_text()
shared_test = replace_section(
    shared_test,
    "    @Test\n    fun claudeReasoningFallbackIsSharedProviderPolicy() {",
    "    @Test\n    fun claudeLegacyThinkingBudgetIsBoundedByOutputLimit() {",
    "",
    "remove name fallback test",
)
shared_test_path.write_text(shared_test)


test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test = replace_once(
    test,
    'private const val TEST_CLAUDE_MAX_TOKENS = 128000\n',
    'private const val TEST_CLAUDE_MAX_TOKENS = 128000\nprivate const val TEST_CLAUDE_MAX_TOKENS_TEXT = "128000"\nprivate const val TEST_CLAUDE_SAME_KEY = "same-key"\n',
    "test literal constants",
)
metadata_tests = """    @Test
    fun claudeMetadataCacheIsAtomicShortLivedForAliasesAndRecoverableAfterOutage() {
        val service = AiChatService()
        val alias = "claude-sonnet-4-5"
        val concrete = "claude-sonnet-4-5-20250929"
        val apiKey = "alias-key"
        val now = 10_000L
        val capabilities = ClaudeReasoningCapabilities(supportsEnabled = true)

        val metadata = service.rememberClaudeMetadata(
            alias, concrete, apiKey, TEST_CLAUDE_MAX_TOKENS, capabilities, now
        )
        assertEquals(concrete, metadata.resolvedModel)
        assertEquals(
            concrete,
            service.readClaudeMetadataCache(alias, apiKey, now + 1)?.resolvedModel
        )
        assertEquals(
            TEST_CLAUDE_MAX_TOKENS,
            service.readClaudeMetadataCache(concrete, apiKey, now + 301_000)?.maxTokens
        )
        assertEquals(null, service.readClaudeMetadataCache(alias, apiKey, now + 301_000))

        val failure = service.rememberClaudeMetadataFailure(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", now)
        assertEquals(2048, failure.maxTokens)
        assertEquals(ClaudeReasoningCapabilities(), failure.reasoningCapabilities)
        assertEquals(
            2048,
            service.readClaudeMetadataCache(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", now + 29_000)?.maxTokens
        )
        assertEquals(
            null,
            service.readClaudeMetadataCache(TEST_CLAUDE_OUTAGE_MODEL, "bad-key", now + 31_000)
        )
        assertEquals(concrete, service.parseClaudeModelId("""{"id":"$concrete"}"""))
        assertEquals(null, service.parseClaudeModelId("""{"model":"$concrete"}"""))
    }

"""
test = replace_section(
    test,
    "    @Test\n    fun claudeMetadataLookupUsesShortTimeoutAndDoesNotPinFailureFallback() {",
    "    @Test\n    fun claudePayloadUsesResolvedOutputLimitInBufferedAndStreamingModes() {",
    metadata_tests + "    @Test\n    fun claudePayloadUsesResolvedOutputLimitInBufferedAndStreamingModes() {",
    "replace metadata cache tests",
)
test = replace_once(
    test,
    """        val buffered = service.buildClaudeRequestPayload(TEST_CLAUDE_MODEL, 128000, false, SYSTEM_PROMPT, messages)
        val streaming = service.buildClaudeRequestPayload(TEST_CLAUDE_MODEL, 128000, true, null, messages)

        assertEquals("128000", buffered.getValue("max_tokens").jsonPrimitive.content)
""",
    """        val noReasoning = ClaudeReasoningCapabilities()
        val buffered = service.buildClaudeRequestPayload(
            TEST_CLAUDE_MODEL, TEST_CLAUDE_MAX_TOKENS, false, SYSTEM_PROMPT, messages, noReasoning
        )
        val streaming = service.buildClaudeRequestPayload(
            TEST_CLAUDE_MODEL, TEST_CLAUDE_MAX_TOKENS, true, null, messages, noReasoning
        )

        assertEquals(TEST_CLAUDE_MAX_TOKENS_TEXT, buffered.getValue("max_tokens").jsonPrimitive.content)
""",
    "basic payload explicit capabilities",
)
test = replace_once(
    test,
    '        assertEquals("128000", streaming.getValue("max_tokens").jsonPrimitive.content)\n',
    '        assertEquals(TEST_CLAUDE_MAX_TOKENS_TEXT, streaming.getValue("max_tokens").jsonPrimitive.content)\n        assertFalse("thinking" in streaming)\n        assertFalse("output_config" in streaming)\n',
    "payload omits unverified reasoning",
)
test = test.replace('"same-key"', 'TEST_CLAUDE_SAME_KEY')
# Undo the declaration replacement itself if the global replacement touched it.
test = test.replace('private const val TEST_CLAUDE_SAME_KEY = TEST_CLAUDE_SAME_KEY', 'private const val TEST_CLAUDE_SAME_KEY = "same-key"')
test = replace_once(
    test,
    '"""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}"""',
    '"""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}"""',
    "thinking start without signature",
)
stop_test = """    @Test
    fun claudeMaxTokenStopReasonMarksBufferedAndStreamingResponsesPartial() {
        val service = AiChatService()
        val buffered = Json.parseToJsonElement(
            """{"stop_reason":"max_tokens"}"""
        ).jsonObject
        val streamed = Json.parseToJsonElement(
            """{"type":"message_delta","delta":{"stop_reason":"max_tokens"}}"""
        ).jsonObject

        assertTrue(service.isClaudePartialStopReason(service.extractClaudeStopReason(buffered)))
        assertTrue(service.isClaudePartialStopReason(service.extractClaudeStreamStopReason(streamed)))
        assertFalse(service.isClaudePartialStopReason("end_turn"))
    }

"""
test = replace_once(
    test,
    "    @Test\n    fun claudeRefusalBlockDisablesOpaqueReplayAndFallsBackToVisibleText() {",
    stop_test + "    @Test\n    fun claudeRefusalBlockDisablesOpaqueReplayAndFallsBackToVisibleText() {",
    "Claude partial stop regression",
)
test_path.write_text(test)


docs_path = Path("docs/provider-runtime.md")
docs = docs_path.read_text()
docs = replace_once(
    docs,
    """Claude reasoning is model-aware through the Anthropic Models API. LlmBench reads `capabilities.thinking.types` and effort support from the same short model-metadata lookup already used for `max_tokens`: adaptive-capable models receive `thinking: {type: \"adaptive\"}` and high effort when the model reports it, while legacy extended-thinking models receive a conservative budget only when `budget_tokens < max_tokens` can be satisfied. If metadata is unavailable, one centralized family fallback covers the known Claude 4.5-5 lines instead of scattering model-name checks through UI or transport code.

When thinking is active, buffered responses retain the complete ordered assistant `content` array as ephemeral provider replay state. Streaming responses reconstruct the provider blocks from `content_block_start` plus `thinking_delta`, `signature_delta`, and `text_delta` events. `thinking`, `signature`, and `redacted_thinking` data are never rendered, exported, logged, or rewritten; they are replayed only for the exact model that produced them. A model switch or malformed state falls back to visible assistant text.
""",
    """Claude reasoning is model-aware through the Anthropic Models API. LlmBench reads `capabilities.thinking.types` and effort support from the same short model-metadata lookup used for `max_tokens`: adaptive-capable models receive `thinking: {type: \"adaptive\"}` and high effort only when reported, while legacy extended-thinking models receive a conservative budget only when `budget_tokens < max_tokens` can be satisfied. If metadata is unavailable or incomplete, generation uses the 2048 compatibility output limit and omits explicit thinking/effort controls rather than guessing capability support from the model name. That conservative outage entry is cached only briefly before metadata is retried.

Models API aliases are resolved to the returned concrete `id`. Successful alias metadata is reused briefly, while requests and replay scoping are pinned to that concrete model so an alias retarget cannot mix opaque thinking state across models. Buffered and streaming Messages responses can still report the actual serving model; a mismatch invalidates the linked requested/concrete cache entries.

When thinking is active, buffered responses retain the complete ordered assistant `content` array as ephemeral provider replay state. Streaming responses reconstruct the provider blocks from `content_block_start` plus `thinking_delta`, `signature_delta`, and `text_delta` events, delaying strict thinking-block validation until the signature delta has arrived. `thinking`, `signature`, and `redacted_thinking` data are never rendered, exported, logged, or rewritten; they are replayed only for the exact model that produced them. Responses stopped by `max_tokens` are marked partial and excluded from later history entirely. A model switch or malformed state falls back to visible assistant text.
""",
    "Claude runtime docs",
)
docs = replace_once(
    docs,
    "- [x] Resolve Claude `max_tokens` from the Anthropic Models API per model with a short independent lookup budget; cache either the reported value or the old 2048 compatibility fallback per model and credential fingerprint so metadata outages do not repeatedly delay generation and replacing a bad key can refresh metadata.\n",
    "- [x] Resolve Claude runtime metadata from the Anthropic Models API with a short independent lookup budget; cache verified model data, cache alias mappings briefly, and use a short-lived 2048/no-reasoning outage entry so repeated failures do not block every generation while recovery remains automatic.\n",
    "Claude metadata TODO docs",
)
docs_path.write_text(docs)

# Safety checks: portable fallback must be gone and the runtime must have both partial paths.
for path in (app_path, shared_path, shared_test_path, test_path, docs_path):
    text = path.read_text()
    assert "fallbackClaudeReasoningCapabilities" not in text, f"stale fallback in {path}"
assert app_path.read_text().count("isPartial = isPartial") == 1
assert "isSupportedClaudeReplayBlockStart" in app_path.read_text()
assert "CLAUDE_MESSAGE_DELTA" in app_path.read_text()
