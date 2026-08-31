package com.example.aiprofilestudio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val schemaVersion: String = "0.2",
    val id: String = "default",
    val locale: String = "en-US",
    val personality: PersonalityConfig = PersonalityConfig(),
    val collaboration: CollaborationConfig = CollaborationConfig(),
    val knowledge: KnowledgeConfig = KnowledgeConfig(),
    val output: OutputConfig = OutputConfig(),
    val extensions: Map<String, Map<String, String>> = emptyMap()
)

@Serializable
data class PersonalityConfig(
    val base: String = "friendly", // default, professional, friendly, honest, whimsical, concise, cynical
    val intensity: Int? = 1, // 0, 1, 2, 3, null
    val modifiers: Map<String, Int?> = mapOf(
        "honest" to 1,
        "concise" to 1,
        "warm" to 1,
        "technical" to 0,
        "educational" to 0,
        "critical" to 0,
        "headingsAndLists" to 1,
        "emoji" to 0,
        "quickReplies" to 1,
        "whimsical" to 0,
        "cynical" to 0,
        "enthusiastic" to 0
    ),
    val adaptation: AdaptationConfig = AdaptationConfig()
)

@Serializable
data class AdaptationConfig(
    val followUserRegister: Boolean = true,
    val preserveRequestedArtifactStyle: Boolean = true,
    val reduceHumorInSeriousContexts: Boolean = true,
    val mirrorLanguage: Boolean = true,
    val allowCasualProfanity: Boolean = false
)

@Serializable
data class CollaborationConfig(
    val preamble: String = "multiStepOnly", // off, multiStepOnly, always
    val initiative: String = "balanced", // conservative, balanced, proactive
    val verification: String = "normal", // light, normal, strict
    val questionPolicy: String = "blockingOnly", // blockingOnly, materialAmbiguity, earlyAlignment
    val assumptionPolicy: String = "balanced", // cautious, balanced, decisive
    val answerFirst: Boolean = true,
    val plainChatIsDefault: Boolean = true,
    val respectExplicitTurnInstructions: Boolean = true,
    val avoidRoutinePraise: Boolean = true,
    val avoidRoutineFollowUpOffer: Boolean = true,
    val announceOnlyMaterialActions: Boolean = true,
    val reportPartialFailures: Boolean = true,
    val preferResultOverProcess: Boolean = true
)

@Serializable
data class KnowledgeConfig(
    val distinguishRawFromSynthesis: Boolean = true,
    val treatMemoryAsFallible: Boolean = true,
    val surfaceSourceConflicts: Boolean = true,
    val preferMaintainedSynthesisForOrientation: Boolean = true,
    val requireTraceableClaims: Boolean = false
)

@Serializable
data class OutputConfig(
    val defaultFormat: String = "prose",
    val maxHeadingDepth: Int? = 3,
    val preferShortParagraphs: Boolean = true,
    val tables: String = "whenUseful", // avoid, whenUseful, prefer
    val codeExamples: String = "runnable", // minimal, runnable, explanatory
    val citations: String = "platformDefault" // platformDefault, whenAvailable, requiredForExternalFacts
)

@Serializable
data class ProfileOverlay(
    val id: String = "custom-overlay",
    val name: String = "Custom Overlay",
    val description: String = "",
    val locale: String? = null,
    val personalityBase: String? = null,
    val personalityIntensity: Int? = null,
    val modifierOverrides: Map<String, Int?> = emptyMap(),
    val adaptationOverrides: Map<String, Boolean?> = emptyMap(),
    val preamble: String? = null,
    val initiative: String? = null,
    val verification: String? = null,
    val questionPolicy: String? = null,
    val assumptionPolicy: String? = null,
    val collabBoolOverrides: Map<String, Boolean?> = emptyMap(),
    val knowledgeOverrides: Map<String, Boolean?> = emptyMap(),
    val defaultFormat: String? = null,
    val maxHeadingDepth: Int? = null,
    val preferShortParagraphs: Boolean? = null,
    val tables: String? = null,
    val codeExamples: String? = null,
    val citations: String? = null,
    val customNote: String? = null
)
