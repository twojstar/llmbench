from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f"{label}: expected one anchor, found {count}"
    return text.replace(old, new, 1)


app_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
app = app_path.read_text()
app = replace_once(
    app,
    'private const val JSON_MODEL_KEY = "model"\n',
    'private const val JSON_MODEL_KEY = "model"\nprivate const val JSON_MODEL_ID_KEY = "id"\n',
    "model id constant",
)
app = replace_once(
    app,
    '        json.parseToJsonElement(rawJson).jsonObject[JSON_MODEL_KEY]\n',
    '        json.parseToJsonElement(rawJson).jsonObject[JSON_MODEL_ID_KEY]\n',
    "Models API id field",
)
app_path.write_text(app)


test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
old = '        assertEquals(concrete, service.parseClaudeModelId("""{"model":"$concrete"}"""))\n'
new = (
    '        assertEquals(concrete, service.parseClaudeModelId("""{"id":"$concrete"}"""))\n'
    '        assertEquals(null, service.parseClaudeModelId("""{"model":"$concrete"}"""))\n'
)
test = replace_once(test, old, new, "Models API id regression")
test_path.write_text(test)
