package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay

object YamlParser {
    private fun yamlQuote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }

    private fun StringBuilder.textLine(key: String, value: String, indent: Int = 0) {
        append(" ".repeat(indent)).append(key).append(": ").appendLine(yamlQuote(value))
    }

    private fun StringBuilder.scalarLine(key: String, value: Any?, indent: Int = 0) {
        append(" ".repeat(indent)).append(key).append(": ")
            .appendLine(value?.toString() ?: "null")
    }

    /** Serializes a Profile into YAML conforming to style-profile.schema.json. */
    fun dumpProfile(profile: Profile): String {
        val sb = StringBuilder()
        sb.appendLine("# yaml-language-server: \$schema=https://raw.githubusercontent.com/trvny/.ai/main/schema/style-profile.schema.json")
        sb.textLine("schemaVersion", profile.schemaVersion)
        sb.textLine("id", profile.id)
        sb.textLine("locale", profile.locale)
        sb.appendLine()
        sb.appendLine("personality:")
        sb.textLine("base", profile.personality.base, 2)
        sb.scalarLine("intensity", profile.personality.intensity, 2)
        sb.appendLine("  modifiers:")
        profile.personality.modifiers.forEach { (key, value) ->
            sb.scalarLine(yamlQuote(key), value, 4)
        }
        sb.appendLine("  adaptation:")
        val adaptation = profile.personality.adaptation
        sb.scalarLine("followUserRegister", adaptation.followUserRegister, 4)
        sb.scalarLine("preserveRequestedArtifactStyle", adaptation.preserveRequestedArtifactStyle, 4)
        sb.scalarLine("reduceHumorInSeriousContexts", adaptation.reduceHumorInSeriousContexts, 4)
        sb.scalarLine("mirrorLanguage", adaptation.mirrorLanguage, 4)
        sb.scalarLine("allowCasualProfanity", adaptation.allowCasualProfanity, 4)
        sb.appendLine()
        sb.appendLine("collaboration:")
        val collaboration = profile.collaboration
        sb.textLine("preamble", collaboration.preamble, 2)
        sb.textLine("initiative", collaboration.initiative, 2)
        sb.textLine("verification", collaboration.verification, 2)
        sb.textLine("questionPolicy", collaboration.questionPolicy, 2)
        sb.textLine("assumptionPolicy", collaboration.assumptionPolicy, 2)
        sb.scalarLine("answerFirst", collaboration.answerFirst, 2)
        sb.scalarLine("plainChatIsDefault", collaboration.plainChatIsDefault, 2)
        sb.scalarLine("respectExplicitTurnInstructions", collaboration.respectExplicitTurnInstructions, 2)
        sb.scalarLine("avoidRoutinePraise", collaboration.avoidRoutinePraise, 2)
        sb.scalarLine("avoidRoutineFollowUpOffer", collaboration.avoidRoutineFollowUpOffer, 2)
        sb.scalarLine("announceOnlyMaterialActions", collaboration.announceOnlyMaterialActions, 2)
        sb.scalarLine("reportPartialFailures", collaboration.reportPartialFailures, 2)
        sb.scalarLine("preferResultOverProcess", collaboration.preferResultOverProcess, 2)
        sb.appendLine()
        sb.appendLine("knowledge:")
        val knowledge = profile.knowledge
        sb.scalarLine("distinguishRawFromSynthesis", knowledge.distinguishRawFromSynthesis, 2)
        sb.scalarLine("treatMemoryAsFallible", knowledge.treatMemoryAsFallible, 2)
        sb.scalarLine("surfaceSourceConflicts", knowledge.surfaceSourceConflicts, 2)
        sb.scalarLine("preferMaintainedSynthesisForOrientation", knowledge.preferMaintainedSynthesisForOrientation, 2)
        sb.scalarLine("requireTraceableClaims", knowledge.requireTraceableClaims, 2)
        sb.appendLine()
        sb.appendLine("output:")
        val output = profile.output
        sb.textLine("defaultFormat", output.defaultFormat, 2)
        sb.scalarLine("maxHeadingDepth", output.maxHeadingDepth, 2)
        sb.scalarLine("preferShortParagraphs", output.preferShortParagraphs, 2)
        sb.textLine("tables", output.tables, 2)
        sb.textLine("codeExamples", output.codeExamples, 2)
        sb.textLine("citations", output.citations, 2)

        if (profile.extensions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("extensions:")
            profile.extensions.forEach { (extension, values) ->
                sb.appendLine("  ${yamlQuote(extension)}:")
                values.forEach { (key, value) ->
                    sb.textLine(yamlQuote(key), value, 4)
                }
            }
        }
        return sb.toString()
    }

    /** Serializes an Overlay into YAML (partial overlay format). */
    fun dumpOverlay(overlay: ProfileOverlay): String = buildString {
        appendLine("# Partial overlay. It is intentionally not a complete standalone profile.")
        textLine("id", overlay.id)
        overlay.locale?.let { textLine("locale", it) }
        appendPersonalityOverlay(overlay)
        appendCollaborationOverlay(overlay)
        appendKnowledgeOverlay(overlay)
        appendOutputOverlay(overlay)
        appendExtensionOverlay(overlay)
    }

    private fun StringBuilder.appendPersonalityOverlay(overlay: ProfileOverlay) {
        val present = overlay.personalityBase != null || overlay.personalityIntensity != null ||
            overlay.modifierOverrides.isNotEmpty() || overlay.adaptationOverrides.isNotEmpty()
        if (!present) return

        appendLine()
        appendLine("personality:")
        overlay.personalityBase?.let { textLine("base", it, 2) }
        overlay.personalityIntensity?.let { scalarLine("intensity", it, 2) }
        if (overlay.modifierOverrides.isNotEmpty()) {
            appendLine("  modifiers:")
            overlay.modifierOverrides.forEach { (key, value) ->
                scalarLine(yamlQuote(key), value, 4)
            }
        }
        if (overlay.adaptationOverrides.isNotEmpty()) {
            appendLine("  adaptation:")
            overlay.adaptationOverrides.forEach { (key, value) ->
                scalarLine(yamlQuote(key), value, 4)
            }
        }
    }

    private fun StringBuilder.appendCollaborationOverlay(overlay: ProfileOverlay) {
        val present = overlay.preamble != null || overlay.initiative != null ||
            overlay.verification != null || overlay.questionPolicy != null ||
            overlay.assumptionPolicy != null || overlay.collabBoolOverrides.isNotEmpty()
        if (!present) return

        appendLine()
        appendLine("collaboration:")
        overlay.preamble?.let { textLine("preamble", it, 2) }
        overlay.initiative?.let { textLine("initiative", it, 2) }
        overlay.verification?.let { textLine("verification", it, 2) }
        overlay.questionPolicy?.let { textLine("questionPolicy", it, 2) }
        overlay.assumptionPolicy?.let { textLine("assumptionPolicy", it, 2) }
        overlay.collabBoolOverrides.forEach { (key, value) ->
            scalarLine(yamlQuote(key), value, 2)
        }
    }

    private fun StringBuilder.appendKnowledgeOverlay(overlay: ProfileOverlay) {
        if (overlay.knowledgeOverrides.isEmpty()) return

        appendLine()
        appendLine("knowledge:")
        overlay.knowledgeOverrides.forEach { (key, value) ->
            scalarLine(yamlQuote(key), value, 2)
        }
    }

    private fun StringBuilder.appendOutputOverlay(overlay: ProfileOverlay) {
        val present = overlay.defaultFormat != null || overlay.maxHeadingDepth != null ||
            overlay.preferShortParagraphs != null || overlay.tables != null ||
            overlay.codeExamples != null || overlay.citations != null
        if (!present) return

        appendLine()
        appendLine("output:")
        overlay.defaultFormat?.let { textLine("defaultFormat", it, 2) }
        overlay.maxHeadingDepth?.let { scalarLine("maxHeadingDepth", it, 2) }
        overlay.preferShortParagraphs?.let { scalarLine("preferShortParagraphs", it, 2) }
        overlay.tables?.let { textLine("tables", it, 2) }
        overlay.codeExamples?.let { textLine("codeExamples", it, 2) }
        overlay.citations?.let { textLine("citations", it, 2) }
    }

    private fun StringBuilder.appendExtensionOverlay(overlay: ProfileOverlay) {
        val note = overlay.customNote ?: return
        appendLine()
        appendLine("extensions:")
        appendLine("  local:")
        textLine("note", note, 4)
    }
}
