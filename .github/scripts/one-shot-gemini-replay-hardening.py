from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    assert count == 1, f"{path}: expected exactly one match, found {count}"
    target.write_text(text.replace(old, new, 1))


chat_models = "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ChatModels.kt"
replace(
    chat_models,
    "import kotlinx.serialization.Serializable\n",
    "import kotlinx.serialization.Serializable\nimport kotlinx.serialization.Transient\n",
)
replace(
    chat_models,
    "    val activeProfileNotes: List<String> = emptyList(),\n    val providerReplayState: String? = null\n",
    "    val activeProfileNotes: List<String> = emptyList(),\n    @Transient val providerReplayState: String? = null\n",
)

history = "shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ConversationHistory.kt"
replace(
    history,
    "    provider: AiProvider,\n    systemInstruction: String? = null,\n    maxHistoryCharacters: Int = DEFAULT_HISTORY_CHARACTER_BUDGET,\n",
    "    provider: AiProvider,\n    systemInstruction: String? = null,\n    replayStateModelName: String? = null,\n    maxHistoryCharacters: Int = DEFAULT_HISTORY_CHARACTER_BUDGET,\n",
)
replace(
    history,
    "                    message.text,\n                    message.providerReplayState,\n                    message.modelName\n",
    "                    message.text,\n                    message.providerReplayState.takeIf {\n                        replayStateModelName == null || message.modelName == replayStateModelName\n                    },\n                    message.modelName\n",
)

service = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace(
    service,
    """        buildBoundedProviderTextTurns(
            prompt, conversationHistory, AiProvider.GEMINI, systemInstruction
        ).forEach { turn ->
""",
    """        buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = conversationHistory,
            provider = AiProvider.GEMINI,
            systemInstruction = systemInstruction,
            replayStateModelName = modelName
        ).forEach { turn ->
""",
)

shared_tests = "shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ConversationHistoryTest.kt"
replace(
    shared_tests,
    "import kotlin.test.assertTrue\n",
    "import kotlin.test.assertTrue\nimport kotlinx.serialization.encodeToString\nimport kotlinx.serialization.json.Json\n",
)
replace(
    shared_tests,
    """    @Test
    fun incompleteOrNonResponseMessagesAreNotCompletedAssistantResponses() {
""",
    """    @Test
    fun providerReplayStateIsTransientInSerialization() {
        val encoded = Json.encodeToString(
            ModelChatMessage(
                id = "serialized",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.GEMINI,
                modelName = "gemini-test",
                text = "visible",
                providerReplayState = "opaque-provider-state"
            )
        )

        assertFalse("providerReplayState" in encoded)
        assertFalse("opaque-provider-state" in encoded)
    }

    @Test
    fun incompleteOrNonResponseMessagesAreNotCompletedAssistantResponses() {
""",
)
replace(
    shared_tests,
    """    @Test
    fun partialAssistantResponsesAreNotReplayed() {
""",
    """    @Test
    fun crossModelReplayStateDoesNotConsumeVisibleFallbackBudget() {
        val prompt = "next"
        val previous = "previous"
        val answer = "answer"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = previous),
            ModelChatMessage(
                id = "a1",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.GEMINI,
                modelName = "gemini-old",
                text = answer,
                providerReplayState = "x".repeat(4_096)
            )
        )
        val visibleSegmentCost = previous.length + answer.length + (2 * 32)

        val turns = buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = history,
            provider = AiProvider.GEMINI,
            replayStateModelName = "gemini-new",
            maxHistoryCharacters = prompt.length + visibleSegmentCost,
            maxHistoryTurns = 8
        )

        assertEquals(listOf(previous, answer, prompt), turns.map { it.text })
        assertEquals(null, turns[1].providerReplayState)
    }

    @Test
    fun partialAssistantResponsesAreNotReplayed() {
""",
)

app_tests = "app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt"
replace(
    app_tests,
    "private const val TEST_OPAQUE_SIGNATURE = \"opaque-signature\"\n",
    "private const val TEST_OPAQUE_SIGNATURE = \"opaque-signature\"\nprivate const val TEST_THOUGHT_SIGNATURE_KEY = \"thoughtSignature\"\n",
)
app_text = Path(app_tests).read_text()
app_text = app_text.replace('getValue("thoughtSignature")', 'getValue(TEST_THOUGHT_SIGNATURE_KEY)')
app_text = app_text.replace('assertFalse("thoughtSignature" in visiblePart)', 'assertFalse(TEST_THOUGHT_SIGNATURE_KEY in visiblePart)')
Path(app_tests).write_text(app_text)

docs = "docs/provider-runtime.md"
replace(
    docs,
    "- keep provider-owned replay state opaque, provider-scoped, budgeted and out of UI/export/log output.\n",
    "- keep provider-owned replay state opaque, provider/model-scoped, budgeted only when replayable, and out of serialization/UI/export/log output.\n",
)
replace(
    docs,
    "`providerReplayState` is opaque transport state: it is never rendered as chat text, exported to Markdown, logged, or rewritten. Invalid/legacy replay state falls back to the existing visible-text reconstruction instead of making the chat unusable. Streaming capture runs through the terminal `STOP` event so signature-only final chunks are not dropped.\n",
    "`providerReplayState` is ephemeral opaque transport state: it is excluded from `ModelChatMessage` serialization and is never rendered as chat text, exported to Markdown, logged, or rewritten. It is replayed and charged against the history budget only for the exact model that produced it; model switches and invalid/legacy state fall back to the existing visible-text reconstruction instead of making the chat unusable. Streaming capture runs through the terminal `STOP` event so signature-only final chunks are not dropped.\n",
)

print("Gemini replay hardening patch applied")
