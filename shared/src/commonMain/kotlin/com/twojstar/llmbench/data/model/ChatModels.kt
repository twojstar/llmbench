package com.twojstar.llmbench.data.model

import kotlin.time.Clock

import kotlinx.serialization.Serializable

@Serializable
enum class WebAiService(
    val id: String,
    val displayName: String,
    val shortName: String,
    val url: String,
    val description: String,
    val brandHexColor: Long
) {
    CLAUDE(
        id = "claude",
        displayName = "Claude.ai",
        shortName = "Claude",
        url = "https://claude.ai",
        description = "Anthropic Claude account chat with Projects & Artifacts",
        brandHexColor = 0xFFD97706 // Warm Amber
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "ChatGPT",
        shortName = "ChatGPT",
        url = "https://chatgpt.com",
        description = "OpenAI ChatGPT account chat with tools and projects",
        brandHexColor = 0xFF10B981 // Emerald Green
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini Chat",
        shortName = "Gemini",
        url = "https://gemini.google.com",
        description = "Google Gemini account chat with multimodal and Workspace tools",
        brandHexColor = 0xFF0EA5E9 // Sky Blue
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek Chat",
        shortName = "DeepSeek",
        url = "https://chat.deepseek.com",
        description = "DeepSeek web chat with reasoning, coding, and long context",
        brandHexColor = 0xFF2563EB // Deep Blue
    ),
    KIMI(
        id = "kimi",
        displayName = "Kimi AI",
        shortName = "Kimi",
        url = "https://www.kimi.com",
        description = "Moonshot AI Kimi chat with search, files, agents, and long context",
        brandHexColor = 0xFF8B5CF6 // Violet
    ),
    VIBE(
        id = "vibe",
        displayName = "Mistral Vibe",
        shortName = "Vibe",
        url = "https://chat.mistral.ai",
        description = "Mistral Vibe account chat, formerly Le Chat, with work and agent tools",
        brandHexColor = 0xFFFF7000 // Mistral Orange
    );

    companion object {
        fun fromId(id: String): WebAiService {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLAUDE
        }
    }
}

@Serializable
enum class BrowserAiPlatform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val url: String,
    val description: String
) {
    GEMINI_AI_STUDIO(
        id = "gemini-ai-studio",
        displayName = "Gemini AI Studio",
        shortName = "AI Studio",
        url = "https://aistudio.google.com",
        description = "Google AI Studio browser platform for model playgrounds, builds, and API tooling"
    )
}

@Serializable
enum class AiProvider(
    val id: String,
    val displayName: String,
    val shortName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val description: String
) {
    ALL(
        id = "all",
        displayName = "All Models (Compare)",
        shortName = "All Models",
        defaultModel = "all",
        availableModels = listOf("all"),
        description = "Send one prompt concurrently to every configured native provider"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        shortName = "Claude",
        defaultModel = "claude-sonnet-5",
        availableModels = listOf(
            "claude-sonnet-5",
            "claude-fable-5",
            "claude-opus-5",
            "claude-haiku-4-5-20251001"
        ),
        description = "Anthropic models for coding, reasoning, writing, and agentic workflows"
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "OpenAI",
        shortName = "OpenAI",
        defaultModel = "gpt-5.6",
        availableModels = listOf(
            "gpt-5.6",
            "gpt-5.5"
        ),
        description = "OpenAI GPT models for general text generation, coding, and reasoning"
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        shortName = "Gemini",
        defaultModel = "gemini-3.7-flash",
        availableModels = listOf(
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview"
        ),
        description = "Google multimodal models for coding, agents, reasoning, and long context"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek AI",
        shortName = "DeepSeek",
        defaultModel = "deepseek-v4-flash",
        availableModels = listOf(
            "deepseek-v4-flash",
            "deepseek-v4-pro"
        ),
        description = "DeepSeek V4 models with long context, reasoning, coding, and agent capabilities"
    ),
    KIMI(
        id = "kimi",
        displayName = "Moonshot Kimi AI",
        shortName = "Kimi",
        defaultModel = "kimi-k2.6",
        availableModels = listOf(
            "kimi-k2.6",
            "kimi-k3",
            "kimi-k2.7-code"
        ),
        description = "Moonshot AI models for general chat, frontier reasoning, coding, and long context"
    );

    companion object {
        val concreteProviders: List<AiProvider> = listOf(
            CLAUDE,
            CHATGPT,
            GEMINI,
            DEEPSEEK,
            KIMI
        )

        fun fromId(id: String): AiProvider {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLAUDE
        }
    }
}

@Serializable
data class ApiKeyConfig(
    val geminiKey: String = "",
    val openAiKey: String = "",
    val claudeKey: String = "",
    val deepseekKey: String = "",
    val kimiKey: String = ""
)

@Serializable
data class ModelChatMessage(
    val id: String,
    val sender: String, // "user" or "assistant"
    val provider: AiProvider? = null,
    val modelName: String? = null,
    val text: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val isError: Boolean = false,
    val isSimulated: Boolean = false,
    val latencyMs: Long? = null,
    val activeProfileNotes: List<String> = emptyList()
)