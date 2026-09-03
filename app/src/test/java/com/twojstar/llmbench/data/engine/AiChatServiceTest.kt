package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.CHAT_ROLE_ASSISTANT
import com.twojstar.llmbench.data.model.CHAT_ROLE_USER
import com.twojstar.llmbench.data.model.ModelChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_ROLE_KEY = "role"
private const val TEST_CONTENT_KEY = "content"
private const val FIRST_QUESTION = "first question"
private const val FOLLOW_UP = "follow up"
private const val OPENAI_ANSWER = "openai answer"
private const val CLAUDE_ANSWER = "claude answer"
private const val GEMINI_ANSWER = "gemini answer"
private const val SIMULATED_ANSWER = "simulated answer"
private const val SYSTEM_PROMPT = "system"
private const val STREAM_HELLO = "hello"

class AiChatServiceTest {
    @Test
    fun compatibleHistoryKeepsOnlyCurrentProviderAssistantTurns() {
        val history = listOf(
            ModelChatMessage(
                id = "welcome",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.ALL,
                text = "welcome"
            ),
            ModelChatMessage(id = "user-1", sender = CHAT_ROLE_USER, text = "first"),
            ModelChatMessage(
                id = "or-1",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.OPENROUTER,
                text = "openrouter answer"
            ),
            ModelChatMessage(
                id = "ds-1",
                sender = CHAT_ROLE_ASSISTANT,
                provider = AiProvider.DEEPSEEK,
                text = "deepseek answer"
            ),
            ModelChatMessage(id = "user-2", sender = CHAT_ROLE_USER, text = FOLLOW_UP)
        )

        val messages = AiChatService().buildOpenAiCompatibleMessages(
            prompt = FOLLOW_UP,
            systemInstruction = SYSTEM_PROMPT,
            conversationHistory = history,
            provider = AiProvider.OPENROUTER
        )

        assertEquals(listOf(SYSTEM_PROMPT, CHAT_ROLE_USER, CHAT_ROLE_ASSISTANT, CHAT_ROLE_USER), messages.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        assertEquals(listOf(SYSTEM_PROMPT, "first", "openrouter answer", FOLLOW_UP), messages.map {
            it.jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content
        })
    }

    @Test
    fun providerHistoryExcludesDiagnosticsAndSimulations() {
        val userSender = "user"
        val assistantSender = "assistant"
        val kimiModel = "kimi-k2.6"
        val liveAnswer = "live answer"
        val apiFailure = "API failed"
        val simulatedAnswer = SIMULATED_ANSWER
        val otherProviderAnswer = "other provider answer"
        val nextQuestion = "next question"
        val history = listOf(
            ModelChatMessage(id = "u1", sender = userSender, text = FIRST_QUESTION),
            ModelChatMessage(
                id = "live", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = liveAnswer
            ),
            ModelChatMessage(
                id = "error", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = apiFailure, isError = true
            ),
            ModelChatMessage(
                id = "sim", sender = assistantSender, provider = AiProvider.KIMI,
                modelName = kimiModel, text = simulatedAnswer, isSimulated = true
            ),
            ModelChatMessage(
                id = "other", sender = assistantSender, provider = AiProvider.DEEPSEEK,
                modelName = "deepseek-v4-flash", text = otherProviderAnswer
            ),
            ModelChatMessage(id = "u2", sender = userSender, text = nextQuestion)
        )

        val messages = AiChatService().buildOpenAiCompatibleMessages(
            prompt = nextQuestion,
            systemInstruction = null,
            conversationHistory = history,
            provider = AiProvider.KIMI
        )
        val contents = messages.map { it.jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content }
        val roles = messages.map { it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content }

        assertEquals(listOf(userSender, assistantSender, userSender), roles)
        assertEquals(listOf(FIRST_QUESTION, liveAnswer, nextQuestion), contents)
        assertFalse(apiFailure in contents)
        assertFalse(simulatedAnswer in contents)
        assertFalse(otherProviderAnswer in contents)
        assertTrue(liveAnswer in contents)
    }

    @Test
    fun directProviderHistoryUsesNativeRolesAndProviderScopedAnswers() {
        val prompt = FOLLOW_UP
        val history = listOf(
            ModelChatMessage(id = "u1", sender = CHAT_ROLE_USER, text = FIRST_QUESTION),
            ModelChatMessage(
                id = "gpt", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CHATGPT, text = OPENAI_ANSWER
            ),
            ModelChatMessage(
                id = "claude", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CLAUDE, text = CLAUDE_ANSWER
            ),
            ModelChatMessage(
                id = "gemini", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.GEMINI, text = GEMINI_ANSWER
            ),
            ModelChatMessage(
                id = "simulated", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CHATGPT,
                text = SIMULATED_ANSWER, isSimulated = true
            ),
            ModelChatMessage(
                id = "error", sender = CHAT_ROLE_ASSISTANT, provider = AiProvider.CLAUDE,
                text = "error answer", isError = true
            ),
            ModelChatMessage(id = "u2", sender = CHAT_ROLE_USER, text = prompt)
        )
        val service = AiChatService()

        val gemini = service.buildGeminiContents(prompt, history)
        assertEquals(listOf(CHAT_ROLE_USER, "model", CHAT_ROLE_USER), gemini.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        assertEquals(listOf(FIRST_QUESTION, GEMINI_ANSWER, prompt), gemini.map {
            it.jsonObject.getValue("parts").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
        })

        val openAi = service.buildOpenAiResponseInput(prompt, history)
        assertEquals(listOf(CHAT_ROLE_USER, CHAT_ROLE_ASSISTANT, CHAT_ROLE_USER), openAi.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        assertEquals(listOf(FIRST_QUESTION, OPENAI_ANSWER, prompt), openAi.map {
            it.jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content
        })

        val claude = service.buildClaudeMessages(prompt, history)
        assertEquals(listOf(CHAT_ROLE_USER, CHAT_ROLE_ASSISTANT, CHAT_ROLE_USER), claude.map {
            it.jsonObject.getValue(TEST_ROLE_KEY).jsonPrimitive.content
        })
        assertEquals(listOf(FIRST_QUESTION, CLAUDE_ANSWER, prompt), claude.map {
            it.jsonObject.getValue(TEST_CONTENT_KEY).jsonPrimitive.content
        })
    }

    @Test
    fun parsesOpenRouterCatalogPricingAndModalities() {
        val raw = """
            {
              "data": [
                {
                  "id": "vendor/free-model:free",
                  "pricing": {"prompt": "0", "completion": "0"},
                  "architecture": {"output_modalities": ["text"]}
                },
                {
                  "id": "vendor/image-only",
                  "pricing": {"prompt": "0", "completion": "0"},
                  "architecture": {"output_modalities": ["image"]}
                },
                {
                  "id": "vendor/unknown-output",
                  "pricing": {"prompt": "0", "completion": "0"}
                }
              ]
            }
        """.trimIndent()

        val entries = AiChatService().parseGatewayModelCatalog(AiProvider.OPENROUTER, raw)

        assertEquals(3, entries.size)
        assertEquals("vendor/free-model:free", entries.first().id)
        assertEquals(0.0, entries.first().inputPriceUsd ?: -1.0, 0.0)
        assertTrue(entries.first().supportsTextOutput)
        assertFalse(entries[1].supportsTextOutput)
        assertFalse(entries.last().supportsTextOutput)
    }

    @Test
    fun rejectsCatalogWithoutDataArray() {
        val result = runCatching {
            AiChatService().parseGatewayModelCatalog(AiProvider.OPENROUTER, "{}")
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun parsesAiHubMixCatalogPricingAndType() {
        val raw = """
            {
              "data": [
                {
                  "model_id": "coding-model-free",
                  "types": "LLM",
                  "pricing": {"input": 0, "output": 0}
                },
                {
                  "model_id": "image-model",
                  "types": "image_generation",
                  "pricing": {"input": 0, "output": 0}
                }
              ]
            }
        """.trimIndent()

        val entries = AiChatService().parseGatewayModelCatalog(AiProvider.AIHUBMIX, raw)

        assertEquals(2, entries.size)
        assertEquals("coding-model-free", entries.first().id)
        assertEquals(0.0, entries.first().outputPriceUsd ?: -1.0, 0.0)
        assertTrue(entries.first().supportsTextOutput)
        assertFalse(entries.last().supportsTextOutput)
    }
    @Test
    fun extractsNativeStreamingTextDeltas() {
        val service = AiChatService()
        val gemini = Json.parseToJsonElement(
            """{"candidates":[{"content":{"parts":[{"text":"hel"},{"text":"lo"}]}}]}"""
        ).jsonObject
        val openAi = Json.parseToJsonElement(
            """{"type":"response.output_text.delta","delta":"hello"}"""
        ).jsonObject
        val claude = Json.parseToJsonElement(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"hello"}}"""
        ).jsonObject

        assertEquals(STREAM_HELLO, service.extractGeminiStreamText(gemini))
        assertEquals(STREAM_HELLO, service.extractOpenAiStreamText(openAi))
        assertEquals(STREAM_HELLO, service.extractClaudeStreamText(claude))
    }

    @Test
    fun ignoresNonTextNativeStreamingEvents() {
        val service = AiChatService()
        val openAi = Json.parseToJsonElement(
            """{"type":"response.completed","response":{"id":"resp_1"}}"""
        ).jsonObject
        val openAiIncomplete = Json.parseToJsonElement(
            """{"type":"response.incomplete","response":{"id":"resp_2","incomplete_details":{"reason":"max_output_tokens"}}}"""
        ).jsonObject
        val claude = Json.parseToJsonElement(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""
        ).jsonObject

        assertEquals(null, service.extractOpenAiStreamText(openAi))
        assertEquals(null, service.extractClaudeStreamText(claude))
    }

    @Test
    fun recognizesNativeStreamingCompletionEvents() {
        val service = AiChatService()
        val gemini = Json.parseToJsonElement(
            """{"candidates":[{"finishReason":"STOP"}]}"""
        ).jsonObject
        val openAi = Json.parseToJsonElement(
            """{"type":"response.completed","response":{"id":"resp_1"}}"""
        ).jsonObject
        val openAiIncomplete = Json.parseToJsonElement(
            """{"type":"response.incomplete","response":{"id":"resp_2","incomplete_details":{"reason":"max_output_tokens"}}}"""
        ).jsonObject
        val claude = Json.parseToJsonElement(
            """{"type":"message_stop"}"""
        ).jsonObject

        assertTrue(service.isGeminiStreamComplete(gemini))
        assertTrue(service.isOpenAiStreamComplete(openAi))
        assertFalse(service.isOpenAiStreamComplete(openAiIncomplete))
        assertEquals("OpenAI response incomplete: max_output_tokens", service.extractStreamError(openAiIncomplete))
        assertTrue(service.isClaudeStreamComplete(claude))
    }

    @Test
    fun extractsOpenAiResponseFailedError() {
        val service = AiChatService()
        val failed = Json.parseToJsonElement(
            """{"type":"response.failed","response":{"error":{"message":"quota exhausted"}}}"""
        ).jsonObject

        assertEquals("quota exhausted", service.extractStreamError(failed))
        assertFalse(service.isOpenAiStreamComplete(failed))
    }

    @Test
    fun rejectsNonStopGeminiFinishReasons() {
        val service = AiChatService()
        val limited = Json.parseToJsonElement(
            """{"candidates":[{"finishReason":"MAX_TOKENS","finishMessage":"token limit reached"}]}"""
        ).jsonObject

        assertFalse(service.isGeminiStreamComplete(limited))
        assertEquals("Gemini stopped with MAX_TOKENS: token limit reached", service.extractStreamError(limited))
    }

    @Test
    fun streamingDeltaCallbackFailurePropagates() {
        val service = AiChatService()
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.test/stream").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())
            )
            .build()

        val result = runCatching {
            service.readSseResponse(
                response = response,
                extractText = service::extractOpenAiStreamText,
                isComplete = service::isOpenAiStreamComplete,
                onTextDelta = { error("UI callback failed") }
            )
        }

        assertTrue(result.isFailure)
        assertEquals("Streaming text callback failed", result.exceptionOrNull()?.message)
    }

}
