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
    """    systemInstruction: String? = null,
    replayStateModelName: String? = null,
    maxHistoryCharacters: Int = DEFAULT_HISTORY_CHARACTER_BUDGET,
""",
    """    systemInstruction: String? = null,
    replayStateModelName: String? = null,
    replayStateValidator: ((String) -> Boolean)? = null,
    maxHistoryCharacters: Int = DEFAULT_HISTORY_CHARACTER_BUDGET,
""",
)
replace(
    history,
    """                    message.providerReplayState.takeIf {
                        replayStateModelName == null || message.modelName == replayStateModelName
                    },
""",
    """                    message.providerReplayState
                        .takeIf { replayStateModelName == null || message.modelName == replayStateModelName }
                        ?.takeIf { state -> replayStateValidator?.invoke(state) != false },
""",
)

service = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace(
    service,
    """            provider = AiProvider.GEMINI,
            systemInstruction = systemInstruction,
            replayStateModelName = modelName
        ).forEach { turn ->
""",
    """            provider = AiProvider.GEMINI,
            systemInstruction = systemInstruction,
            replayStateModelName = modelName,
            replayStateValidator = { state -> parseGeminiReplayState(state).isNotEmpty() }
        ).forEach { turn ->
""",
)

shared_tests = "shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ConversationHistoryTest.kt"
replace(
    shared_tests,
    """    @Test
    fun partialAssistantResponsesAreNotReplayed() {
""",
    """    @Test
    fun invalidSameModelReplayStateDoesNotConsumeVisibleFallbackBudget() {
        val prompt = "next"
        val previous = "previous"
        val answer = "answer"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = previous),
            ModelChatMessage(
                id = "a1",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.GEMINI,
                modelName = "gemini-current",
                text = answer,
                providerReplayState = "broken".repeat(1_024)
            )
        )
        val visibleSegmentCost = previous.length + answer.length + (2 * 32)

        val turns = buildBoundedProviderTextTurns(
            prompt = prompt,
            conversationHistory = history,
            provider = AiProvider.GEMINI,
            replayStateModelName = "gemini-current",
            replayStateValidator = { false },
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

print("Invalid Gemini replay budgeting patch applied")
