from pathlib import Path

path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
text = path.read_text()

model_name_literal = '"gemini-test-model"'
model_name_count = text.count(model_name_literal)
assert model_name_count >= 2, f"expected repeated Gemini test model, found {model_name_count}"
text = text.replace(model_name_literal, "TEST_GEMINI_MODEL")

role_pattern = 'listOf(CHAT_ROLE_USER, "model", CHAT_ROLE_USER)'
role_count = text.count(role_pattern)
assert role_count >= 2, f"expected repeated Gemini role assertion, found {role_count}"
text = text.replace(role_pattern, 'listOf(CHAT_ROLE_USER, TEST_GEMINI_ROLE, CHAT_ROLE_USER)')

signature_pattern = 'assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)'
signature_count = text.count(signature_pattern)
assert signature_count >= 2, f"expected repeated opaque signature assertion, found {signature_count}"
text = text.replace(
    signature_pattern,
    'assertEquals(TEST_OPAQUE_SIGNATURE, signaturePart.getValue("thoughtSignature").jsonPrimitive.content)'
)

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
