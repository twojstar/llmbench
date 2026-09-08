package com.twojstar.llmbench.data.skills

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import doist.x.normalize.Form
import doist.x.normalize.normalize

data class AgentSkillManifest(
    val name: String,
    val description: String,
    val instructions: String,
    val license: String? = null,
    val compatibility: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val allowedTools: String? = null
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

    private val unicodeNameCharacters = Regex("^[\\p{L}\\p{Nd}-]+$")
    private val knownFields = setOf(
        FIELD_NAME,
        FIELD_DESCRIPTION,
        FIELD_LICENSE,
        FIELD_COMPATIBILITY,
        FIELD_METADATA,
        FIELD_ALLOWED_TOOLS
    )

    fun parse(source: String, directoryName: String? = null): AgentSkillParseResult {
        val envelopeResult = extractEnvelope(source)
        val envelope = envelopeResult.envelope
            ?: return invalid(requireNotNull(envelopeResult.error))
        val yamlResult = parseFrontmatter(envelope.frontmatter)
        val frontmatter = yamlResult.frontmatter
            ?: return invalid(requireNotNull(yamlResult.error))

        return buildManifest(frontmatter, envelope.instructions, directoryName)
    }

    private fun buildManifest(
        frontmatter: YamlMap,
        instructions: String,
        directoryName: String?
    ): AgentSkillParseResult {
        val issues = mutableListOf<AgentSkillValidationIssue>()
        validateKnownFields(frontmatter, issues)

        val name = frontmatter.stringValue(FIELD_NAME, issues)
        val description = frontmatter.stringValue(FIELD_DESCRIPTION, issues)
        val license = frontmatter.stringValue(FIELD_LICENSE, issues)
        val compatibility = frontmatter.stringValue(FIELD_COMPATIBILITY, issues)
        val allowedTools = frontmatter.stringValue(FIELD_ALLOWED_TOOLS, issues)
        val metadata = frontmatter.metadataValues(issues)

        validateRequiredFields(frontmatter, name, description, directoryName, issues)
        validateCompatibility(compatibility, issues)

        if (issues.isNotEmpty() || name == null || description == null) {
            return AgentSkillParseResult(manifest = null, issues = issues)
        }

        return AgentSkillParseResult(
            manifest = AgentSkillManifest(
                name = canonicalSkillName(name),
                description = description,
                instructions = instructions,
                license = license?.takeIf(String::isNotBlank),
                compatibility = compatibility,
                metadata = metadata,
                allowedTools = allowedTools?.takeIf(String::isNotBlank)
            ),
            issues = emptyList()
        )
    }

    private fun validateRequiredFields(
        frontmatter: YamlMap,
        name: String?,
        description: String?,
        directoryName: String?,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
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
    }

    private fun validateKnownFields(
        frontmatter: YamlMap,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        frontmatter.entries.keys
            .map(YamlScalar::content)
            .filterNot(knownFields::contains)
            .forEach { field ->
                issues += AgentSkillValidationIssue(
                    field,
                    "Unexpected frontmatter field '$field'"
                )
            }
    }

    private fun validateName(
        name: String,
        directoryName: String?,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        val canonicalName = canonicalSkillName(name)
        when {
            canonicalName.isBlank() -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME is required"
            )
            canonicalName.codePointCount() > MAX_NAME_LENGTH -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME must be at most $MAX_NAME_LENGTH characters"
            )
            canonicalName != canonicalName.lowercase() -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME must be lowercase"
            )
            canonicalName.startsWith('-') || canonicalName.endsWith('-') ->
                issues += AgentSkillValidationIssue(
                    FIELD_NAME,
                    "$FIELD_NAME must not start or end with a hyphen"
                )
            "--" in canonicalName -> issues += AgentSkillValidationIssue(
                FIELD_NAME,
                "$FIELD_NAME must not contain consecutive hyphens"
            )
            !unicodeNameCharacters.matches(canonicalName) ->
                issues += AgentSkillValidationIssue(
                    FIELD_NAME,
                    "$FIELD_NAME may contain only Unicode letters, decimal digits and hyphens"
                )
        }

        directoryName?.takeIf(String::isNotEmpty)?.let { expectedDirectory ->
            val canonicalDirectory = expectedDirectory.normalize(Form.NFKC)
            if (canonicalName.isNotEmpty() && canonicalName != canonicalDirectory) {
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
            description.codePointCount() > MAX_DESCRIPTION_LENGTH ->
                issues += AgentSkillValidationIssue(
                    FIELD_DESCRIPTION,
                    "$FIELD_DESCRIPTION must be at most $MAX_DESCRIPTION_LENGTH characters"
                )
        }
    }

    private fun validateCompatibility(
        compatibility: String?,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        compatibility ?: return
        when {
            compatibility.isBlank() -> issues += AgentSkillValidationIssue(
                FIELD_COMPATIBILITY,
                "$FIELD_COMPATIBILITY must not be blank"
            )
            compatibility.codePointCount() > MAX_COMPATIBILITY_LENGTH ->
                issues += AgentSkillValidationIssue(
                    FIELD_COMPATIBILITY,
                    "$FIELD_COMPATIBILITY must be at most $MAX_COMPATIBILITY_LENGTH characters"
                )
        }
    }

    private fun canonicalSkillName(name: String): String = name.normalize(Form.NFKC)

    private fun String.codePointCount(): Int {
        var count = 0
        var index = 0
        while (index < length) {
            val current = this[index]
            index += if (
                current.isHighSurrogate() &&
                index + 1 < length &&
                this[index + 1].isLowSurrogate()
            ) {
                2
            } else {
                1
            }
            count += 1
        }
        return count
    }

    private fun YamlMap.containsField(field: String): Boolean =
        entries.keys.any { it.content == field }

    private fun YamlMap.valueFor(field: String): YamlNode? =
        entries.entries.firstOrNull { it.key.content == field }?.value

    private fun YamlMap.stringValue(
        field: String,
        issues: MutableList<AgentSkillValidationIssue>
    ): String? {
        val value = valueFor(field) ?: return null
        if (value is YamlScalar && value.representsYamlString()) return value.content

        issues += AgentSkillValidationIssue(field, "$field must be a YAML string")
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
            val field = "$FIELD_METADATA.${key.content}"
            when {
                !key.representsYamlString() -> issues += AgentSkillValidationIssue(
                    field,
                    "$FIELD_METADATA keys must be YAML strings"
                )
                entry is YamlScalar && entry.representsYamlString() -> {
                    metadata[key.content] = entry.content
                }
                else -> issues += AgentSkillValidationIssue(
                    field,
                    "$FIELD_METADATA values must be YAML strings"
                )
            }
        }
        return metadata
    }

    private fun YamlScalar.representsYamlString(): Boolean {
        if (!plain) return true
        if (content.equals("true", ignoreCase = true) || content.equals("false", ignoreCase = true)) {
            return false
        }
        if (runCatching { toLong() }.isSuccess) return false
        if (runCatching { toDouble() }.isSuccess) return false
        return true
    }

    private fun extractEnvelope(source: String): EnvelopeResult {
        val normalized = source
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')
        if (lines.firstOrNull() != FRONTMATTER_DELIMITER) {
            return EnvelopeResult(error = "SKILL.md must start with YAML frontmatter")
        }

        val closingIndex = (1 until lines.size)
            .firstOrNull { lines[it] == FRONTMATTER_DELIMITER }
            ?: return EnvelopeResult(
                error = "SKILL.md frontmatter is missing its closing --- delimiter"
            )

        return EnvelopeResult(
            envelope = SkillEnvelope(
                frontmatter = lines.subList(1, closingIndex).joinToString("\n"),
                instructions = lines.drop(closingIndex + 1)
                    .joinToString("\n")
                    .trimStart('\n')
            )
        )
    }

    private fun parseFrontmatter(source: String): FrontmatterResult = try {
        val root = Yaml.default.parseToYamlNode(source)
        val frontmatter = root as? YamlMap
        if (frontmatter == null) {
            FrontmatterResult(error = "SKILL.md frontmatter must be a YAML mapping")
        } else {
            FrontmatterResult(frontmatter = frontmatter)
        }
    } catch (error: YamlException) {
        FrontmatterResult(error = "Invalid YAML frontmatter: ${error.message}")
    }

    private fun invalid(message: String): AgentSkillParseResult = AgentSkillParseResult(
        manifest = null,
        issues = listOf(AgentSkillValidationIssue(field = null, message = message))
    )

    private data class SkillEnvelope(
        val frontmatter: String,
        val instructions: String
    )

    private data class EnvelopeResult(
        val envelope: SkillEnvelope? = null,
        val error: String? = null
    )

    private data class FrontmatterResult(
        val frontmatter: YamlMap? = null,
        val error: String? = null
    )
}
