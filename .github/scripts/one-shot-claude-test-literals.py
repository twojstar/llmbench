from pathlib import Path

path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
text = path.read_text()
marker = 'private const val TEST_CLAUDE_REDACTED_DATA = "redacted-data"\n'
assert text.count(marker) == 1
constants = marker + (
    'private const val TEST_TYPE_KEY = "type"\n'
    'private const val TEST_TEXT_KEY = "text"\n'
    'private const val TEST_SIGNATURE_KEY = "signature"\n'
    'private const val TEST_DATA_KEY = "data"\n'
    'private const val TEST_CLAUDE_THINKING = "thinking"\n'
    'private const val TEST_CLAUDE_REDACTED_THINKING = "redacted_thinking"\n'
)
text = text.replace(marker, constants, 1)

replacements = {
    '.getValue("thinking")': '.getValue(TEST_CLAUDE_THINKING)',
    '.getValue("type")': '.getValue(TEST_TYPE_KEY)',
    '.getValue("signature")': '.getValue(TEST_SIGNATURE_KEY)',
    '.getValue("data")': '.getValue(TEST_DATA_KEY)',
    '.getValue("text")': '.getValue(TEST_TEXT_KEY)',
    'listOf("thinking", "redacted_thinking", "text")':
        'listOf(TEST_CLAUDE_THINKING, TEST_CLAUDE_REDACTED_THINKING, TEST_TEXT_KEY)',
}
for old, new in replacements.items():
    count = text.count(old)
    if old == 'listOf("thinking", "redacted_thinking", "text")':
        assert count == 2, f"expected two Claude block type lists, found {count}"
    else:
        assert count >= 1, f"expected at least one occurrence of {old!r}"
    text = text.replace(old, new)

path.write_text(text)
