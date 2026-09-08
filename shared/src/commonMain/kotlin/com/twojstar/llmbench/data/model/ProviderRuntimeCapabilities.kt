package com.twojstar.llmbench.data.model

enum class NativeChatTransport {
    COMPARE_FAN_OUT,
    GEMINI_GENERATE_CONTENT,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    OPENAI_COMPATIBLE_CHAT_COMPLETIONS
}

enum class SystemInstructionPlacement {
    PER_PROVIDER,
    NATIVE_FIELD,
    SYSTEM_MESSAGE
}

enum class ConversationStateStrategy {
    PROVIDER_FAN_OUT,
    BOUNDED_PROVIDER_TEXT_REPLAY,
    BOUNDED_PROVIDER_CONTENT_REPLAY
}

enum class ReasoningControlStrategy {
    PER_PROVIDER,
    PROVIDER_DEFAULT,
    MODEL_CAPABILITY_METADATA
}

fun AiProvider.reasoningControlStrategy(): ReasoningControlStrategy = when (this) {
    AiProvider.ALL -> ReasoningControlStrategy.PER_PROVIDER
    AiProvider.CLAUDE -> ReasoningControlStrategy.MODEL_CAPABILITY_METADATA
    else -> ReasoningControlStrategy.PROVIDER_DEFAULT
}

data class ProviderRuntimeCapabilities(
    val transport: NativeChatTransport,
    val systemInstructionPlacement: SystemInstructionPlacement,
    val conversationStateStrategy: ConversationStateStrategy,
    val streamsText: Boolean,
    val reportsResolvedModel: Boolean
)

fun AiProvider.runtimeCapabilities(): ProviderRuntimeCapabilities = when (this) {
    AiProvider.ALL -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.COMPARE_FAN_OUT,
        systemInstructionPlacement = SystemInstructionPlacement.PER_PROVIDER,
        conversationStateStrategy = ConversationStateStrategy.PROVIDER_FAN_OUT,
        streamsText = true,
        reportsResolvedModel = false
    )
    AiProvider.GEMINI -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.GEMINI_GENERATE_CONTENT,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )
    AiProvider.CHATGPT -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.OPENAI_RESPONSES,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )
    AiProvider.CLAUDE -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.ANTHROPIC_MESSAGES,
        systemInstructionPlacement = SystemInstructionPlacement.NATIVE_FIELD,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_CONTENT_REPLAY,
        streamsText = true,
        reportsResolvedModel = false
    )
    AiProvider.DEEPSEEK,
    AiProvider.KIMI,
    AiProvider.OPENROUTER,
    AiProvider.AIHUBMIX -> ProviderRuntimeCapabilities(
        transport = NativeChatTransport.OPENAI_COMPATIBLE_CHAT_COMPLETIONS,
        systemInstructionPlacement = SystemInstructionPlacement.SYSTEM_MESSAGE,
        conversationStateStrategy = ConversationStateStrategy.BOUNDED_PROVIDER_TEXT_REPLAY,
        streamsText = true,
        reportsResolvedModel = true
    )
}
