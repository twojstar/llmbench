package com.twojstar.llmbench.data.model

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
        description = "Anthropic Claude Pro / Free account with Projects & Artifacts",
        brandHexColor = 0xFFD97706 // Warm Amber
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "ChatGPT",
        shortName = "ChatGPT",
        url = "https://chatgpt.com",
        description = "OpenAI ChatGPT Plus / Free account with GPTs & Canvas",
        brandHexColor = 0xFF10B981 // Emerald Green
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini Chat",
        shortName = "Gemini",
        url = "https://gemini.google.com",
        description = "Google Gemini Advanced / Free account with Workspace tools",
        brandHexColor = 0xFF0EA5E9 // Sky Blue
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek Chat",
        shortName = "DeepSeek",
        url = "https://chat.deepseek.com",
        description = "DeepSeek-V3 & DeepSeek-R1 Deep Thinking web interface",
        brandHexColor = 0xFF2563EB // Deep Blue
    ),
    KIMI(
        id = "kimi",
        displayName = "Kimi AI",
        shortName = "Kimi",
        url = "https://kimi.moonshot.cn",
        description = "Moonshot AI Kimi with long-context web search & research",
        brandHexColor = 0xFF8B5CF6 // Violet
    );

    companion object {
        fun fromId(id: String): WebAiService {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLAUDE
        }
    }
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
        description = "Send prompt simultaneously to ChatGPT, Gemini, Claude, DeepSeek & Kimi"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        shortName = "Claude",
        defaultModel = "claude-3-5-sonnet-20241022",
        availableModels = listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        ),
        description = "Anthropic's safety-first models renowned for articulate writing, nuanced code analysis & depth"
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "OpenAI ChatGPT",
        shortName = "ChatGPT",
        defaultModel = "gpt-4o",
        availableModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4-turbo",
            "o3-mini"
        ),
        description = "OpenAI's flagship intelligence models optimized for versatile conversation and coding"
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        shortName = "Gemini",
        defaultModel = "gemini-3.5-flash",
        availableModels = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-flash-latest"
        ),
        description = "Google's next-gen multimodal foundation models with deep reasoning and long context"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek AI",
        shortName = "DeepSeek",
        defaultModel = "deepseek-chat",
        availableModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        description = "State-of-the-art reasoning and coding models with deep step-by-step thinking"
    ),
    KIMI(
        id = "kimi",
        displayName = "Moonshot Kimi AI",
        shortName = "Kimi",
        defaultModel = "moonshot-v1-auto",
        availableModels = listOf(
            "moonshot-v1-8k",
            "moonshot-v1-32k",
            "moonshot-v1-128k"
        ),
        description = "Moonshot AI high-capacity long-context conversational assistant"
    );

    companion object {
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
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isSimulated: Boolean = false,
    val latencyMs: Long? = null,
    val activeProfileNotes: List<String> = emptyList()
)
