package com.example.aiprofilestudio.data.engine

import com.example.aiprofilestudio.data.model.Profile
import com.example.aiprofilestudio.data.model.ProfileOverlay

object YamlParser {

    /**
     * Serializes a Profile into clean YAML conforming to style-profile.schema.json
     */
    fun dumpProfile(profile: Profile): String {
        val sb = StringBuilder()
        sb.appendLine("# yaml-language-server: \$schema=../schema/style-profile.schema.json")
        sb.appendLine("schemaVersion: \"${profile.schemaVersion}\"")
        sb.appendLine("id: ${profile.id}")
        sb.appendLine("locale: ${profile.locale}")
        sb.appendLine()
        sb.appendLine("personality:")
        sb.appendLine("  base: ${profile.personality.base}")
        sb.appendLine("  intensity: ${profile.personality.intensity ?: "null"}")
        sb.appendLine("  modifiers:")
        profile.personality.modifiers.forEach { (k, v) ->
            sb.appendLine("    $k: ${v ?: "null"}")
        }
        sb.appendLine("  adaptation:")
        sb.appendLine("    followUserRegister: ${profile.personality.adaptation.followUserRegister}")
        sb.appendLine("    preserveRequestedArtifactStyle: ${profile.personality.adaptation.preserveRequestedArtifactStyle}")
        sb.appendLine("    reduceHumorInSeriousContexts: ${profile.personality.adaptation.reduceHumorInSeriousContexts}")
        sb.appendLine("    mirrorLanguage: ${profile.personality.adaptation.mirrorLanguage}")
        sb.appendLine("    allowCasualProfanity: ${profile.personality.adaptation.allowCasualProfanity}")
        sb.appendLine()
        sb.appendLine("collaboration:")
        sb.appendLine("  preamble: ${profile.collaboration.preamble}")
        sb.appendLine("  initiative: ${profile.collaboration.initiative}")
        sb.appendLine("  verification: ${profile.collaboration.verification}")
        sb.appendLine("  questionPolicy: ${profile.collaboration.questionPolicy}")
        sb.appendLine("  assumptionPolicy: ${profile.collaboration.assumptionPolicy}")
        sb.appendLine("  answerFirst: ${profile.collaboration.answerFirst}")
        sb.appendLine("  plainChatIsDefault: ${profile.collaboration.plainChatIsDefault}")
        sb.appendLine("  respectExplicitTurnInstructions: ${profile.collaboration.respectExplicitTurnInstructions}")
        sb.appendLine("  avoidRoutinePraise: ${profile.collaboration.avoidRoutinePraise}")
        sb.appendLine("  avoidRoutineFollowUpOffer: ${profile.collaboration.avoidRoutineFollowUpOffer}")
        sb.appendLine("  announceOnlyMaterialActions: ${profile.collaboration.announceOnlyMaterialActions}")
        sb.appendLine("  reportPartialFailures: ${profile.collaboration.reportPartialFailures}")
        sb.appendLine("  preferResultOverProcess: ${profile.collaboration.preferResultOverProcess}")
        sb.appendLine()
        sb.appendLine("knowledge:")
        sb.appendLine("  distinguishRawFromSynthesis: ${profile.knowledge.distinguishRawFromSynthesis}")
        sb.appendLine("  treatMemoryAsFallible: ${profile.knowledge.treatMemoryAsFallible}")
        sb.appendLine("  surfaceSourceConflicts: ${profile.knowledge.surfaceSourceConflicts}")
        sb.appendLine("  preferMaintainedSynthesisForOrientation: ${profile.knowledge.preferMaintainedSynthesisForOrientation}")
        sb.appendLine("  requireTraceableClaims: ${profile.knowledge.requireTraceableClaims}")
        sb.appendLine()
        sb.appendLine("output:")
        sb.appendLine("  defaultFormat: ${profile.output.defaultFormat}")
        sb.appendLine("  maxHeadingDepth: ${profile.output.maxHeadingDepth ?: "null"}")
        sb.appendLine("  preferShortParagraphs: ${profile.output.preferShortParagraphs}")
        sb.appendLine("  tables: ${profile.output.tables}")
        sb.appendLine("  codeExamples: ${profile.output.codeExamples}")
        sb.appendLine("  citations: ${profile.output.citations}")

        if (profile.extensions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("extensions:")
            profile.extensions.forEach { (extKey, extMap) ->
                sb.appendLine("  $extKey:")
                extMap.forEach { (k, v) ->
                    sb.appendLine("    $k: \"$v\"")
                }
            }
        }

        return sb.toString()
    }

    /**
     * Serializes an Overlay into YAML (partial overlay format)
     */
    fun dumpOverlay(overlay: ProfileOverlay): String {
        val sb = StringBuilder()
        sb.appendLine("# Partial overlay. It is intentionally not a complete standalone profile.")
        sb.appendLine("id: ${overlay.id}")
        if (overlay.locale != null) sb.appendLine("locale: ${overlay.locale}")
        sb.appendLine()

        if (overlay.personalityBase != null || overlay.personalityIntensity != null || overlay.modifierOverrides.isNotEmpty() || overlay.adaptationOverrides.isNotEmpty()) {
            sb.appendLine("personality:")
            if (overlay.personalityBase != null) sb.appendLine("  base: ${overlay.personalityBase}")
            if (overlay.personalityIntensity != null) sb.appendLine("  intensity: ${overlay.personalityIntensity}")
            if (overlay.modifierOverrides.isNotEmpty()) {
                sb.appendLine("  modifiers:")
                overlay.modifierOverrides.forEach { (k, v) ->
                    sb.appendLine("    $k: ${v ?: "null"}")
                }
            }
            if (overlay.adaptationOverrides.isNotEmpty()) {
                sb.appendLine("  adaptation:")
                overlay.adaptationOverrides.forEach { (k, v) ->
                    sb.appendLine("    $k: $v")
                }
            }
        }

        val hasCollab = overlay.preamble != null || overlay.initiative != null || overlay.verification != null ||
                overlay.questionPolicy != null || overlay.assumptionPolicy != null || overlay.collabBoolOverrides.isNotEmpty()

        if (hasCollab) {
            sb.appendLine()
            sb.appendLine("collaboration:")
            if (overlay.preamble != null) sb.appendLine("  preamble: ${overlay.preamble}")
            if (overlay.initiative != null) sb.appendLine("  initiative: ${overlay.initiative}")
            if (overlay.verification != null) sb.appendLine("  verification: ${overlay.verification}")
            if (overlay.questionPolicy != null) sb.appendLine("  questionPolicy: ${overlay.questionPolicy}")
            if (overlay.assumptionPolicy != null) sb.appendLine("  assumptionPolicy: ${overlay.assumptionPolicy}")
            overlay.collabBoolOverrides.forEach { (k, v) ->
                sb.appendLine("  $k: $v")
            }
        }

        if (overlay.customNote != null) {
            sb.appendLine()
            sb.appendLine("extensions:")
            sb.appendLine("  local:")
            sb.appendLine("    note: \"${overlay.customNote}\"")
        }

        return sb.toString()
    }
}
