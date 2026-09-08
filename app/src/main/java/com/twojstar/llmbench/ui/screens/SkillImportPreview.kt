package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.skills.AgentSkillManifest
import com.twojstar.llmbench.data.skills.AgentSkillManifestParser
import com.twojstar.llmbench.data.skills.AgentSkillValidationIssue

internal data class SkillImportPreview(
    val displayName: String,
    val source: String,
    val manifest: AgentSkillManifest?,
    val issues: List<AgentSkillValidationIssue>
) {
    val isValid: Boolean
        get() = manifest != null && issues.isEmpty()
}

internal fun buildSkillImportPreview(displayName: String, source: String): SkillImportPreview {
    val parsed = AgentSkillManifestParser.parse(source)
    return SkillImportPreview(
        displayName = displayName,
        source = source,
        manifest = parsed.manifest,
        issues = parsed.issues
    )
}
