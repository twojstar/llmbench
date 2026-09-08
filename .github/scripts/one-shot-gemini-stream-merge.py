from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    assert count == 1, f"{path}: expected exactly one match, found {count}"
    target.write_text(text.replace(old, new, 1))


service = "app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt"
replace(
    service,
    '''    private fun encodeGeminiReplayState(contents: List<JsonObject>): String? =
        contents.takeIf { it.isNotEmpty() }?.let { JsonArray(it).toString() }

    private fun parseGeminiReplayState(state: String?): List<JsonObject> {
        if (state.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(state) as? JsonArray
                ?: return@runCatching emptyList()
            val contents = array.mapNotNull { it as? JsonObject }
            if (contents.size != array.size || contents.any { !isValidGeminiReplayContent(it) }) {
                emptyList()
            } else {
                contents
            }
        }.getOrDefault(emptyList())
    }
''',
    '''    internal fun mergeGeminiReplayContents(contents: List<JsonObject>): JsonObject? {
        if (contents.isEmpty() || contents.any { !isValidGeminiReplayContent(it) }) return null
        val parts = contents.flatMap { content ->
            (content[JSON_PARTS_KEY] as JsonArray).toList()
        }
        return buildJsonObject {
            put(JSON_ROLE_KEY, JSON_MODEL_KEY)
            put(JSON_PARTS_KEY, JsonArray(parts))
        }
    }

    private fun encodeGeminiReplayState(contents: List<JsonObject>): String? =
        mergeGeminiReplayContents(contents)?.let { JsonArray(listOf(it)).toString() }

    private fun parseGeminiReplayState(state: String?): List<JsonObject> {
        if (state.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(state) as? JsonArray
                ?: return@runCatching emptyList()
            val contents = array.mapNotNull { it as? JsonObject }
            if (contents.size != array.size) {
                emptyList()
            } else {
                mergeGeminiReplayContents(contents)?.let(::listOf).orEmpty()
            }
        }.getOrDefault(emptyList())
    }
''',
)

tests = "app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt"
replace(
    tests,
    '''        assertEquals(listOf(CHAT_ROLE_USER, "model", "model", CHAT_ROLE_USER), contents.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        val signaturePart = contents[2].jsonObject.getValue("parts").jsonArray.single().jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
''',
    '''        assertEquals(listOf(CHAT_ROLE_USER, "model", CHAT_ROLE_USER), contents.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        val replayParts = contents[1].jsonObject.getValue("parts").jsonArray
        assertEquals(2, replayParts.size)
        val signaturePart = replayParts[1].jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
''',
)
replace(
    tests,
    '''        assertEquals(STREAM_HELLO, text)
        assertEquals(2, replayContents.size)
        val signaturePart = replayContents.last().getValue("parts").jsonArray.single().jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
''',
    '''        assertEquals(STREAM_HELLO, text)
        assertEquals(2, replayContents.size)
        val merged = service.mergeGeminiReplayContents(replayContents)
        val replayParts = requireNotNull(merged).getValue("parts").jsonArray
        assertEquals(2, replayParts.size)
        assertEquals("hello", replayParts[0].jsonObject.getValue("text").jsonPrimitive.content)
        val signaturePart = replayParts[1].jsonObject
        assertEquals("", signaturePart.getValue("text").jsonPrimitive.content)
        assertEquals("opaque-signature", signaturePart.getValue("thoughtSignature").jsonPrimitive.content)
''',
)

print("Gemini stream replay merge patch applied")
