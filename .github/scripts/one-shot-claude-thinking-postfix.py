from pathlib import Path

path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
text = path.read_text()
replacements = {
    "private const val TEST_CLAUDE_MODEL = TEST_CLAUDE_MODEL":
        'private const val TEST_CLAUDE_MODEL = "claude-sonnet-5"',
    "private const val TEST_CLAUDE_LEGACY_MODEL = TEST_CLAUDE_LEGACY_MODEL":
        'private const val TEST_CLAUDE_LEGACY_MODEL = "claude-haiku-4-5-20251001"',
}
for old, new in replacements.items():
    count = text.count(old)
    assert count == 1, f"expected one generated constant to repair for {old!r}, found {count}"
    text = text.replace(old, new, 1)
path.write_text(text)
