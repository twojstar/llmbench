from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected 1 match, found {count}"
    return text.replace(old, new, 1)


app_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
app = app_path.read_text()
app = replace_once(
    app,
    'private const val CLAUDE_STOP_MAX_TOKENS = "max_tokens"\n',
    'private const val CLAUDE_STOP_MAX_TOKENS = "max_tokens"\nprivate const val CLAUDE_STOP_CONTEXT_WINDOW_EXCEEDED = "model_context_window_exceeded"\n',
    "Claude context stop constant",
)
app = replace_once(
    app,
    '''    internal fun readClaudeMetadataCache(
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
''',
    '''    internal fun readClaudeMetadataCache(
        model: String,
        apiKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClaudeRuntimeMetadata? {
        val cacheKey = claudeMetadataCacheKey(model, apiKey)
        while (true) {
            val entry = claudeMetadataByModelAndCredential[cacheKey] ?: return null
            if (entry.expiresAtMillis == null || nowMillis < entry.expiresAtMillis) {
                return entry.metadata
            }
            if (claudeMetadataByModelAndCredential.remove(cacheKey, entry)) return null
        }
    }
''',
    "Claude cache expiry read",
)
app = replace_once(
    app,
    '''    internal fun isClaudePartialStopReason(stopReason: String?): Boolean =
        stopReason == CLAUDE_STOP_MAX_TOKENS
''',
    '''    internal fun isClaudePartialStopReason(stopReason: String?): Boolean =
        stopReason == CLAUDE_STOP_MAX_TOKENS || stopReason == CLAUDE_STOP_CONTEXT_WINDOW_EXCEEDED
''',
    "Claude partial stop reasons",
)
app_path.write_text(app)

vm_path = Path("app/src/main/java/com/twojstar/llmbench/ui/viewmodel/StudioViewModel.kt")
vm = vm_path.read_text()
vm = replace_once(
    vm,
    '            val finalMessage = response.copy(id = messageId, isPartial = false)\n',
    '            val finalMessage = response.copy(id = messageId)\n',
    "preserve streaming partial marker",
)
vm_path.write_text(vm)

test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test = test.replace('val concrete = "claude-sonnet-4-5-20250929"', 'val concrete = TEST_CLAUDE_CONCRETE_MODEL')
test = test.replace('"bad-key"', 'TEST_BAD_API_KEY')
messages_expr = 'Json.parseToJsonElement("""[{"role":"user","content":"hello"}]""").jsonArray'
assert test.count(messages_expr) == 2, f"Claude messages fixture: {test.count(messages_expr)}"
test = test.replace(messages_expr, 'Json.parseToJsonElement(TEST_CLAUDE_USER_MESSAGES_JSON).jsonArray')
const_anchor = 'private const val TEST_CLAUDE_OUTAGE_MODEL = "claude-outage"\n'
const_block = const_anchor + (
    'private const val TEST_CLAUDE_CONCRETE_MODEL = "claude-sonnet-4-5-20250929"\n'
    'private const val TEST_BAD_API_KEY = "bad-key"\n'
    'private const val TEST_CLAUDE_USER_MESSAGES_JSON = "[{\\"role\\":\\"user\\",\\"content\\":\\"hello\\"}]"\n'
    'private const val TEST_CLAUDE_CONTEXT_STOP = "model_context_window_exceeded"\n'
)
test = replace_once(test, const_anchor, const_block, "Claude test constants")
stop_assert = '        assertTrue(service.isClaudePartialStopReason(service.extractClaudeStreamStopReason(streamed)))\n        assertFalse(service.isClaudePartialStopReason("end_turn"))\n'
stop_replacement = '        assertTrue(service.isClaudePartialStopReason(service.extractClaudeStreamStopReason(streamed)))\n        assertTrue(service.isClaudePartialStopReason(TEST_CLAUDE_CONTEXT_STOP))\n        assertFalse(service.isClaudePartialStopReason("end_turn"))\n'
test = replace_once(test, stop_assert, stop_replacement, "Claude context stop regression")
test_path.write_text(test)

assert app_path.read_text().count('CLAUDE_STOP_CONTEXT_WINDOW_EXCEEDED') == 2
assert vm_path.read_text().count('response.copy(id = messageId, isPartial = false)') == 0
assert test_path.read_text().count('val concrete = TEST_CLAUDE_CONCRETE_MODEL') == 2
assert test_path.read_text().count('TEST_BAD_API_KEY') >= 4
