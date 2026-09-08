from pathlib import Path

service_path = Path("app/src/main/java/com/twojstar/llmbench/data/engine/AiChatService.kt")
service = service_path.read_text()

replacements = [
    (
        'private const val JSON_OUTPUT_KEY = "output"\n',
        'private const val JSON_OUTPUT_KEY = "output"\nprivate const val JSON_STATUS_KEY = "status"\n',
    ),
    (
        'private const val OPENAI_RESPONSE_INCOMPLETE = "response.incomplete"\n',
        'private const val OPENAI_RESPONSE_INCOMPLETE = "response.incomplete"\nprivate const val OPENAI_STATUS_FAILED = "failed"\nprivate const val OPENAI_STATUS_INCOMPLETE = "incomplete"\n',
    ),
    (
        '        val parsed = json.parseToJsonElement(responseBody).jsonObject\n        return OpenAiGenerationResult(\n',
        '        val parsed = json.parseToJsonElement(responseBody).jsonObject\n        ensureOpenAiBufferedResponseCompleted(parsed)\n        return OpenAiGenerationResult(\n',
    ),
]
for old, new in replacements:
    count = service.count(old)
    assert count == 1, f"unexpected service anchor count {count}: {old!r}"
    service = service.replace(old, new, 1)

anchor = '''    internal fun extractOpenAiCompletedReplayState(event: JsonObject): String? =
        if (event[STREAM_TYPE_KEY]?.jsonPrimitive?.contentOrNull == OPENAI_RESPONSE_COMPLETED) {
            (event[STREAM_RESPONSE_KEY] as? JsonObject)?.let(::extractOpenAiReplayState)
        } else {
            null
        }

'''
helper = '''    internal fun ensureOpenAiBufferedResponseCompleted(response: JsonObject) {
        when (response[JSON_STATUS_KEY]?.jsonPrimitive?.contentOrNull) {
            OPENAI_STATUS_FAILED -> {
                val message = (response[STREAM_ERROR_KEY] as? JsonObject)
                    ?.get(STREAM_MESSAGE_KEY)?.jsonPrimitive?.contentOrNull
                    ?: "OpenAI response failed"
                throw IOException(message)
            }
            OPENAI_STATUS_INCOMPLETE -> {
                val reason = (response[OPENAI_INCOMPLETE_DETAILS_KEY] as? JsonObject)
                    ?.get(OPENAI_INCOMPLETE_REASON_KEY)?.jsonPrimitive?.contentOrNull
                throw IOException(
                    reason?.let { "OpenAI response incomplete: $it" }
                        ?: "OpenAI response incomplete"
                )
            }
        }
    }

'''
count = service.count(anchor)
assert count == 1, f"unexpected helper anchor count: {count}"
service = service.replace(anchor, anchor + helper, 1)
service_path.write_text(service)

test_path = Path("app/src/test/java/com/twojstar/llmbench/data/engine/AiChatServiceTest.kt")
test = test_path.read_text()
test_anchor = '''    @Test
    fun openAiStreamingCapturesCompletedEncryptedReasoningOutput() {
'''
regression = '''    @Test
    fun openAiBufferedTerminalFailuresAreRejectedBeforeReplayCapture() {
        val service = AiChatService()
        val incomplete = Json.parseToJsonElement(
            """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":${openAiReplayStateJson()}}"""
        ).jsonObject
        val failed = Json.parseToJsonElement(
            """{"status":"failed","error":{"message":"server exploded"},"output":${openAiReplayStateJson()}}"""
        ).jsonObject

        val incompleteFailure = runCatching {
            service.ensureOpenAiBufferedResponseCompleted(incomplete)
        }.exceptionOrNull()
        val failedFailure = runCatching {
            service.ensureOpenAiBufferedResponseCompleted(failed)
        }.exceptionOrNull()

        assertTrue(incompleteFailure is java.io.IOException)
        assertEquals("OpenAI response incomplete: max_output_tokens", incompleteFailure?.message)
        assertTrue(failedFailure is java.io.IOException)
        assertEquals("server exploded", failedFailure?.message)
    }

'''
count = test.count(test_anchor)
assert count == 1, f"unexpected test anchor count: {count}"
test = test.replace(test_anchor, regression + test_anchor, 1)
test_path.write_text(test)

print("OpenAI buffered terminal-status patch applied")
