from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    assert count == 1, f"{path}: expected exactly one match, found {count}"
    target.write_text(text.replace(old, new, 1))


history = "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ConversationHistory.kt"
replace(
    history,
    '''data class ProviderTextTurn(
    val role: String,
    val text: String,
    val providerReplayState: String? = null
)
''',
    '''data class ProviderTextTurn(
    val role: String,
    val text: String,
    val providerReplayState: String? = null,
    val modelName: String? = null
)
''',
)
replace(
    history,
    '''                segments.last() += ProviderTextTurn(
                    CHAT_ROLE_ASSISTANT,
                    message.text,
                    message.providerReplayState
                )
''',
    '''                segments.last() += ProviderTextTurn(
                    CHAT_ROLE_ASSISTANT,
                    message.text,
                    message.providerReplayState,
                    message.modelName
                )
''',
)

service = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace(
    service,
    '''    internal fun buildGeminiContents(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        systemInstruction: String? = null
    ): JsonArray = buildJsonArray {
''',
    '''    internal fun buildGeminiContents(
        prompt: String,
        conversationHistory: List<ModelChatMessage>,
        modelName: String,
        systemInstruction: String? = null
    ): JsonArray = buildJsonArray {
''',
)
replace(
    service,
    '''            if (turn.role == CHAT_ROLE_ASSISTANT) {
                val replayContents = parseGeminiReplayState(turn.providerReplayState)
''',
    '''            if (turn.role == CHAT_ROLE_ASSISTANT) {
                val replayContents = turn.providerReplayState
                    .takeIf { turn.modelName == modelName }
                    ?.let(::parseGeminiReplayState)
                    .orEmpty()
''',
)
replace(
    service,
    '''        val contentsArray = buildGeminiContents(prompt, conversationHistory, systemInstruction)
''',
    '''        val contentsArray = buildGeminiContents(prompt, conversationHistory, model, systemInstruction)
''',
)
replace(
    service,
    '''            put("contents", buildGeminiContents(prompt, conversationHistory, systemInstruction))
''',
    '''            put("contents", buildGeminiContents(prompt, conversationHistory, model, systemInstruction))
''',
)

tests = "app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt"
replace(
    tests,
    '''        val gemini = service.buildGeminiContents(prompt, history)
''',
    '''        val gemini = service.buildGeminiContents(prompt, history, "gemini-test-model")
''',
)
replace(
    tests,
    '''                provider = AiProvider.GEMINI,
                text = GEMINI_ANSWER,
                providerReplayState = replayState
''',
    '''                provider = AiProvider.GEMINI,
                modelName = "gemini-test-model",
                text = GEMINI_ANSWER,
                providerReplayState = replayState
''',
)
replace(
    tests,
    '''        val contents = AiChatService().buildGeminiContents(FOLLOW_UP, history)
''',
    '''        val contents = AiChatService().buildGeminiContents(FOLLOW_UP, history, "gemini-test-model")
''',
)
replace(
    tests,
    '''    @Test
    fun geminiStreamingKeepsEmptyTextSignatureCarrier() {
''',
    '''    @Test
    fun geminiModelSwitchFallsBackToVisibleTextReplay() {
        val replayState = "[{\\\"role\\\":\\\"model\\\",\\\"parts\\\":[{\\\"text\\\":\\\"gemini answer\\\"},{\\\"text\\\":\\\"\\\",\\\"thoughtSignature\\\":\\\"opaque-signature\\\"}]}]"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = FIRST_QUESTION),
            ModelChatMessage(
                id = "gemini",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.GEMINI,
                modelName = "gemini-old-model",
                text = GEMINI_ANSWER,
                providerReplayState = replayState
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = FOLLOW_UP)
        )

        val contents = AiChatService().buildGeminiContents(FOLLOW_UP, history, "gemini-new-model")

        assertEquals(listOf(CHAT_ROLE_USER, "model", CHAT_ROLE_USER), contents.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        val replayParts = contents[1].jsonObject.getValue("parts").jsonArray
        assertEquals(1, replayParts.size)
        val visiblePart = replayParts.single().jsonObject
        assertEquals(GEMINI_ANSWER, visiblePart.getValue("text").jsonPrimitive.content)
        assertFalse("thoughtSignature" in visiblePart)
    }

    @Test
    fun geminiStreamingKeepsEmptyTextSignatureCarrier() {
''',
)

print("Gemini model-scope patch applied")
