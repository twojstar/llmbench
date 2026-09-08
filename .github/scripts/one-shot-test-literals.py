from pathlib import Path

path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
text = path.read_text()

replacements = {
    '"gemini-test-model"': "TEST_GEMINI_MODEL",
    '"model"': "TEST_GEMINI_ROLE",
    '"opaque-signature"': "TEST_OPAQUE_SIGNATURE",
}
minimum_counts = {
    '"gemini-test-model"': 2,
    '"model"': 2,
    '"opaque-signature"': 2,
}

for old, new in replacements.items():
    count = text.count(old)
    assert count >= minimum_counts[old], f"{old}: expected at least {minimum_counts[old]} matches, found {count}"
    text = text.replace(old, new)

marker = 'private const val TEST_EVENT_STREAM_TYPE = "text/event-stream"\n'
assert text.count(marker) == 1
constants = (
    marker
    + 'private const val TEST_GEMINI_MODEL = "gemini-test-model"\n'
    + 'private const val TEST_GEMINI_ROLE = "model"\n'
    + 'private const val TEST_OPAQUE_SIGNATURE = "opaque-signature"\n'
)
text = text.replace(marker, constants, 1)
path.write_text(text)
print("Gemini test literal cleanup applied")
