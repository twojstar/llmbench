from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    target = Path(path)
    text = target.read_text()
    actual = text.count(old)
    assert actual == count, f"{path}: expected {count} matches, found {actual}"
    target.write_text(text.replace(old, new, count))


replace(
    "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ChatModels.kt",
    """    val latencyMs: Long? = null,
    val activeProfileNotes: List<String> = emptyList()
)""",
    """    val latencyMs: Long? = null,
    val activeProfileNotes: List<String> = emptyList(),
    val providerReplayState: String? = null
)""",
)

history = "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ConversationHistory.kt"
replace(
    history,
    """data class ProviderTextTurn(
    val role: String,
    val text: String
)""",
    """data class ProviderTextTurn(
    val role: String,
    val text: String,
    val providerReplayState: String? = null
)""",
)
replace(
    history,
    "                segments.last() += ProviderTextTurn(CHAT_ROLE_ASSISTANT, message.text)",
    """                segments.last() += ProviderTextTurn(
                    CHAT_ROLE_ASSISTANT,
                    message.text,
                    message.providerReplayState
                )""",
)
replace(
    history,
    "        val segmentCost = segment.sumOf { it.text.length + MESSAGE_OVERHEAD_CHARACTERS }",
    """        val segmentCost = segment.sumOf {
            maxOf(it.text.length, it.providerReplayState?.length ?: 0) + MESSAGE_OVERHEAD_CHARACTERS
        }""",
)

capabilities = "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilities.kt"
replace(
    capabilities,
    """enum class ConversationStateStrategy {
    PROVIDER_FAN_OUT,
    BOUNDED_PROVIDER_TEXT_REPLAY
}""",
    """enum class ConversationStateStrategy {
    PROVIDER_FAN_OUT,
    BOUNDED_PROVIDER_TEXT_REPLAY,
    BOUNDED_PROVIDER_CONTENT_REPLAY
}""",
)
replace(
    capabilities,
    """    AiProvider.GEMINI -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.GEMINI_GENERATE_CONTENT,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_TEXT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )""",
    """    AiProvider.GEMINI -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.GEMINI_GENERATE_CONTENT,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )""",
)

replace(
    "app/src/main/java/com/twojstar/llmbench/ui/screens/StudioScreen.kt",
    """    val stateLabel = when (capabilities.conversationStateStrategy) {
        ConversationStateStrategy.PROVIDER_FAN_OUT -> "Isolated history per provider in compare fan-out"
        ConversationStateStrategy.BOUNDED_PROVIDER_TEXT_REPLAY -> "Bounded provider-scoped text replay"
    }""",
    """    val stateLabel = when (capabilities.conversationStateStrategy) {
        ConversationStateStrategy.PROVIDER_FAN_OUT -> "Isolated history per provider in compare fan-out"
        ConversationStateStrategy.BOUNDED_PROVIDER_TEXT_REPLAY -> "Bounded provider-scoped text replay"
        ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY ->
            "Bounded provider content replay with opaque model state"
    }""",
)

capability_tests = "shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilitiesTest.kt"
replace(
    capability_tests,
    """    @Test
    fun compareModeIsFanOutRatherThanAProviderTransport() {""",
    """    @Test
    fun geminiPreservesOpaqueProviderContentState() {
        assertEquals(
            ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
            AiProvider.GEMINI.runtimeCapabilities().conversationStateStrategy
        )
    }

    @Test
    fun compareModeIsFanOutRatherThanAProviderTransport() {""",
)

service = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace(
    service,
    """class AiChatService {

    private data class ClaudeMaxTokensCacheKey(""",
    """class AiChatService {

    private data class GeminiGenerationResult(
        val text: String,
        val replayState: String?
    )

    private data class ClaudeMaxTokensCacheKey(""",
)
replace(
    service,
    """        var resolvedModel = effectiveModel

        val (key, isKeyProvided) = when (provider) {""",
    """        var resolvedModel = effectiveModel
        var providerReplayState: String? = null

        val (key, isKeyProvided) = when (provider) {""",
)
replace(
    service,
    """                    AiProvider.GEMINI -> if (onTextDelta != null) {
                        callGeminiStreamApi(
                            prompt, effectiveModel, key, systemInstruction, conversationHistory, onTextDelta
                        )
                    } else {
                        callGeminiApi(prompt, effectiveModel, key, systemInstruction, conversationHistory)
                    }""",
    """                    AiProvider.GEMINI -> {
                        val result = if (onTextDelta != null) {
                            callGeminiStreamApi(
                                prompt, effectiveModel, key, systemInstruction, conversationHistory, onTextDelta
                            )
                        } else {
                            callGeminiApi(prompt, effectiveModel, key, systemInstruction, conversationHistory)
                        }
                        providerReplayState = result.replayState
                        result.text
                    }""",
)
replace(
    service,
    """                        isSimulated = false,
                        latencyMs = latency,
                        activeProfileNotes = activeNotes""",
    """                        isSimulated = false,
                        latencyMs = latency,
                        activeProfileNotes = activeNotes,
                        providerReplayState = providerReplayState""",
)

old_builder = """    internal fun buildGeminiContents(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        systemInstruction: String? = null
    ): JsonArray = buildJsonArray {
        buildBoundedProviderTextTurns(
            prompt, conversationHistory, AiProvider.GEMINI, systemInstruction
        ).forEach { turn ->
            addJsonObject {
                put(JSON_ROLE_KEY, if (turn.role == CHAT_ROLE_ASSISTANT) JSON_MODEL_KEY else CHAT_ROLE_USER)
                putJsonArray(JSON_PARTS_KEY) {
                    addJsonObject { put(JSON_TEXT_KEY, turn.text) }
                }
            }
        }
    }
"""
new_builder = """    internal fun buildGeminiContents(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        systemInstruction: String? = null
    ): JsonArray = buildJsonArray {
        buildBoundedProviderTextTurns(
            prompt, conversationHistory, AiProvider.GEMINI, systemInstruction
        ).forEach { turn ->
            if (turn.role == CHAT_ROLE_ASSISTANT) {
                val replayContents = parseGeminiReplayState(turn.providerReplayState)
                if (replayContents.isNotEmpty()) {
                    replayContents.forEach(::add)
                } else {
                    addJsonObject {
                        put(JSON_ROLE_KEY, JSON_MODEL_KEY)
                        putJsonArray(JSON_PARTS_KEY) {
                            addJsonObject { put(JSON_TEXT_KEY, turn.text) }
                        }
                    }
                }
            } else {
                addJsonObject {
                    put(JSON_ROLE_KEY, CHAT_ROLE_USER)
                    putJsonArray(JSON_PARTS_KEY) {
                        addJsonObject { put(JSON_TEXT_KEY, turn.text) }
                    }
                }
            }
        }
    }
"""
replace(service, old_builder, new_builder)

old_unary = """    // --- Google Gemini REST API ---
    private suspend fun callGeminiApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val contentsArray = buildGeminiContents(prompt, conversationHistory, systemInstruction)

        val requestPayload = buildJsonObject {
            put("contents", contentsArray)
            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray(JSON_PARTS_KEY) {
                        addJsonObject { put(JSON_TEXT_KEY, systemInstruction) }
                    }
                }
            }
        }

        val body = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val responseBody = executeCancellableJson(request, "Empty response from Gemini server")
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val text = parsed[JSON_CANDIDATES_KEY]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get(JSON_CONTENT_KEY)?.jsonObject
            ?.get(JSON_PARTS_KEY)?.jsonArray
            .orEmpty()
            .mapNotNull { it.jsonObject[JSON_TEXT_KEY]?.jsonPrimitive?.contentOrNull }
            .joinToString(separator = "")
            .takeIf { it.isNotEmpty() }

        return text ?: "Received empty content response from Gemini."
    }
"""
new_unary = """    // --- Google Gemini REST API ---
    private suspend fun callGeminiApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>
    ): GeminiGenerationResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val contentsArray = buildGeminiContents(prompt, conversationHistory, systemInstruction)

        val requestPayload = buildJsonObject {
            put("contents", contentsArray)
            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray(JSON_PARTS_KEY) {
                        addJsonObject { put(JSON_TEXT_KEY, systemInstruction) }
                    }
                }
            }
        }

        val body = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val responseBody = executeCancellableJson(request, "Empty response from Gemini server")
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val content = extractGeminiReplayContent(parsed)
        return GeminiGenerationResult(
            text = extractGeminiStreamText(parsed)
                ?: "Received empty content response from Gemini.",
            replayState = content?.let { encodeGeminiReplayState(listOf(it)) }
        )
    }
"""
replace(service, old_unary, new_unary)

replace(
    service,
    "    internal fun extractGeminiStreamText(event: JsonObject): String? =\n",
    """    private fun isValidGeminiReplayContent(content: JsonObject): Boolean {
        val role = (content[JSON_ROLE_KEY] as? JsonPrimitive)?.contentOrNull
        val parts = content[JSON_PARTS_KEY] as? JsonArray ?: return false
        return role == JSON_MODEL_KEY && parts.isNotEmpty() && parts.all { part ->
            part is JsonObject && part.isNotEmpty()
        }
    }

    internal fun extractGeminiReplayContent(event: JsonObject): JsonObject? {
        val candidate = (event[JSON_CANDIDATES_KEY] as? JsonArray)
            ?.firstOrNull() as? JsonObject ?: return null
        val content = candidate[JSON_CONTENT_KEY] as? JsonObject ?: return null
        return content.takeIf(::isValidGeminiReplayContent)
    }

    private fun encodeGeminiReplayState(contents: List<JsonObject>): String? =
        contents.takeIf { it.isNotEmpty() }?.let { JsonArray(it).toString() }

    private fun parseGeminiReplayState(state: String?): List<JsonObject> {
        if (state.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(state) as? JsonArray
                ?: return@runCatching emptyList()
            val contents = array.mapNotNull { it as? JsonObject }
            if (contents.size != array.size || contents.any { !isValidGeminiReplayContent(it) }) {
                emptyList()
            } else {
                contents
            }
        }.getOrDefault(emptyList())
    }

    internal fun extractGeminiStreamText(event: JsonObject): String? =
""",
)

old_stream = """    private suspend fun callGeminiStreamApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>,
        onTextDelta: (String) -> Unit
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val requestPayload = buildJsonObject {
            put("contents", buildGeminiContents(prompt, conversationHistory, systemInstruction))
            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray(JSON_PARTS_KEY) {
                        addJsonObject { put(JSON_TEXT_KEY, systemInstruction) }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return executeSse(request, ::extractGeminiStreamText, ::isGeminiStreamComplete, onTextDelta)
            .ifEmpty { "Received empty content response from Gemini." }
    }
"""
new_stream = """    private suspend fun callGeminiStreamApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?,
        conversationHistory: List<ModelChatMessage>,
        onTextDelta: (String) -> Unit
    ): GeminiGenerationResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val requestPayload = buildJsonObject {
            put("contents", buildGeminiContents(prompt, conversationHistory, systemInstruction))
            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray(JSON_PARTS_KEY) {
                        addJsonObject { put(JSON_TEXT_KEY, systemInstruction) }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        val replayContents = mutableListOf<JsonObject>()
        val text = executeSse(
            request = request,
            extractText = ::extractGeminiStreamText,
            isComplete = ::isGeminiStreamComplete,
            onTextDelta = onTextDelta,
            onEvent = { event ->
                extractGeminiReplayContent(event)?.let(replayContents::add)
            }
        )
        return GeminiGenerationResult(
            text = text.ifEmpty { "Received empty content response from Gemini." },
            replayState = encodeGeminiReplayState(replayContents)
        )
    }
"""
replace(service, old_stream, new_stream)

replace(
    service,
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean = false
    ): String = suspendCancellableCoroutine { continuation ->""",
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean = false,
        onEvent: ((JsonObject) -> Unit)? = null
    ): String = suspendCancellableCoroutine { continuation ->""",
)
replace(
    service,
    "                completeSseContinuation(continuation, response, extractText, isComplete, onTextDelta, completeOnDoneSentinel)",
    """                completeSseContinuation(
                    continuation, response, extractText, isComplete, onTextDelta,
                    completeOnDoneSentinel, onEvent
                )""",
)
replace(
    service,
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean
    ) {
        val result = runCatching {
            readSseResponse(response, extractText, isComplete, onTextDelta, completeOnDoneSentinel)
        }""",
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean,
        onEvent: ((JsonObject) -> Unit)?
    ) {
        val result = runCatching {
            readSseResponse(
                response, extractText, isComplete, onTextDelta,
                completeOnDoneSentinel, onEvent
            )
        }""",
)
replace(
    service,
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean = false
    ): String {
        response.use {
            ensureSuccessfulStreamingResponse(response)
            val source = response.body?.source() ?: throw IOException("Empty streaming response body")
            return consumeSseSource(source, extractText, isComplete, onTextDelta, completeOnDoneSentinel)
        }
    }""",
    """        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean = false,
        onEvent: ((JsonObject) -> Unit)? = null
    ): String {
        response.use {
            ensureSuccessfulStreamingResponse(response)
            val source = response.body?.source() ?: throw IOException("Empty streaming response body")
            return consumeSseSource(
                source, extractText, isComplete, onTextDelta,
                completeOnDoneSentinel, onEvent
            )
        }
    }""",
)
replace(
    service,
    """        isComplete: (JsonObject) -> Boolean,
        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean
    ): String {""",
    """        isComplete: (JsonObject) -> Boolean,
        onTextDelta: (String) -> Unit,
        completeOnDoneSentinel: Boolean,
        onEvent: ((JsonObject) -> Unit)?
    ): String {""",
)
replace(
    service,
    """            val event = parseSseEvent(payload) ?: return
            val (delta, eventComplete) = decodeSseEvent(event, extractText, isComplete)""",
    """            val event = parseSseEvent(payload) ?: return
            onEvent?.invoke(event)
            val (delta, eventComplete) = decodeSseEvent(event, extractText, isComplete)""",
)

tests = "app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt"
replace(
    tests,
    """    @Test
    fun extractsNativeStreamingTextDeltas() {""",
    """    @Test
    fun geminiHistoryReplaysOpaqueModelContentsIncludingSignatureOnlyChunk() {
        val replayState = """[{"role":"model","parts":[{"text":"gemini answer"}]},{"role":"model","parts":[{"text":"","thoughtSignature":"opaque-signature"}]}]"""
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = FIRST_QUESTION),
            ModelChatMessage(
                id = "gemini",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.GEMINI,
                text = GEMINI_ANSWER,
                providerReplayState = replayState
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = FOLLOW_UP)
        )

        val contents = AiChatService().buildGeminiContents(FOLLOW_UP, history)

        assertEquals(listOf(CHAT_ROLE_USER, "model", "model", CHAT_ROLE_USER), contents.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        val signaturePart = contents[2].jsonObject.getValue("parts").jsonArray.single().jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
    }

    @Test
    fun geminiStreamingKeepsEmptyTextSignatureCarrier() {
        val service = AiChatService()
        val replayContents = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val response = Response.Builder()
            .request(Request.Builder().url(TEST_STREAM_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body((
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"hello\"}]}}]}\n\n" +
                    "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"\",\"thoughtSignature\":\"opaque-signature\"}]},\"finishReason\":\"STOP\"}]}\n\n"
                ).toResponseBody(TEST_EVENT_STREAM_TYPE.toMediaType()))
            .build()

        val text = service.readSseResponse(
            response = response,
            extractText = service::extractGeminiStreamText,
            isComplete = service::isGeminiStreamComplete,
            onTextDelta = {},
            onEvent = { event ->
                service.extractGeminiReplayContent(event)?.let(replayContents::add)
            }
        )

        assertEquals(STREAM_HELLO, text)
        assertEquals(2, replayContents.size)
        val signaturePart = replayContents.last().getValue("parts").jsonArray.single().jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
    }

    @Test
    fun extractsNativeStreamingTextDeltas() {""",
)

docs = "docs/provider-runtime.md"
replace(
    docs,
    "| Gemini | `generateContent` REST | `systemInstruction` | SSE | bounded visible text replay |",
    "| Gemini | `generateContent` REST | `systemInstruction` | SSE | bounded provider content replay with opaque thought signatures |",
)
replace(
    docs,
    "- reserve history budget for the current prompt and system instruction.\n",
    "- reserve history budget for the current prompt and system instruction;\n- keep provider-owned replay state opaque, provider-scoped, budgeted and out of UI/export/log output.\n",
)
replace(
    docs,
    """The REST path currently reconstructs history from visible text only. Gemini thinking models can return `thoughtSignature` metadata that should be passed back unchanged in subsequent stateless turns. Dropping it can reduce reasoning continuity, and function-calling flows may reject requests without required signatures.

Potential directions:

- keep `generateContent` stateless but persist opaque response parts/signatures alongside visible text; or
- evaluate Gemini Interactions stateful mode, with an explicit privacy/storage decision before switching transports.

Do not expose or rewrite the signature content. Treat it as opaque provider state.""",
    """The REST path remains stateless, but LlmBench now retains the full model `Content` chunks returned by Gemini alongside visible text. Subsequent Gemini turns replay those model-owned chunks unchanged, including an empty-text final part when it carries `thoughtSignature`. This mirrors the GenerateContent SDK behavior while preserving the existing local/provider-scoped history boundary.

`providerReplayState` is opaque transport state: it is never rendered as chat text, exported to Markdown, logged, or rewritten. Invalid/legacy replay state falls back to the existing visible-text reconstruction instead of making the chat unusable. Streaming capture runs through the terminal `STOP` event so signature-only final chunks are not dropped.

A future move to Gemini Interactions can still be evaluated, but only with an explicit privacy/storage decision because that would change the current client-managed stateless model.""",
)
replace(
    docs,
    "- [ ] Preserve Gemini thought signatures or migrate that path to a stateful API with explicit storage semantics.",
    "- [x] Preserve Gemini thought signatures in stateless `generateContent` by replaying full model `Content` chunks unchanged.",
)

print("Gemini replay patch applied")
