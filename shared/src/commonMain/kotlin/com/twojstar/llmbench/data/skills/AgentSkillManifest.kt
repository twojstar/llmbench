package com.twojstar.llmbench.data.skills

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar

data class AgentSkillManifest(
    val name: String,
    val description: String,
    val instructions: String,
    val license: String? = null,
    val compatibility: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val allowedTools: String? = null,
    val extraFrontmatter: Map<String, String> = emptyMap()
)

data class AgentSkillValidationIssue(
    val field: String?,
    val message: String
)

data class AgentSkillParseResult(
    val manifest: AgentSkillManifest?,
    val issues: List<AgentSkillValidationIssue>
) {
    val isValid: Boolean
        get() = manifest != null && issues.isEmpty()
}

/**
 * Parses the portable Agent Skills `SKILL.md` envelope without executing imported content.
 *
 * YAML syntax and scalar semantics are delegated to Kotaml. This layer only identifies the
 * Markdown frontmatter boundary, validates Agent Skills fields and maps supported metadata into
 * the read-only preview model.
 */
object AgentSkillManifestParser {
    private const val FRONTMATTER_DELIMITER = "---"
    private const val FIELD_NAME = "name"
    private const val FIELD_DESCRIPTION = "description"
    private const val FIELD_LICENSE = "license"
    private const val FIELD_COMPATIBILITY = "compatibility"
    private const val FIELD_METADATA = "metadata"
    private const val FIELD_ALLOWED_TOOLS = "allowed-tools"
    private const val MAX_NAME_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 1_024
    private const val MAX_COMPATIBILITY_LENGTH = 500

    private val validName = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val knownFields = setOf(
        FIELD_NAME,
        FIELD_DESCRIPTION,
        FIELD_LICENSE,
        FIELD_COMPATIBILITY,
        FIELD_METADATA,
        FIELD_ALLOWED_TOOLS
    )

    fun parse(source: String, directoryName: String? = null): AgentSkillParseResult {
        val normalized = source
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')

        if (lines.firstOrNull() != FRONTMATTER_DELIMITER) {
            return invalid("SKILL.md must start with YAML frontmatter")
        }

        val closingIndex = (1 until lines.size)
            .firstOrNull { lines[it] == FRONTMATTER_DELIMITER }
            ?: return invalid("SKILL.md frontmatter is missing its closing --- delimiter")
        val yamlSource = lines.subList(1, closingIndex).joinToString("\n")
        val root = try {
            Yaml.default.parseToYamlNode(yamlSource)
        } catch (error: YamlException) {
            return invalid("Invalid YAML frontmatter: ${error.message}")
        }
        val frontmatter = root as? YamlMap
            ?: return invalid("SKILL.md frontmatter must be a YAML mapping")
        val issues = mutableListOf<AgentSkillValidationIssue>()

        val name = frontmatter.scalarValue(FIELD_NAME, issues)
        val description = frontmatter.scalarValue(FIELD_DESCRIPTION, issues)
        val license = frontmatter.scalarValue(FIELD_LICENSE, issues)
        val compatibility = frontmatter.scalarValue(FIELD_COMPATIBILITY, issues)
        val allowedTools = frontmatter.scalarValue(FIELD_ALLOWED_TOOLS, issues)
        val metadata = frontmatter.metadataValues(issues)

        if (!frontmatter.containsField(FIELD_NAME)) {
            issues += AgentSkillValidationIssue(FIELD_NAME, "$FIELD_NAME is required")
        } else if (name != null) {
            validateName(name, directoryName, issues)
        }

        if (!frontmatter.containsField(FIELD_DESCRIPTION)) {
            issues += AgentSkillValidationIssue(
                FIELD_DESCRIPTION,
                "$FIELD_DESCRIPTION is required"
            )
        } else if (description != null) {
            validateDescription(description, issues)
        }

        compatibility?.let { value ->
            if (value.length > MAX_COMPATIBILITY_LENGTH) {
                issues += AgentSkillValidationIssue(
                    field = FIELD_COMPATIBILITY,
                    message = "$FIELD_COMPATIBILITY must be at most $MAX_COMPATIBILITY_LENGTH characters"
                )
            }
        }

        if (issues.isNotEmpty() || name == null || description == null) {
            return AgentSkillParseResult(manifest = null, issues = issues)
        }

        val extraFrontmatter = linkedMapOf<String, String>()
        frontmatter.entries.forEach { (key, value) ->
            if (key.content !in knownFields && value is YamlScalar) {
                extraFrontmatter[key.content] = value.content
            }
        }
        val instructions = lines.drop(closingIndex + 1)
            .joinToString("\n")
            .trimStart('\n')

        return AgentSkillParseResult(
            manifest = AgentSkillManifest(
                name = name,
                description = description,
                instructions = instructions,
                license = license?.takeIf(String::isNotBlank),
                compatibility = compatibility?.takeIf(String::isNotBlank),
                metadata = metadata,
                allowedTools = allowedTools?.takeIf(String::isNotBlank),
                extraFrontmatter = extraFrontmatter
            ),
            issues = emptyList()
        )
    }

    private fun validateName(
        name: String,
        directoryName: String?,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        when {
            name.isBlank() -> issues += AgentSkillValidationIssue(FIELD_NAME, "$FIELD_NAME is required")
            name.length > MAX_NAME_LENGTH -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME must be at most $MAX_NAME_LENGTH characters"
            )
            !validName.matches(name) -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME must use lowercase letters, numbers and single hyphens only"
            )
        }

        directoryName?.trim()?.takeIf(String::isNotEmpty)?.let { expectedDirectory ->
            if (name.isNotBlank() && name != expectedDirectory) {
                issues += AgentSkillValidationIssue(
                    FIELD_NAME,
                    "$FIELD_NAME must match the parent skill directory '$expectedDirectory'"
                )
            }
        }
    }

    private fun validateDescription(
        description: String,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        when {
            description.isBlank() -> issues += AgentSkillValidationIssue(
                FIELD_DESCRIPTION,
                "$FIELD_DESCRIPTION is required"
            )
            description.length > MAX_DESCRIPTION_LENGTH -> issues += AgentSkillValidationIssue(
                FIELD_DESCRIPTION,
                "$FIELD_DESCRIPTION must be at most $MAX_DESCRIPTION_LENGTH characters"
            )
        }
    }

    private fun YamlMap.containsField(field: String): Boolean =
        entries.keys.any { it.content == field }

    private fun YamlMap.valueFor(field: String): YamlNode? =
        entries.entries.firstOrNull { it.key.content == field }?.value

    private fun YamlMap.scalarValue(
        field: String,
        issues: MutableList<AgentSkillValidationIssue>
    ): String? {
        val value = valueFor(field) ?: return null
        if (value is YamlScalar) return value.content

        issues += AgentSkillValidationIssue(field, "$field must be a YAML scalar")
        return null
    }

    private fun YamlMap.metadataValues(
        issues: MutableList<AgentSkillValidationIssue>
    ): Map<String, String> {
        val value = valueFor(FIELD_METADATA) ?: return emptyMap()
        if (value !is YamlMap) {
            issues += AgentSkillValidationIssue(
                FIELD_METADATA,
                "$FIELD_METADATA must be a YAML mapping"
            )
            return emptyMap()
        }

        val metadata = linkedMapOf<String, String>()
        value.entries.forEach { (key, entry) ->
            if (entry is YamlScalar) {
                metadata[key.content] = entry.content
            } else {
                issues += AgentSkillValidationIssue(
                    "$FIELD_METADATA.${key.content}",
                    "$FIELD_METADATA values must be YAML scalars"
                )
            }
        }
        return metadata
    }

    private fun invalid(message: String): AgentSkillParseResult = AgentSkillParseResult(
        manifest = null,
        issues = listOf(AgentSkillValidationIssue(field = null, message = message))
    )
}
