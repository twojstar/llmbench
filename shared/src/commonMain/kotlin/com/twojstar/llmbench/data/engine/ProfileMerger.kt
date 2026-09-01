package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.*

object ProfileMerger {
    /**
     * Merges a base Profile with an Overlay according to .ai semantics:
     * - Mappings merge recursively
     * - Scalars replace base values
     * - Null in overlay explicitly clears or keeps defaults
     */
    fun merge(base: Profile, overlay: ProfileOverlay): Profile {
        val mergedId = overlay.id.ifBlank { base.id }
        val mergedLocale = overlay.locale ?: base.locale

        // Merge personality
        val basePersonality = base.personality
        val mergedBaseVoice = overlay.personalityBase ?: basePersonality.base
        val mergedIntensity = overlay.personalityIntensity ?: basePersonality.intensity

        val mergedModifiers = basePersonality.modifiers.toMutableMap()
        overlay.modifierOverrides.forEach { (key, value) ->
            if (value != null) {
                mergedModifiers[key] = value
            } else {
                mergedModifiers.remove(key)
            }
        }

        val baseAdaptation = basePersonality.adaptation
        val mergedAdaptation = AdaptationConfig(
            followUserRegister = overlay.adaptationOverrides["followUserRegister"] ?: baseAdaptation.followUserRegister,
            preserveRequestedArtifactStyle = overlay.adaptationOverrides["preserveRequestedArtifactStyle"] ?: baseAdaptation.preserveRequestedArtifactStyle,
            reduceHumorInSeriousContexts = overlay.adaptationOverrides["reduceHumorInSeriousContexts"] ?: baseAdaptation.reduceHumorInSeriousContexts,
            mirrorLanguage = overlay.adaptationOverrides["mirrorLanguage"] ?: baseAdaptation.mirrorLanguage,
            allowCasualProfanity = overlay.adaptationOverrides["allowCasualProfanity"] ?: baseAdaptation.allowCasualProfanity
        )

        val mergedPersonality = PersonalityConfig(
            base = mergedBaseVoice,
            intensity = mergedIntensity,
            modifiers = mergedModifiers,
            adaptation = mergedAdaptation
        )

        // Merge collaboration
        val baseCollab = base.collaboration
        val mergedCollab = CollaborationConfig(
            preamble = overlay.preamble ?: baseCollab.preamble,
            initiative = overlay.initiative ?: baseCollab.initiative,
            verification = overlay.verification ?: baseCollab.verification,
            questionPolicy = overlay.questionPolicy ?: baseCollab.questionPolicy,
            assumptionPolicy = overlay.assumptionPolicy ?: baseCollab.assumptionPolicy,
            answerFirst = overlay.collabBoolOverrides["answerFirst"] ?: baseCollab.answerFirst,
            plainChatIsDefault = overlay.collabBoolOverrides["plainChatIsDefault"] ?: baseCollab.plainChatIsDefault,
            respectExplicitTurnInstructions = overlay.collabBoolOverrides["respectExplicitTurnInstructions"] ?: baseCollab.respectExplicitTurnInstructions,
            avoidRoutinePraise = overlay.collabBoolOverrides["avoidRoutinePraise"] ?: baseCollab.avoidRoutinePraise,
            avoidRoutineFollowUpOffer = overlay.collabBoolOverrides["avoidRoutineFollowUpOffer"] ?: baseCollab.avoidRoutineFollowUpOffer,
            announceOnlyMaterialActions = overlay.collabBoolOverrides["announceOnlyMaterialActions"] ?: baseCollab.announceOnlyMaterialActions,
            reportPartialFailures = overlay.collabBoolOverrides["reportPartialFailures"] ?: baseCollab.reportPartialFailures,
            preferResultOverProcess = overlay.collabBoolOverrides["preferResultOverProcess"] ?: baseCollab.preferResultOverProcess
        )

        // Merge knowledge
        val baseKnowledge = base.knowledge
        val mergedKnowledge = KnowledgeConfig(
            distinguishRawFromSynthesis = overlay.knowledgeOverrides["distinguishRawFromSynthesis"] ?: baseKnowledge.distinguishRawFromSynthesis,
            treatMemoryAsFallible = overlay.knowledgeOverrides["treatMemoryAsFallible"] ?: baseKnowledge.treatMemoryAsFallible,
            surfaceSourceConflicts = overlay.knowledgeOverrides["surfaceSourceConflicts"] ?: baseKnowledge.surfaceSourceConflicts,
            preferMaintainedSynthesisForOrientation = overlay.knowledgeOverrides["preferMaintainedSynthesisForOrientation"] ?: baseKnowledge.preferMaintainedSynthesisForOrientation,
            requireTraceableClaims = overlay.knowledgeOverrides["requireTraceableClaims"] ?: baseKnowledge.requireTraceableClaims
        )

        // Merge output
        val baseOutput = base.output
        val mergedOutput = OutputConfig(
            defaultFormat = overlay.defaultFormat ?: baseOutput.defaultFormat,
            maxHeadingDepth = overlay.maxHeadingDepth ?: baseOutput.maxHeadingDepth,
            preferShortParagraphs = overlay.preferShortParagraphs ?: baseOutput.preferShortParagraphs,
            tables = overlay.tables ?: baseOutput.tables,
            codeExamples = overlay.codeExamples ?: baseOutput.codeExamples,
            citations = overlay.citations ?: baseOutput.citations
        )

        // Extensions
        val mergedExtensions = base.extensions.toMutableMap()
        if (!overlay.customNote.isNullOrBlank()) {
            val local = mergedExtensions["local"].orEmpty().toMutableMap()
            local["note"] = overlay.customNote
            mergedExtensions["local"] = local
        }

        return Profile(
            schemaVersion = base.schemaVersion,
            id = mergedId,
            locale = mergedLocale,
            personality = mergedPersonality,
            collaboration = mergedCollab,
            knowledge = mergedKnowledge,
            output = mergedOutput,
            extensions = mergedExtensions
        )
    }

    /**
     * Validates if a Profile complies with schema rules
     */
    fun validate(profile: Profile): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (profile.schemaVersion != "0.2") {
            warnings.add("Schema version '${profile.schemaVersion}' may not be fully supported (expected '0.2').")
        }
        if (profile.id.isBlank()) {
            errors.add("Profile ID cannot be blank.")
        }
        if (profile.personality.intensity !in listOf(0, 1, 2, 3, null)) {
            errors.add("Personality intensity must be between 0 and 3, or null.")
        }
        profile.personality.modifiers.forEach { (name, value) ->
            if (value != null && value !in 0..3) {
                errors.add("Modifier '$name' level must be between 0 and 3 (got $value).")
            }
        }
        if (profile.output.maxHeadingDepth != null && profile.output.maxHeadingDepth !in 1..6) {
            errors.add("maxHeadingDepth must be between 1 and 6.")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
