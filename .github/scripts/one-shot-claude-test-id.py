from pathlib import Path

path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
text = path.read_text()
anchor = 'private const val TEST_CLAUDE_MODEL = "claude-sonnet-5"\n'
constant = anchor + 'private const val TEST_CLAUDE_MESSAGE_ID = "claude"\n'
assert text.count(anchor) == 1
assert 'TEST_CLAUDE_MESSAGE_ID' not in text
text = text.replace(anchor, constant, 1)
pattern = 'id = "claude"'
count = text.count(pattern)
assert count >= 2, count
text = text.replace(pattern, 'id = TEST_CLAUDE_MESSAGE_ID')
path.write_text(text)
assert path.read_text().count(pattern) == 0
