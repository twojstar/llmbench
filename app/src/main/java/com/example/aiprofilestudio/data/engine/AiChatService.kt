package com.example.aiprofilestudio.data.engine

import com.example.aiprofilestudio.data.model.AiProvider
import com.example.aiprofilestudio.data.model.ApiKeyConfig
import com.example.aiprofilestudio.data.model.ModelChatMessage
import com.example.aiprofilestudio.data.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiChatService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun generateResponse(
        prompt: String,
        provider: AiProvider,
        modelName: String,
        apiKeys: ApiKeyConfig,
        systemInstruction: String?,
        profile: Profile?,
        conversationHistory: List<ModelChatMessage> = emptyList()
    ): ModelChatMessage = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val effectiveModel = if (modelName == "all" || modelName.isBlank()) provider.defaultModel else modelName

        val (key, isKeyProvided) = when (provider) {
            AiProvider.GEMINI -> Pair(apiKeys.geminiKey.trim(), apiKeys.geminiKey.isNotBlank())
            AiProvider.CHATGPT -> Pair(apiKeys.openAiKey.trim(), apiKeys.openAiKey.isNotBlank())
            AiProvider.CLAUDE -> Pair(apiKeys.claudeKey.trim(), apiKeys.claudeKey.isNotBlank())
            AiProvider.DEEPSEEK -> Pair(apiKeys.deepseekKey.trim(), apiKeys.deepseekKey.isNotBlank())
            AiProvider.KIMI -> Pair(apiKeys.kimiKey.trim(), apiKeys.kimiKey.isNotBlank())
            AiProvider.ALL -> Pair("", false)
        }

        // If key is available, attempt real live REST call
        if (isKeyProvided) {
            try {
                val realResult = when (provider) {
                    AiProvider.GEMINI -> callGeminiApi(prompt, effectiveModel, key, systemInstruction)
                    AiProvider.CHATGPT -> callOpenAiApi(prompt, effectiveModel, key, systemInstruction)
                    AiProvider.CLAUDE -> callClaudeApi(prompt, effectiveModel, key, systemInstruction)
                    AiProvider.DEEPSEEK -> callOpenAiCompatibleApi("https://api.deepseek.com/chat/completions", prompt, effectiveModel, key, systemInstruction)
                    AiProvider.KIMI -> callOpenAiCompatibleApi("https://api.moonshot.cn/v1/chat/completions", prompt, effectiveModel, key, systemInstruction)
                    AiProvider.ALL -> null
                }

                if (realResult != null) {
                    val latency = System.currentTimeMillis() - startTime
                    val activeNotes = extractProfileNotes(profile)
                    return@withContext ModelChatMessage(
                        id = "msg_${System.currentTimeMillis()}_${provider.id}",
                        sender = "assistant",
                        provider = provider,
                        modelName = effectiveModel,
                        text = realResult,
                        isError = false,
                        isSimulated = false,
                        latencyMs = latency,
                        activeProfileNotes = activeNotes
                    )
                }
            } catch (e: Exception) {
                // If network failed with real key, return error with details
                val latency = System.currentTimeMillis() - startTime
                return@withContext ModelChatMessage(
                    id = "msg_${System.currentTimeMillis()}_${provider.id}",
                    sender = "assistant",
                    provider = provider,
                    modelName = effectiveModel,
                    text = "⚠️ Error communicating with ${provider.displayName} API ($effectiveModel):\n\n${e.localizedMessage ?: e.message ?: "Unknown error"}\n\nFalling back to profile persona simulation below:\n\n" +
                            generatePersonaResponse(prompt, provider, effectiveModel, profile),
                    isError = true,
                    isSimulated = true,
                    latencyMs = latency,
                    activeProfileNotes = listOf("API Request Failed - Returned fallback simulation")
                )
            }
        }

        // No key provided or in demonstration mode: generate high-fidelity persona simulation
        kotlinx.coroutines.delay(450) // Realistic interactive delay
        val simulatedText = generatePersonaResponse(prompt, provider, effectiveModel, profile)
        val latency = System.currentTimeMillis() - startTime
        val notes = extractProfileNotes(profile).toMutableList()
        notes.add(0, "⚡ Mode: Ready for Live Keys (tap Key icon to connect ${provider.shortName} API)")

        ModelChatMessage(
            id = "msg_${System.currentTimeMillis()}_${provider.id}",
            sender = "assistant",
            provider = provider,
            modelName = effectiveModel,
            text = simulatedText,
            isError = false,
            isSimulated = true,
            latencyMs = latency,
            activeProfileNotes = notes
        )
    }

    private fun extractProfileNotes(profile: Profile?): List<String> {
        if (profile == null) return emptyList()
        val notes = mutableListOf<String>()
        notes.add("Base Voice: ${profile.personality.base} (lvl ${profile.personality.intensity ?: 1})")
        profile.personality.modifiers.filter { (it.value ?: 0) > 0 }.forEach { (k, v) ->
            notes.add("$k: $v")
        }
        notes.add("Initiative: ${profile.collaboration.initiative} • Verification: ${profile.collaboration.verification}")
        return notes
    }

    // --- Google Gemini REST API ---
    private fun callGeminiApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contentsArray = buildJsonArray {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                }
            }
        }

        val requestPayload = buildJsonObject {
            put("contents", contentsArray)
            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemInstruction) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.7)
            }
        }

        val body = requestPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from Gemini server")
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody) ?: "HTTP ${response.code}: ${response.message}"
                throw Exception(errorMsg)
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val candidates = parsed["candidates"]?.jsonArray
            val firstCandidate = candidates?.getOrNull(0)?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val text = parts?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

            return text ?: "Received empty content response from Gemini."
        }
    }

    // --- OpenAI ChatGPT REST API ---
    private fun callOpenAiApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?
    ): String {
        val url = "https://api.openai.com/v1/chat/completions"

        val messagesArray = buildJsonArray {
            if (!systemInstruction.isNullOrBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemInstruction)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
        }

        val body = requestPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI server")
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody) ?: "HTTP ${response.code}: ${response.message}"
                throw Exception(errorMsg)
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val choices = parsed["choices"]?.jsonArray
            val firstChoice = choices?.getOrNull(0)?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.contentOrNull

            return content ?: "Received empty message content from OpenAI."
        }
    }

    // --- Anthropic Claude REST API ---
    private fun callClaudeApi(
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?
    ): String {
        val url = "https://api.anthropic.com/v1/messages"

        val messagesArray = buildJsonArray {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            if (!systemInstruction.isNullOrBlank()) {
                put("system", systemInstruction)
            }
            put("messages", messagesArray)
        }

        val body = requestPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from Anthropic server")
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody) ?: "HTTP ${response.code}: ${response.message}"
                throw Exception(errorMsg)
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val contentArray = parsed["content"]?.jsonArray
            val firstBlock = contentArray?.getOrNull(0)?.jsonObject
            val text = firstBlock?.get("text")?.jsonPrimitive?.contentOrNull

            return text ?: "Received empty content block from Claude."
        }
    }

    // --- OpenAI Compatible API (DeepSeek & Kimi) ---
    private fun callOpenAiCompatibleApi(
        endpointUrl: String,
        prompt: String,
        model: String,
        apiKey: String,
        systemInstruction: String?
    ): String {
        val messagesArray = buildJsonArray {
            if (!systemInstruction.isNullOrBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemInstruction)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
        }

        val body = requestPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from server")
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody) ?: "HTTP ${response.code}: ${response.message}"
                throw Exception(errorMsg)
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val choices = parsed["choices"]?.jsonArray
            val firstChoice = choices?.getOrNull(0)?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.contentOrNull

            return content ?: "Received empty message content."
        }
    }

    private fun parseErrorMessage(rawJson: String): String? {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            when {
                obj.containsKey("error") -> {
                    val err = obj["error"]
                    if (err is JsonObject) {
                        err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString()
                    } else {
                        err?.jsonPrimitive?.contentOrNull
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- High Fidelity Persona & Style Adaptation Simulator ---
    private fun generatePersonaResponse(
        prompt: String,
        provider: AiProvider,
        model: String,
        profile: Profile?
    ): String {
        val baseVoice = profile?.personality?.base ?: "friendly"
        val conciseLvl = profile?.personality?.modifiers?.get("concise") ?: 0
        val technicalLvl = profile?.personality?.modifiers?.get("technical") ?: 0
        val cynicalLvl = profile?.personality?.modifiers?.get("cynical") ?: 0
        val lowerPrompt = prompt.lowercase()

        // Distinguish provider traits while respecting active style profile
        return when (provider) {
            AiProvider.GEMINI -> {
                when {
                    lowerPrompt.contains("compare") || lowerPrompt.contains("difference") -> {
                        """
                        ### 🔮 Google Gemini ($model) Analysis
                        
                        **Multimodal & Context Window Strengths:**
                        1. **Massive Token Context**: Gemini 3.5 Flash and 3.1 Pro handle up to 2M tokens, allowing whole-codebase repository reasoning in a single prompt.
                        2. **Native Multimodality**: Native interleaved video, audio, code, and vision ingestion without separate tokenizer pipelines.
                        3. **Structured Outputs**: Direct OpenAPI schema conformance for function calling and JSON payloads.
                        
                        *Style Profile applied: Voice '$baseVoice', Initiative '${profile?.collaboration?.initiative ?: "balanced"}'.*
                        """.trimIndent()
                    }
                    lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") -> {
                        """
                        ### 🔮 Google Gemini Solution ($model)
                        
                        Here is the idiomatic implementation utilizing modern coroutines and Compose state:
                        
                        ```kotlin
                        // Modern asynchronous pipeline
                        suspend fun processPrompt(query: String): Result<String> = withContext(Dispatchers.IO) {
                            runCatching {
                                GeminiClient.generateContent(query)
                            }
                        }
                        ```
                        
                        **Key architectural advantages:**
                        - Non-blocking I/O execution on background dispatchers.
                        - Preserves active personality constraints (`$baseVoice`).
                        """.trimIndent()
                    }
                    else -> {
                        if (conciseLvl >= 2) {
                            "**Gemini ($model)**: Directly addressing '$prompt'. In summary: verified input parameters, structured context bounds, and executed inference with 0-shot precision."
                        } else {
                            "Hello from **Google Gemini** ($model)! Analyzing your request regarding '$prompt':\n\nGemini models are tuned for high-velocity synthesis, multi-step problem solving, and contextual reasoning. Let me know if you would like me to drill into technical implementation details or expand with concrete code snippets."
                        }
                    }
                }
            }

            AiProvider.CHATGPT -> {
                when {
                    lowerPrompt.contains("compare") || lowerPrompt.contains("difference") -> {
                        """
                        ### 🟢 OpenAI ChatGPT ($model) Perspective
                        
                        Here is how ChatGPT approaches conversational intelligence:
                        
                        - **Conversational Fluency**: Tuned for natural, step-by-step dialogue and creative structuring.
                        - **Instruction Following**: Exceptional adherence to system prompts and behavioral guardrails.
                        - **Tool Integration**: Code Interpreter, web browsing, and custom GPT actions seamlessly chain tasks.
                        
                        *Active Profile Voice: $baseVoice (concise level: $conciseLvl, technical level: $technicalLvl).*
                        """.trimIndent()
                    }
                    lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") -> {
                        """
                        ### 🟢 OpenAI ChatGPT Code Solution ($model)
                        
                        Here is a clean and modular solution:
                        
                        ```kotlin
                        data class ChatState(
                            val messages: List<ChatMessage> = emptyList(),
                            val isTyping: Boolean = false
                        )
                        ```
                        
                        **Notes:**
                        1. Encapsulates message state cleanly for unidirectional data flow.
                        2. Easily testable in unit test suites.
                        """.trimIndent()
                    }
                    else -> {
                        if (cynicalLvl >= 2) {
                            "**ChatGPT ($model)**: Regarding '$prompt': Let's skip the corporate marketing speak. If your premise lacks sound architecture, no prompt engineering will save it. Fix the core invariants first."
                        } else if (conciseLvl >= 2) {
                            "**ChatGPT ($model)**: Understood. Action items for '$prompt':\n1. Define schema contracts\n2. Stream token chunks\n3. Render message bubbles."
                        } else {
                            "Hi! I'm **ChatGPT** ($model). Regarding '$prompt':\n\nI can help you build, refine, and debug your ideas. I adapt to your chosen tone ('$baseVoice') while keeping solutions practical and conversational. What would you like to explore next?"
                        }
                    }
                }
            }

            AiProvider.CLAUDE -> {
                when {
                    lowerPrompt.contains("compare") || lowerPrompt.contains("difference") -> {
                        """
                        ### 🟣 Anthropic Claude ($model) Perspective
                        
                        When examining architectural differences between LLM providers:
                        
                        1. **Nuance and Artifact Quality**: Claude 3.5 Sonnet excels in subtle linguistic cadence, avoiding conversational fluff while producing rigorous, well-formatted documents.
                        2. **Refusal Calibration**: Transparent and calibrated refusal mechanics designed to assist safe developer workflows without unwarranted friction.
                        3. **Complex Code Synthesis**: Superior understanding of complex cross-module dependencies and edge-case error handling.
                        
                        *System Policy: Tone calibrated to '$baseVoice'.*
                        """.trimIndent()
                    }
                    lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") -> {
                        """
                        ### 🟣 Anthropic Claude Code Formulation ($model)
                        
                        Let's structure this cleanly with proper error boundaries:
                        
                        ```kotlin
                        sealed interface ChatEvent {
                            data class MessageSent(val text: String) : ChatEvent
                            data class ProviderChanged(val provider: AiProvider) : ChatEvent
                            object Cleared : ChatEvent
                        }
                        ```
                        
                        This algebraic data type structure ensures complete compile-time pattern matching in your Compose `when` expressions.
                        """.trimIndent()
                    }
                    else -> {
                        if (conciseLvl >= 2) {
                            "**Claude ($model)**: Direct response to '$prompt': Core principle is rigorous composability. We apply semantic overlays sequentially without mutating baseline contracts."
                        } else {
                            "Greetings! I'm **Claude** ($model) by Anthropic. In response to '$prompt':\n\nI strive to provide thoughtful, articulate, and well-reasoned answers, aligned with your current profile ($baseVoice style, verification set to ${profile?.collaboration?.verification ?: "balanced"}). Let me know how I can best support your work."
                        }
                    }
                }
            }

            AiProvider.DEEPSEEK -> {
                when {
                    lowerPrompt.contains("compare") || lowerPrompt.contains("difference") -> {
                        """
                        ### 🔷 DeepSeek ($model) Analysis
                        
                        **Key Architecture & Strengths:**
                        1. **DeepSeek-R1 Chain of Thought**: Open-weights reasoning powerhouse with explicit reasoning traces (`<think>...</think>`).
                        2. **DeepSeek-V3 MoE Architecture**: Ultra-efficient Mixture-of-Experts routing delivering frontier-class coding and math at extreme compute efficiency.
                        3. **Open Architecture & Native Reasoning**: Exceptional at intricate algorithmic decomposition, competitive programming, and formal logic.
                        
                        *Style Profile applied: Voice '$baseVoice'.*
                        """.trimIndent()
                    }
                    lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") -> {
                        """
                        ### 🔷 DeepSeek Code Solution ($model)
                        
                        Here is an optimized implementation focusing on runtime efficiency:
                        
                        ```kotlin
                        inline fun <T, R> Sequence<T>.concurrentMap(
                            crossinline transform: suspend (T) -> R
                        ): Flow<R> = flow {
                            coroutineScope {
                                map { item -> async { transform(item) } }
                                    .toList()
                                    .awaitAll()
                                    .forEach { emit(it) }
                            }
                        }
                        ```
                        
                        **Analysis:**
                        - Preserves backpressure while maintaining bounded concurrency.
                        - Adheres to active personality guidelines (`$baseVoice`).
                        """.trimIndent()
                    }
                    else -> {
                        if (conciseLvl >= 2) {
                            "**DeepSeek ($model)**: '$prompt': Breakdown:\n1. Formulate problem space\n2. Minimize computational complexity\n3. Execute with verified test cases."
                        } else {
                            "Hello! I am **DeepSeek** ($model). In addressing '$prompt':\n\nI specialize in deep logical reasoning, coding, and mathematical analysis. I am configured with your active profile ($baseVoice tone, ${profile?.collaboration?.initiative ?: "balanced"} initiative). How can I assist with your development or research today?"
                        }
                    }
                }
            }

            AiProvider.KIMI -> {
                when {
                    lowerPrompt.contains("compare") || lowerPrompt.contains("difference") -> {
                        """
                        ### ⚡ Moonshot Kimi ($model) Analysis
                        
                        **Key Strengths & Long-Context Mastery:**
                        1. **2 Million Token Context Window**: Kimi (Moonshot AI) processes mammoth PDFs, complete code repositories, and exhaustive documentation without loss of recall.
                        2. **Research & Synthesis Engine**: Advanced parsing of structured data, research papers, and technical specifications.
                        3. **Multilingual Precision**: Industry-leading fluency in both Chinese and English technical literature.
                        
                        *Style Profile applied: Voice '$baseVoice'.*
                        """.trimIndent()
                    }
                    lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") -> {
                        """
                        ### ⚡ Kimi Code Generation ($model)
                        
                        Here is the clean, production-ready implementation:
                        
                        ```kotlin
                        class DocumentProcessor(private val maxChunkSize: Int = 8192) {
                            fun chunkText(content: String): List<String> {
                                return content.chunked(maxChunkSize)
                            }
                        }
                        ```
                        
                        **Features:**
                        - Handles ultra-long context streams gracefully.
                        - Follows '$baseVoice' style constraints.
                        """.trimIndent()
                    }
                    else -> {
                        if (conciseLvl >= 2) {
                            "**Kimi ($model)**: Response to '$prompt': Processing long-context input with precision. Ready to ingest and summarize comprehensive documentation."
                        } else {
                            "Hello! I am **Kimi** ($model) from Moonshot AI. Regarding '$prompt':\n\nI can read and analyze massive documents, codebases, and conversations with high speed and precision, customized to your '$baseVoice' personality profile. What would you like to process?"
                        }
                    }
                }
            }

            AiProvider.ALL -> "Multi-provider dispatch."
        }
    }
}
