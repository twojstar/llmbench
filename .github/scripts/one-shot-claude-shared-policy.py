from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected one anchor, found {count}"
    return text.replace(old, new, 1)


app_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
app = app_path.read_text()
app = replace_once(
    app,
    "import com.twojstar.llmbench.data.model.CHAT_ROLE_USER\n",
    "import com.twojstar.llmbench.data.model.CHAT_ROLE_USER\n"
    "import com.twojstar.llmbench.data.model.ClaudeReasoningCapabilities\n"
    "import com.twojstar.llmbench.data.model.fallbackClaudeReasoningCapabilities\n"
    "import com.twojstar.llmbench.data.model.parseClaudeReasoningCapabilities\n"
    "import com.twojstar.llmbench.data.model.resolveClaudeThinkingBudget\n",
    "Claude shared policy imports",
)
for line in (
    'private const val JSON_CAPABILITIES_KEY = "capabilities"\n',
    'private const val JSON_TYPES_KEY = "types"\n',
    'private const val JSON_SUPPORTED_KEY = "supported"\n',
    'private const val CLAUDE_MIN_THINKING_BUDGET = 1024\n',
    'private const val CLAUDE_DEFAULT_THINKING_BUDGET = 4096\n',
):
    app = replace_once(app, line, "", f"remove {line.strip()}")
app = replace_once(
    app,
    "internal data class ClaudeReasoningCapabilities(\n"
    "    val supportsAdaptive: Boolean = false,\n"
    "    val supportsEnabled: Boolean = false,\n"
    "    val supportsHighEffort: Boolean = false\n"
    ")\n\n",
    "",
    "remove Android Claude capability data class",
)
policy_start = app.index("    internal fun parseClaudeReasoningCapabilities(rawJson: String): ClaudeReasoningCapabilities?")
policy_end = app.index("    internal fun buildClaudeModelMetadataRequest(", policy_start)
app = app[:policy_start] + app[policy_end:]
app_path.write_text(app)


test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test = replace_once(
    test,
    "import com.twojstar.llmbench.data.model.CHAT_ROLE_USER\n",
    "import com.twojstar.llmbench.data.model.CHAT_ROLE_USER\n"
    "import com.twojstar.llmbench.data.model.ClaudeReasoningCapabilities\n",
    "Claude capability test import",
)
test_start = test.index("    @Test\n    fun parsesClaudeReasoningCapabilitiesFromModelMetadata()")
test_end = test.index("    @Test\n    fun claudePayloadUsesModelAwareAdaptiveAndLegacyThinking()", test_start)
test = test[:test_start] + test[test_end:]
test_path.write_text(test)


shared_path = Path("shared/src/commonMain/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilities.kt")
shared = shared_path.read_text()
shared = replace_once(
    shared,
    "package com.twojstar.llmbench.data.model\n\n",
    "package com.twojstar.llmbench.data.model\n\n"
    "import kotlinx.serialization.json.Json\n"
    "import kotlinx.serialization.json.JsonObject\n"
    "import kotlinx.serialization.json.JsonPrimitive\n"
    "import kotlinx.serialization.json.booleanOrNull\n"
    "import kotlinx.serialization.json.jsonObject\n"
    "import kotlinx.serialization.json.jsonPrimitive\n\n",
    "shared JSON imports",
)
policy = '''private const val CLAUDE_CAPABILITIES_KEY = "capabilities"
private const val CLAUDE_THINKING_KEY = "thinking"
private const val CLAUDE_TYPES_KEY = "types"
private const val CLAUDE_SUPPORTED_KEY = "supported"
private const val CLAUDE_ADAPTIVE_KEY = "adaptive"
private const val CLAUDE_ENABLED_KEY = "enabled"
private const val CLAUDE_EFFORT_KEY = "effort"
private const val CLAUDE_HIGH_EFFORT_KEY = "high"
private const val CLAUDE_MIN_THINKING_BUDGET = 1024
private const val CLAUDE_DEFAULT_THINKING_BUDGET = 4096

data class ClaudeReasoningCapabilities(
    val supportsAdaptive: Boolean = false,
    val supportsEnabled: Boolean = false,
    val supportsHighEffort: Boolean = false
)

fun parseClaudeReasoningCapabilities(rawJson: String): ClaudeReasoningCapabilities? = runCatching {
    val root = Json.parseToJsonElement(rawJson).jsonObject
    val capabilities = root[CLAUDE_CAPABILITIES_KEY] as? JsonObject ?: return@runCatching null
    val thinking = capabilities[CLAUDE_THINKING_KEY] as? JsonObject ?: return@runCatching null
    if (thinking[CLAUDE_SUPPORTED_KEY]?.jsonPrimitive?.booleanOrNull != true) {
        return@runCatching ClaudeReasoningCapabilities()
    }
    val types = thinking[CLAUDE_TYPES_KEY] as? JsonObject
    val effort = capabilities[CLAUDE_EFFORT_KEY] as? JsonObject
    ClaudeReasoningCapabilities(
        supportsAdaptive = types.supportsClaudeCapability(CLAUDE_ADAPTIVE_KEY),
        supportsEnabled = types.supportsClaudeCapability(CLAUDE_ENABLED_KEY),
        supportsHighEffort = effort.supportsClaudeCapability(CLAUDE_HIGH_EFFORT_KEY)
    )
}.getOrNull()

private fun JsonObject?.supportsClaudeCapability(name: String): Boolean =
    ((this?.get(name) as? JsonObject)?.get(CLAUDE_SUPPORTED_KEY) as? JsonPrimitive)
        ?.booleanOrNull == true

fun fallbackClaudeReasoningCapabilities(model: String): ClaudeReasoningCapabilities {
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

fun resolveClaudeThinkingBudget(maxTokens: Int): Int? {
    val budget = minOf(CLAUDE_DEFAULT_THINKING_BUDGET, maxTokens / 2)
    return budget.takeIf { it >= CLAUDE_MIN_THINKING_BUDGET && it < maxTokens }
}

'''
shared = replace_once(
    shared,
    "data class ProviderRuntimeCapabilities(\n",
    policy + "data class ProviderRuntimeCapabilities(\n",
    "shared Claude policy home",
)
shared_path.write_text(shared)


shared_test_path = Path("shared/src/commonTest/kotlin/com/twojstar/llmbench/data/model/ProviderRuntimeCapabilitiesTest.kt")
shared_test = shared_test_path.read_text()
new_tests = '''    @Test
    fun claudeReasoningMetadataParserUsesReportedThinkingAndEffortLevels() {
        val raw = """
            {
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

        assertEquals(
            ClaudeReasoningCapabilities(
                supportsAdaptive = true,
                supportsHighEffort = true
            ),
            parseClaudeReasoningCapabilities(raw)
        )
    }

    @Test
    fun claudeReasoningFallbackIsSharedProviderPolicy() {
        assertEquals(
            ClaudeReasoningCapabilities(supportsAdaptive = true, supportsHighEffort = true),
            fallbackClaudeReasoningCapabilities("claude-sonnet-5")
        )
        assertEquals(
            ClaudeReasoningCapabilities(supportsEnabled = true),
            fallbackClaudeReasoningCapabilities("claude-haiku-4-5-20251001")
        )
        assertEquals(ClaudeReasoningCapabilities(), fallbackClaudeReasoningCapabilities("claude-unknown"))
    }

    @Test
    fun claudeLegacyThinkingBudgetIsBoundedByOutputLimit() {
        assertEquals(4096, resolveClaudeThinkingBudget(64_000))
        assertEquals(1024, resolveClaudeThinkingBudget(2048))
        assertEquals(null, resolveClaudeThinkingBudget(1024))
    }

'''
shared_test = replace_once(
    shared_test,
    "    @Test\n    fun compareModeIsFanOutRatherThanAProviderTransport() {\n",
    new_tests + "    @Test\n    fun compareModeIsFanOutRatherThanAProviderTransport() {\n",
    "shared Claude policy tests",
)
shared_test_path.write_text(shared_test)
