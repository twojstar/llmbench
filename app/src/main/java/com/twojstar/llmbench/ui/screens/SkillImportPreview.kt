package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.skills.AgentSkillManifest
import com.twojstar.llmbench.data.skills.AgentSkillManifestParser
import com.twojstar.llmbench.data.skills.AgentSkillValidationIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SOURCE_PREVIEW_CHARS = 24 * 1024
private val PORTABLE_SKILL_FILENAMES = setOf("SKILL.md", "skill.md")

internal data class SkillImportPreview(
    val displayName: String,
    val source: String,
    val manifest: AgentSkillManifest?,
    val issues: List<AgentSkillValidationIssue>
) {
    val isValid: Boolean
        get() = manifest != null && issues.isEmpty()

    fun sourceForDisplay(): String {
        if (source.length <= MAX_SOURCE_PREVIEW_CHARS) return source
        return source.take(MAX_SOURCE_PREVIEW_CHARS) +
            "\n\n… source preview truncated; the selected file remains unchanged …"
    }
}

internal suspend fun buildSkillImportPreview(
    displayName: String,
    source: String,
    validateFilename: Boolean = true
): SkillImportPreview = withContext(Dispatchers.Default) {
    val parsed = AgentSkillManifestParser.parse(source)
    val issues = parsed.issues.toMutableList()
    if (validateFilename && displayName !in PORTABLE_SKILL_FILENAMES) {
        issues += AgentSkillValidationIssue(
            field = null,
            message = "Portable skills must be named SKILL.md (skill.md is also accepted)."
        )
    }
    SkillImportPreview(
        displayName = displayName,
        source = source,
        manifest = parsed.manifest,
        issues = issues
    )
}
