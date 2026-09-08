from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected one anchor, found {count}"
    return text.replace(old, new, 1)


service_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
service = service_path.read_text()

service = replace_once(
    service,
    'private const val JSON_INSTRUCTIONS_KEY = "instructions"\n',
    'private const val JSON_INSTRUCTIONS_KEY = "instructions"\n'
    'private const val JSON_CAPABILITIES_KEY = "capabilities"\n'
    'private const val JSON_THINKING_KEY = "thinking"\n'
    'private const val JSON_TYPES_KEY = "types"\n'
    'private const val JSON_SUPPORTED_KEY = "supported"\n'
    'private const val JSON_ADAPTIVE_KEY = "adaptive"\n'
    'private const val JSON_ENABLED_KEY = "enabled"\n'
    'private const val JSON_EFFORT_KEY = "effort"\n'
    'private const val JSON_HIGH_KEY = "high"\n'
    'private const val JSON_OUTPUT_CONFIG_KEY = "output_config"\n'
    'private const val JSON_BUDGET_TOKENS_KEY = "budget_tokens"\n'
    'private const val JSON_SIGNATURE_KEY = "signature"\n'
    'private const val JSON_INDEX_KEY = "index"\n'
    'private const val JSON_CONTENT_BLOCK_KEY = "content_block"\n',
    "Claude JSON constants",
)
service = replace_once(
    service,
    'private const val CLAUDE_CONTENT_BLOCK_DELTA = "content_block_delta"\nprivate const val CLAUDE_TEXT_DELTA = "text_delta"\n',
    'private const val CLAUDE_CONTENT_BLOCK_START = "content_block_start"\n'
    'private const val CLAUDE_CONTENT_BLOCK_DELTA = "content_block_delta"\n'
    'private const val CLAUDE_TEXT_DELTA = "text_delta"\n'
    'private const val CLAUDE_THINKING_DELTA = "thinking_delta"\n'
    'private const val CLAUDE_SIGNATURE_DELTA = "signature_delta"\n'
    'private const val CLAUDE_THINKING_BLOCK = "thinking"\n'
    'private const val CLAUDE_REDACTED_THINKING_BLOCK = "redacted_thinking"\n'
    'private const val CLAUDE_MIN_THINKING_BUDGET = 1024\n'
    'private const val CLAUDE_DEFAULT_THINKING_BUDGET = 4096\n',
    "Claude stream constants",
)
service = replace_once(
    service,
    'internal fun buildClaudeMetadataHttpClient(baseClient: OkHttpClient): OkHttpClient =\n'
    '    baseClient.newBuilder()\n'
    '        .callTimeout(CLAUDE_METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)\n'
    '        .build()\n\n'
    'class AiChatService {\n',
    'internal fun buildClaudeMetadataHttpClient(baseClient: OkHttpClient): OkHttpClient =\n'
    '    baseClient.newBuilder()\n'
    '        .callTimeout(CLAUDE_METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)\n'
    '        .build()\n\n'
    'internal data class ClaudeReasoningCapabilities(\n'
    '    val supportsAdaptive: Boolean = false,\n'
    '    val supportsEnabled: Boolean = false,\n'
    '    val supportsHighEffort: Boolean = false\n'
    ')\n\n'
    'class AiChatService {\n',
    "Claude capability data class",
)
service = replace_once(
    service,
    '    private data class OpenAiGenerationResult(\n'
    '        val text: String,\n'
    '        val replayState: String?\n'
    '    )\n\n',
    '    private data class OpenAiGenerationResult(\n'
    '        val text: String,\n'
    '        val replayState: String?\n'
    '    )\n\n'
    '    private data class ClaudeGenerationResult(\n'
    '        val text: String,\n'
    '        val replayState: String?\n'
    '    )\n\n',
    "Claude result",
)
service = replace_once(
    service,
    '    private val claudeMaxTokensByModelAndCredential =\n'
    '        ConcurrentHashMap<ClaudeMaxTokensCacheKey, Int>()\n',
    '    private val claudeMaxTokensByModelAndCredential =\n'
    '        ConcurrentHashMap<ClaudeMaxTokensCacheKey, Int>()\n'
    '    private val claudeReasoningByModelAndCredential =\n'
    '        ConcurrentHashMap<ClaudeMaxTokensCacheKey, ClaudeReasoningCapabilities>()\n',
    "Claude capability cache",
)

old_claude_branch = '''                    AiProvider.CLAUDE -> if (onTextDelta != null) {
                        callClaudeStreamApi(
                            prompt, effectiveModel, key, systemInstruction, conversationHistory, onTextDelta
                        )
                    } else {
                        callClaudeApi(prompt, effectiveModel, key, systemInstruction, conversationHistory)
                    }
'''
new_claude_branch = '''                    AiProvider.CLAUDE -> {
                        val result = if (onTextDelta != null) {
                            callClaudeStreamApi(
                                prompt, effectiveModel, key, systemInstruction, conversationHistory, onTextDelta
                            )
                        } else {
                            callClaudeApi(prompt, effectiveModel, key, systemInstruction, conversationHistory)
                        }
                        providerReplayState = result.replayState
                        result.text
                    }
'''
service = replace_once(service, old_claude_branch, new_claude_branch, "Claude generation branch")

old_messages = '''    internal fun buildClaudeMessages(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        systemInstruction: String? = null
    ): JsonArray = buildJsonArray {
        buildBoundedProviderTextTurns(
            prompt, conversationHistory, AiProvider.CLAUDE, systemInstruction
        ).forEach { turn ->
            addJsonObject {
                put(JSON_ROLE_KEY, turn.role)
                put(JSON_CONTENT_KEY, turn.text)
            }
        }
    }
'''
new_messages = '''    internal fun buildClaudeMessages(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        systemInstruction: String? = null,
        modelName: String? = null
    ): JsonArray = buildJsonArray {
        buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = conversationHistory,
            provider = AiProvider.CLAUDE,
            systemInstruction = systemInstruction,
            replayStateModelName = modelName,
            replayStateValidator = { state -> parseClaudeReplayState(state).isNotEmpty() }
        ).forEach { turn ->
            addJsonObject {
                put(JSON_ROLE_KEY, turn.role)
                val replayBlocks = turn.providerReplayState
                    ?.let(::parseClaudeReplayState)
                    .orEmpty()
                if (turn.role == CHAT_ROLE_ASSISTANT && replayBlocks.isNotEmpty()) {
                    put(JSON_CONTENT_KEY, JsonArray(replayBlocks))
                } else {
                    put(JSON_CONTENT_KEY, turn.text)
                }
            }
        }
    }
'''
service = replace_once(service, old_messages, new_messages, "Claude messages")

max_tokens_anchor = '''    internal fun parseClaudeModelMaxTokens(rawJson: String): Int? = runCatching {
        json.parseToJsonElement(rawJson).jsonObject[JSON_MAX_TOKENS_KEY]?.jsonPrimitive?.intOrNull
    }.getOrNull()?.takeIf { it > 0 }

'''
reasoning_helpers = '''    internal fun parseClaudeReasoningCapabilities(rawJson: String): ClaudeReasoningCapabilities? = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val capabilities = root[JSON_CAPABILITIES_KEY] as? JsonObject ?: return@runCatching null
        val thinking = capabilities[JSON_THINKING_KEY] as? JsonObject ?: return@runCatching null
        if (thinking[JSON_SUPPORTED_KEY]?.jsonPrimitive?.booleanOrNull != true) {
            return@runCatching ClaudeReasoningCapabilities()
        }
        val types = thinking[JSON_TYPES_KEY] as? JsonObject
        val effort = capabilities[JSON_EFFORT_KEY] as? JsonObject
        ClaudeReasoningCapabilities(
            supportsAdaptive = types.supportsClaudeCapability(JSON_ADAPTIVE_KEY),
            supportsEnabled = types.supportsClaudeCapability(JSON_ENABLED_KEY),
            supportsHighEffort = effort.supportsClaudeCapability(JSON_HIGH_KEY)
        )
    }.getOrNull()

    private fun JsonObject?.supportsClaudeCapability(name: String): Boolean =
        ((this?.get(name) as? JsonObject)?.get(JSON_SUPPORTED_KEY) as? JsonPrimitive)
            ?.booleanOrNull == true

    internal fun fallbackClaudeReasoningCapabilities(model: String): ClaudeReasoningCapabilities {
        val normalized = model.lowercase()
        return when {
            normalized.startsWith("claude-haiku-4-5") ||
                normalized.startsWith("claude-sonnet-4-5") ||
                normalized.startsWith("claude-opus-4-5") -> ClaudeReasoningCapabilities(
                supportsEnabled = true
            )
            normalized.startsWith("claude-sonnet-4-6") ||
                normalized.startsWith("claude-opus-4-6") ||
                normalized.startsWith("claude-opus-4-7") ||
                normalized.startsWith("claude-opus-4-8") ||
                normalized.startsWith("claude-sonnet-5") ||
                normalized.startsWith("claude-opus-5") ||
                normalized.startsWith("claude-fable-5") -> ClaudeReasoningCapabilities(
                supportsAdaptive = true,
                supportsHighEffort = true
            )
            else -> ClaudeReasoningCapabilities()
        }
    }

    internal fun resolveClaudeThinkingBudget(maxTokens: Int): Int? {
        val budget = minOf(CLAUDE_DEFAULT_THINKING_BUDGET, maxTokens / 2)
        return budget.takeIf { it >= CLAUDE_MIN_THINKING_BUDGET && it < maxTokens }
    }

'''
service = replace_once(service, max_tokens_anchor, max_tokens_anchor + reasoning_helpers, "Claude metadata helpers")

old_resolve = '''    private suspend fun resolveClaudeMaxTokens(model: String, apiKey: String): Int {
        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)
        claudeMaxTokensByModelAndCredential[cacheKey]?.let { return it }
        return try {
            val request = buildClaudeModelMetadataRequest(model, apiKey)
            val responseBody = executeCancellableJson(
                request,
                httpErrorContext = "Anthropic model metadata",
                client = claudeMetadataHttpClient
            )
            rememberClaudeMaxTokens(model, apiKey, parseClaudeModelMaxTokens(responseBody))
        } catch (_: IOException) {
            rememberClaudeMaxTokens(model, apiKey, null)
        }
    }
'''
new_resolve = '''    private suspend fun resolveClaudeMaxTokens(model: String, apiKey: String): Int {
        val cacheKey = claudeMaxTokensCacheKey(model, apiKey)
        claudeMaxTokensByModelAndCredential[cacheKey]?.let { cached ->
            claudeReasoningByModelAndCredential.putIfAbsent(
                cacheKey,
                fallbackClaudeReasoningCapabilities(model)
            )
            return cached
        }
        return try {
            val request = buildClaudeModelMetadataRequest(model, apiKey)
            val responseBody = executeCancellableJson(
                request,
                httpErrorContext = "Anthropic model metadata",
                client = claudeMetadataHttpClient
            )
            claudeReasoningByModelAndCredential.putIfAbsent(
                cacheKey,
                parseClaudeReasoningCapabilities(responseBody)
                    ?: fallbackClaudeReasoningCapabilities(model)
            )
            rememberClaudeMaxTokens(model, apiKey, parseClaudeModelMaxTokens(responseBody))
        } catch (_: IOException) {
            claudeReasoningByModelAndCredential.putIfAbsent(
                cacheKey,
                fallbackClaudeReasoningCapabilities(model)
            )
            rememberClaudeMaxTokens(model, apiKey, null)
        }
    }

    private fun resolveClaudeReasoningCapabilities(model: String, apiKey: String): ClaudeReasoningCapabilities =
        claudeReasoningByModelAndCredential[claudeMaxTokensCacheKey(model, apiKey)]
            ?: fallbackClaudeReasoningCapabilities(model)
'''
service = replace_once(service, old_resolve, new_resolve, "Claude metadata resolver")

old_payload = '''    internal fun buildClaudeRequestPayload(
        model: String,
        maxTokens: Int,
        stream: Boolean,
        systemInstruction: String?,
        messages: JsonArray
    ): JsonObject = buildJsonObject {
        put(JSON_MODEL_KEY, model)
        put(JSON_MAX_TOKENS_KEY, maxTokens)
        if (stream) put(JSON_STREAM_KEY, true)
        if (!systemInstruction.isNullOrBlank()) put(JSON_SYSTEM_KEY, systemInstruction)
        put(JSON_MESSAGES_KEY, messages)
    }
'''
new_payload = '''    internal fun buildClaudeRequestPayload(
        model: String,
        maxTokens: Int,
        stream: Boolean,
        systemInstruction: String?,
        messages: JsonArray,
        reasoningCapabilities: ClaudeReasoningCapabilities = fallbackClaudeReasoningCapabilities(model)
    ): JsonObject = buildJsonObject {
        put(JSON_MODEL_KEY, model)
        put(JSON_MAX_TOKENS_KEY, maxTokens)
        if (stream) put(JSON_STREAM_KEY, true)
        if (!systemInstruction.isNullOrBlank()) put(JSON_SYSTEM_KEY, systemInstruction)
        when {
            reasoningCapabilities.supportsAdaptive -> {
                putJsonObject(JSON_THINKING_KEY) {
                    put(STREAM_TYPE_KEY, JSON_ADAPTIVE_KEY)
                }
                if (reasoningCapabilities.supportsHighEffort) {
                    putJsonObject(JSON_OUTPUT_CONFIG_KEY) {
                        put(JSON_EFFORT_KEY, JSON_HIGH_KEY)
                    }
                }
            }
            reasoningCapabilities.supportsEnabled -> resolveClaudeThinkingBudget(maxTokens)?.let { budget ->
                putJsonObject(JSON_THINKING_KEY) {
                    put(STREAM_TYPE_KEY, JSON_ENABLED_KEY)
                    put(JSON_BUDGET_TOKENS_KEY, budget)
                }
            }
        }
        put(JSON_MESSAGES_KEY, messages)
    }
'''
service = replace_once(service, old_payload, new_payload, "Claude request payload")

old_call = '''    private suspend fun callClaudeApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>
    ): String {
        val messagesArray = buildClaudeMessages(prompt, conversationHistory, systemInstruction)
        val maxTokens = resolveClaudeMaxTokens(model, apiKey)

        val requestPayload = buildClaudeRequestPayload(
            model, maxTokens, stream = false, systemInstruction, messagesArray
        )

        val body = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val request = Request.Builder()
            .url(CLAUDE_MESSAGES_API_URL)
            .addHeader(HEADER_ANTHROPIC_API_KEY, apiKey)
            .addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            .addHeader(HEADER_CONTENT_TYPE_LOWER, JSON_MEDIA_TYPE)
            .post(body)
            .build()

        val responseBody = executeCancellableJson(request, "Empty response from Anthropic server")
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val text = parsed[JSON_CONTENT_KEY]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull == JSON_TEXT_KEY }
            .mapNotNull { it[JSON_TEXT_KEY]?.jsonPrimitive?.contentOrNull }
            .joinToString(separator = "")
            .takeIf { it.isNotEmpty() }

        return text ?: "Received empty content block from Claude."
    }
'''
new_call = '''    private suspend fun callClaudeApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>
    ): ClaudeGenerationResult {
        val maxTokens = resolveClaudeMaxTokens(model, apiKey)
        val reasoningCapabilities = resolveClaudeReasoningCapabilities(model, apiKey)
        val messagesArray = buildClaudeMessages(prompt, conversationHistory, systemInstruction, model)
        val requestPayload = buildClaudeRequestPayload(
            model, maxTokens, stream = false, systemInstruction, messagesArray, reasoningCapabilities
        )

        val body = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val request = Request.Builder()
            .url(CLAUDE_MESSAGES_API_URL)
            .addHeader(HEADER_ANTHROPIC_API_KEY, apiKey)
            .addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            .addHeader(HEADER_CONTENT_TYPE_LOWER, JSON_MEDIA_TYPE)
            .post(body)
            .build()

        val responseBody = executeCancellableJson(request, "Empty response from Anthropic server")
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        return ClaudeGenerationResult(
            text = extractClaudeResponseText(parsed) ?: "Received empty content block from Claude.",
            replayState = extractClaudeReplayState(parsed)
        )
    }

    private fun isValidClaudeReplayBlock(block: JsonObject): Boolean =
        (block[STREAM_TYPE_KEY] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.isNotBlank() == true

    internal fun parseClaudeReplayState(state: String?): List<JsonObject> {
        if (state.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(state) as? JsonArray
                ?: return@runCatching emptyList()
            val blocks = array.mapNotNull { it as? JsonObject }
            if (blocks.size != array.size || blocks.isEmpty() || blocks.any { !isValidClaudeReplayBlock(it) }) {
                emptyList()
            } else {
                blocks
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeClaudeReplayState(blocks: List<JsonObject>): String? =
        blocks.takeIf { it.isNotEmpty() && it.all(::isValidClaudeReplayBlock) }
            ?.let { JsonArray(it).toString() }

    internal fun extractClaudeReplayState(response: JsonObject): String? {
        val content = response[JSON_CONTENT_KEY] as? JsonArray ?: return null
        val blocks = content.mapNotNull { it as? JsonObject }
        if (blocks.size != content.size) return null
        return encodeClaudeReplayState(blocks)
    }

    internal fun extractClaudeResponseText(response: JsonObject): String? =
        (response[JSON_CONTENT_KEY] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull == JSON_TEXT_KEY }
            .mapNotNull { it[JSON_TEXT_KEY]?.jsonPrimitive?.contentOrNull }
            .joinToString(separator = "")
            .takeIf { it.isNotEmpty() }

    internal fun applyClaudeReplayEvent(
        blocks: MutableMap<Int, JsonObject>,
        event: JsonObject
    ) {
        val index = event[JSON_INDEX_KEY]?.jsonPrimitive?.intOrNull ?: return
        when (event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull) {
            CLAUDE_CONTENT_BLOCK_START -> {
                val block = event[JSON_CONTENT_BLOCK_KEY] as? JsonObject ?: return
                if (isValidClaudeReplayBlock(block)) blocks[index] = block
            }
            CLAUDE_CONTENT_BLOCK_DELTA -> {
                val delta = event[JSON_DELTA_KEY] as? JsonObject ?: return
                val field = when (delta[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull) {
                    CLAUDE_TEXT_DELTA -> JSON_TEXT_KEY
                    CLAUDE_THINKING_DELTA -> JSON_THINKING_KEY
                    CLAUDE_SIGNATURE_DELTA -> JSON_SIGNATURE_KEY
                    else -> return
                }
                val fragment = delta[field]?.jsonPrimitive?.contentOrNull ?: return
                val block = blocks[index] ?: return
                val existing = block[field]?.jsonPrimitive?.contentOrNull.orEmpty()
                blocks[index] = JsonObject(block + (field to JsonPrimitive(existing + fragment)))
            }
        }
    }

    internal fun encodeClaudeStreamReplayState(blocks: Map<Int, JsonObject>): String? =
        encodeClaudeReplayState(blocks.toSortedMap().values.toList())
'''
service = replace_once(service, old_call, new_call, "Claude buffered call and replay")

old_stream = '''    private suspend fun callClaudeStreamApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>,
        onTextDelta: (String) -> Unit
    ): String {
        val maxTokens = resolveClaudeMaxTokens(model, apiKey)
        val requestPayload = buildClaudeRequestPayload(
            model, maxTokens, stream = true, systemInstruction,
            buildClaudeMessages(prompt, conversationHistory, systemInstruction)
        )
        val request = Request.Builder()
            .url(CLAUDE_MESSAGES_API_URL)
            .addHeader(HEADER_ANTHROPIC_API_KEY, apiKey)
            .addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            .addHeader(HEADER_CONTENT_TYPE_LOWER, JSON_MEDIA_TYPE)
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return executeSse(request, ::extractClaudeStreamText, ::isClaudeStreamComplete, onTextDelta)
            .ifEmpty { "Received empty content block from Claude." }
    }
'''
new_stream = '''    private suspend fun callClaudeStreamApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>,
        onTextDelta: (String) -> Unit
    ): ClaudeGenerationResult {
        val maxTokens = resolveClaudeMaxTokens(model, apiKey)
        val reasoningCapabilities = resolveClaudeReasoningCapabilities(model, apiKey)
        val requestPayload = buildClaudeRequestPayload(
            model, maxTokens, stream = true, systemInstruction,
            buildClaudeMessages(prompt, conversationHistory, systemInstruction, model),
            reasoningCapabilities
        )
        val request = Request.Builder()
            .url(CLAUDE_MESSAGES_API_URL)
            .addHeader(HEADER_ANTHROPIC_API_KEY, apiKey)
            .addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            .addHeader(HEADER_CONTENT_TYPE_LOWER, JSON_MEDIA_TYPE)
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        val replayBlocks = mutableMapOf<Int, JsonObject>()
        val text = executeSse(
            request,
            ::extractClaudeStreamText,
            ::isClaudeStreamComplete,
            onTextDelta,
            onEvent = { event -> applyClaudeReplayEvent(replayBlocks, event) }
        )
        return ClaudeGenerationResult(
            text = text.ifEmpty { "Received empty content block from Claude." },
            replayState = encodeClaudeStreamReplayState(replayBlocks)
        )
    }
'''
service = replace_once(service, old_stream, new_stream, "Claude streaming call")
service_path.write_text(service)


test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test = replace_once(
    test,
    'import kotlinx.serialization.json.Json\n',
    'import kotlinx.serialization.json.Json\nimport kotlinx.serialization.json.JsonObject\n',
    "test JsonObject import",
)
test = replace_once(
    test,
    'private const val TEST_CLAUDE_OUTAGE_MODEL = "claude-outage"\n',
    'private const val TEST_CLAUDE_OUTAGE_MODEL = "claude-outage"\n'
    'private const val TEST_CLAUDE_MODEL = "claude-sonnet-5"\n'
    'private const val TEST_CLAUDE_LEGACY_MODEL = "claude-haiku-4-5-20251001"\n'
    'private const val TEST_CLAUDE_SIGNATURE = "claude-signature"\n'
    'private const val TEST_CLAUDE_REDACTED_DATA = "redacted-data"\n',
    "Claude test constants",
)
helper_anchor = 'private fun openAiReplayStateJson(): String = """\n'
claude_helper = '''private fun claudeReplayStateJson(): String = """
    [
      {"type":"thinking","thinking":"reasoning summary","signature":"$TEST_CLAUDE_SIGNATURE"},
      {"type":"redacted_thinking","data":"$TEST_CLAUDE_REDACTED_DATA"},
      {"type":"text","text":"$CLAUDE_ANSWER"}
    ]
""".trimIndent()

'''
assert test.count(helper_anchor) == 1
test = test.replace(helper_anchor, claude_helper + helper_anchor, 1)

test = test.replace('"claude-sonnet-5"', 'TEST_CLAUDE_MODEL)
test = test.replace('"claude-haiku-4-5-20251001"', 'TEST_CLAUDE_LEGACY_MODEL)
# Restore literals inside string fixtures where Kotlin interpolation is not desired.
test = test.replace('"""{"id":TEST_CLAUDE_MODEL}"""', '"""{"id":"claude-sonnet-5"}"""')

insert_anchor = '''    @Test
    fun compatibleHistoryKeepsOnlyCurrentProviderAssistantTurns() {
'''
new_tests = '''    @Test
    fun parsesClaudeReasoningCapabilitiesFromModelMetadata() {
        val raw = """
            {
              "max_tokens": 128000,
              "capabilities": {
                "thinking": {
                  "supported": true,
                  "types": {
                    "adaptive": {"supported": true},
                    "enabled": {"supported": false}
                  }
                },
                "effort": {
                  "supported": true,
                  "high": {"supported": true}
                }
              }
            }
        """.trimIndent()

        val capabilities = AiChatService().parseClaudeReasoningCapabilities(raw)

        assertEquals(
            ClaudeReasoningCapabilities(
                supportsAdaptive = true,
                supportsHighEffort = true
            ),
            capabilities
        )
    }

    @Test
    fun claudePayloadUsesModelAwareAdaptiveAndLegacyThinking() {
        val service = AiChatService()
        val messages = Json.parseToJsonElement("""[{"role":"user","content":"hello"}]""").jsonArray
        val adaptive = service.buildClaudeRequestPayload(
            TEST_CLAUDE_MODEL,
            TEST_CLAUDE_MAX_TOKENS,
            false,
            null,
            messages,
            ClaudeReasoningCapabilities(supportsAdaptive = true, supportsHighEffort = true)
        )
        val legacy = service.buildClaudeRequestPayload(
            TEST_CLAUDE_LEGACY_MODEL,
            64_000,
            false,
            null,
            messages,
            ClaudeReasoningCapabilities(supportsEnabled = true)
        )

        assertEquals("adaptive", adaptive.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("high", adaptive.getValue("output_config").jsonObject.getValue("effort").jsonPrimitive.content)
        assertEquals("enabled", legacy.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("4096", legacy.getValue("thinking").jsonObject.getValue("budget_tokens").jsonPrimitive.content)
    }

    @Test
    fun claudeHistoryReplaysOpaqueThinkingBlocksOnlyForMatchingModel() {
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = FIRST_QUESTION),
            ModelChatMessage(
                id = "claude",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.CLAUDE,
                modelName = TEST_CLAUDE_MODEL,
                text = CLAUDE_ANSWER,
                providerReplayState = claudeReplayStateJson()
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = FOLLOW_UP)
        )
        val service = AiChatService()

        val replayed = service.buildClaudeMessages(FOLLOW_UP, history, modelName = TEST_CLAUDE_MODEL)
        val blocks = replayed[1].jsonObject.getValue(TEST_CONTENT_KEY).jsonArray
        assertEquals(listOf("thinking", "redacted_thinking", "text"), blocks.map {
            it.jsonObject.getValue("type").jsonPrimitive.content
        })
        assertEquals(
            TEST_CLAUDE_SIGNATURE,
            blocks[0].jsonObject.getValue("signature").jsonPrimitive.content
        )
        assertEquals(
            TEST_CLAUDE_REDACTED_DATA,
            blocks[1].jsonObject.getValue("data").jsonPrimitive.content
        )

        val switched = service.buildClaudeMessages(FOLLOW_UP, history, modelName = "claude-opus-5")
        assertEquals(
            CLAUDE_ANSWER,
            switched[1].jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content
        )
    }

    @Test
    fun claudeStreamingReconstructsThinkingSignatureRedactionAndTextInOrder() {
        val service = AiChatService()
        val blocks = mutableMapOf<Int, JsonObject>()
        listOf(
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"reasoning summary"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"$TEST_CLAUDE_SIGNATURE"}}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"redacted_thinking","data":"$TEST_CLAUDE_REDACTED_DATA"}}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"$CLAUDE_ANSWER"}}"""
        ).forEach { raw ->
            service.applyClaudeReplayEvent(blocks, Json.parseToJsonElement(raw).jsonObject)
        }

        val replay = service.parseClaudeReplayState(service.encodeClaudeStreamReplayState(blocks))
        assertEquals(listOf("thinking", "redacted_thinking", "text"), replay.map {
            it.getValue("type").jsonPrimitive.content
        })
        assertEquals(TEST_CLAUDE_SIGNATURE, replay[0].getValue("signature").jsonPrimitive.content)
        assertEquals(TEST_CLAUDE_REDACTED_DATA, replay[1].getValue("data").jsonPrimitive.content)
        assertEquals(CLAUDE_ANSWER, replay[2].getValue("text").jsonPrimitive.content)
    }

'''
test = replace_once(test, insert_anchor, new_tests + insert_anchor, "Claude tests")
test_path.write_text(test)


caps_path = Path("shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilities.kt")
caps = caps_path.read_text()
caps = replace_once(
    caps,
    '''enum class ConversationStateStrategy {
    PROVIDER_FAN_OUT,
    BOUNDED_PROVIDER_TEXT_REPLAY,
    BOUNDED_PROVIDER_CONTENT_REPLAY
}
''',
    '''enum class ConversationStateStrategy {
    PROVIDER_FAN_OUT,
    BOUNDED_PROVIDER_TEXT_REPLAY,
    BOUNDED_PROVIDER_CONTENT_REPLAY
}

enum class ReasoningControlStrategy {
    PER_PROVIDER,
    PROVIDER_DEFAULT,
    MODEL_CAPABILITY_METADATA
}

fun AiProvider.reasoningControlStrategy(): ReasoningControlStrategy = when (this) {
    AiProvider.ALL -> ReasoningControlStrategy.PER_PROVIDER
    AiProvider.CLAUDE -> ReasoningControlStrategy.MODEL_CAPABILITY_METADATA
    else -> ReasoningControlStrategy.PROVIDER_DEFAULT
}
''',
    "reasoning strategy capability",
)
caps = replace_once(
    caps,
    '''    AiProvider.CLAUDE -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.ANTHROPIC_MESSAGES,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_TEXT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )
''',
    '''    AiProvider.CLAUDE -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.ANTHROPIC_MESSAGES,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )
''',
    "Claude state strategy",
)
caps_path.write_text(caps)


caps_test_path = Path("shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilitiesTest.kt")
caps_test = caps_test_path.read_text()
anchor = '''    @Test
    fun compareModeIsFanOutRatherThanAProviderTransport() {
'''
addition = '''    @Test
    fun claudeUsesModelCapabilityMetadataAndOpaqueContentReplay() {
        assertEquals(
            ReasoningControlStrategy.MODEL_CAPABILITY_METADATA,
            AiProvider.CLAUDE.reasoningControlStrategy()
        )
        assertEquals(
            ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
            AiProvider.CLAUDE.runtimeCapabilities().conversationStateStrategy
        )
    }

'''
caps_test = replace_once(caps_test, anchor, addition + anchor, "capability test")
caps_test_path.write_text(caps_test)


studio_path = Path("app/src/main/java/com/twojstar/llmbench/ui/screens/StudioScreen.kt")
studio = studio_path.read_text()
studio = replace_once(
    studio,
    'import com.twojstar.llmbench.data.model.ProfileOverlay\n',
    'import com.twojstar.llmbench.data.model.ProfileOverlay\nimport com.twojstar.llmbench.data.model.ReasoningControlStrategy\n',
    "Studio reasoning import",
)
studio = replace_once(
    studio,
    'import com.twojstar.llmbench.data.model.runtimeCapabilities\n',
    'import com.twojstar.llmbench.data.model.reasoningControlStrategy\nimport com.twojstar.llmbench.data.model.runtimeCapabilities\n',
    "Studio reasoning function import",
)
state_anchor = '''    val resolvedModelLabel = when {
        provider == AiProvider.ALL -> "Captured per gateway when returned"
        capabilities.reportsResolvedModel -> "Gateway response metadata when returned"
        else -> "Selected/requested model"
    }
'''
state_addition = state_anchor + '''    val reasoningLabel = when (provider.reasoningControlStrategy()) {
        ReasoningControlStrategy.PER_PROVIDER -> "Per-provider model capabilities"
        ReasoningControlStrategy.PROVIDER_DEFAULT -> "Provider default"
        ReasoningControlStrategy.MODEL_CAPABILITY_METADATA -> "Anthropic Models API capabilities"
    }
'''
studio = replace_once(studio, state_anchor, state_addition, "Studio reasoning label")
studio = replace_once(
    studio,
    '            PromptRouteRow("History", stateLabel)\n            PromptRouteRow("Streaming", if (capabilities.streamsText) "Incremental SSE" else "Buffered response")\n',
    '            PromptRouteRow("History", stateLabel)\n            PromptRouteRow("Reasoning", reasoningLabel)\n            PromptRouteRow("Streaming", if (capabilities.streamsText) "Incremental SSE" else "Buffered response")\n',
    "Studio reasoning row",
)
studio_path.write_text(studio)


docs_path = Path("docs/provider-runtime.md")
docs = docs_path.read_text()
docs = replace_once(
    docs,
    '| Claude | `Messages API` | top-level `system` | SSE | bounded visible text replay |\n',
    '| Claude | `Messages API` | top-level `system` | SSE | bounded provider content replay with opaque thinking blocks |\n',
    "Claude runtime table",
)
old_claude_docs = '''### Claude

The current Claude path does not enable or retain thinking blocks. If adaptive/extended thinking controls are added, preserve provider-returned `thinking` and `redacted_thinking` blocks exactly where the API requires them, especially around tool-use turns. Do not flatten them into visible text history.

Thinking configuration is model-family-specific. Prefer capability metadata over model-name conditionals scattered through UI code.
'''
new_claude_docs = '''### Claude

Claude reasoning is model-aware through the Anthropic Models API. LlmBench reads `capabilities.thinking.types` and effort support from the same short model-metadata lookup already used for `max_tokens`: adaptive-capable models receive `thinking: {type: "adaptive"}` and high effort when the model reports it, while legacy extended-thinking models receive a conservative budget only when `budget_tokens < max_tokens` can be satisfied. If metadata is unavailable, one centralized family fallback covers the known Claude 4.5-5 lines instead of scattering model-name checks through UI or transport code.

When thinking is active, buffered responses retain the complete ordered assistant `content` array as ephemeral provider replay state. Streaming responses reconstruct the provider blocks from `content_block_start` plus `thinking_delta`, `signature_delta`, and `text_delta` events. `thinking`, `signature`, and `redacted_thinking` data are never rendered, exported, logged, or rewritten; they are replayed only for the exact model that produced them. A model switch or malformed state falls back to visible assistant text.

The current native path does not expose client tools, so streaming replay only mutates the block fields used by text/thinking/signature deltas. Provider-returned block starts, including opaque redacted-thinking blocks, remain otherwise untouched.
'''
docs = replace_once(docs, old_claude_docs, new_claude_docs, "Claude docs")
docs = replace_once(
    docs,
    '- [ ] Add Claude thinking/effort only through model-aware capabilities; preserve opaque thinking blocks when enabled.\n',
    '- [x] Add Claude thinking/effort only through model-aware capabilities; preserve opaque thinking blocks when enabled.\n',
    "Claude TODO",
)
docs_path.write_text(docs)
