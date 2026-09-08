package com.twojstar.llmbench.data.skills

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
 * Parses the portable Agent Skills `SKILL.md` envelope without executing any bundled content.
 *
 * The parser intentionally handles the small YAML subset used by skill metadata instead of
 * treating imported Markdown as executable configuration. Unknown scalar frontmatter is kept
 * for forward compatibility, while nested `metadata` is exposed as plain strings.
 */
object AgentSkillManifestParser {
    private const val FRONTMATTER_DELIMITER = "---"
    private const val MAX_NAME_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 1_024
    private const val MAX_COMPATIBILITY_LENGTH = 500

    private val validName = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val topLevelField = Regex("^([A-Za-z0-9_-]+):(?:[ \\t]*(.*))?$")
    private val nestedField = Regex("^[ \\t]+([A-Za-z0-9_.-]+):(?:[ \\t]*(.*))?$")

    fun parse(source: String, directoryName: String? = null): AgentSkillParseResult {
        val normalized = source
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')
        val issues = mutableListOf<AgentSkillValidationIssue>()

        if (lines.firstOrNull()?.trim() != FRONTMATTER_DELIMITER) {
            return invalid("SKILL.md must start with YAML frontmatter")
        }

        val closingIndex = lines.indexOfFirstAfter(0) { it.trim() == FRONTMATTER_DELIMITER }
        if (closingIndex < 0) {
            return invalid("SKILL.md frontmatter is missing its closing --- delimiter")
        }

        val parsed = parseFrontmatter(lines.subList(1, closingIndex), issues)
        val name = parsed.scalars["name"]?.trim().orEmpty()
        val description = parsed.scalars["description"]?.trim().orEmpty()

        validateName(name, directoryName, issues)
        validateDescription(description, issues)
        parsed.scalars["compatibility"]?.let { compatibility ->
            if (compatibility.length > MAX_COMPATIBILITY_LENGTH) {
                issues += AgentSkillValidationIssue(
                    field = "compatibility",
                    message = "compatibility must be at most $MAX_COMPATIBILITY_LENGTH characters"
                )
            }
        }

        if (issues.isNotEmpty()) {
            return AgentSkillParseResult(manifest = null, issues = issues)
        }

        val knownFields = setOf(
            "name", "description", "license", "compatibility", "metadata", "allowed-tools"
        )
        val instructions = lines.drop(closingIndex + 1)
            .joinToString("\n")
            .trimStart('\n')

        return AgentSkillParseResult(
            manifest = AgentSkillManifest(
                name = name,
                description = description,
                instructions = instructions,
                license = parsed.scalars["license"]?.takeIf(String::isNotBlank),
                compatibility = parsed.scalars["compatibility"]?.takeIf(String::isNotBlank),
                metadata = parsed.metadata,
                allowedTools = parsed.scalars["allowed-tools"]?.takeIf(String::isNotBlank),
                extraFrontmatter = parsed.scalars.filterKeys { it !in knownFields }
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
            name.isBlank() -> issues += AgentSkillValidationIssue("name", "name is required")
            name.length > MAX_NAME_LENGTH -> issues += AgentSkillValidationIssue(
                "name",
                "name must be at most $MAX_NAME_LENGTH characters"
            )
            !validName.matches(name) -> issues += AgentSkillValidationIssue(
                "name",
                "name must use lowercase letters, numbers and single hyphens only"
            )
        }
        val expectedDirectory = directoryName?.trim()?.takeIf { it.isNotEmpty() }
        if (name.isNotBlank() && expectedDirectory != null && name != expectedDirectory) {
            issues += AgentSkillValidationIssue(
                "name",
                "name must match the parent skill directory '$expectedDirectory'"
            )
        }
    }

    private fun validateDescription(
        description: String,
        issues: MutableList<AgentSkillValidationIssue>
    ) {
        when {
            description.isBlank() -> issues += AgentSkillValidationIssue(
                "description",
                "description is required"
            )
            description.length > MAX_DESCRIPTION_LENGTH -> issues += AgentSkillValidationIssue(
                "description",
                "description must be at most $MAX_DESCRIPTION_LENGTH characters"
            )
        }
    }

    private data class ParsedFrontmatter(
        val scalars: Map<String, String>,
        val metadata: Map<String, String>
    )

    private fun parseFrontmatter(
        lines: List<String>,
        issues: MutableList<AgentSkillValidationIssue>
    ): ParsedFrontmatter {
        val scalars = linkedMapOf<String, String>()
        val metadata = linkedMapOf<String, String>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith('#')) {
                index++
                continue
            }
            if (line.firstOrNull()?.isWhitespace() == true) {
                issues += AgentSkillValidationIssue(
                    field = null,
                    message = "Unexpected nested frontmatter at line ${index + 2}"
                )
                index++
                continue
            }

            val match = topLevelField.matchEntire(line)
            if (match == null) {
                issues += AgentSkillValidationIssue(
                    field = null,
                    message = "Malformed frontmatter at line ${index + 2}"
                )
                index++
                continue
            }

            val key = match.groupValues[1]
            val rawValue = match.groupValues[2].trim()
            if (key in scalars || key == "metadata" && metadata.isNotEmpty()) {
                issues += AgentSkillValidationIssue(key, "Duplicate frontmatter field '$key'")
                index++
                continue
            }

            if (key == "metadata") {
                if (rawValue == "{}") {
                    index++
                    continue
                }
                if (rawValue.isNotEmpty()) {
                    issues += AgentSkillValidationIssue(
                        "metadata",
                        "metadata must be a YAML mapping"
                    )
                    index++
                    continue
                }
                index = parseMetadata(lines, index + 1, metadata, issues)
                continue
            }

            if (rawValue.isBlockScalarIndicator()) {
                val block = collectIndentedBlock(lines, index + 1)
                scalars[key] = decodeBlockScalar(rawValue, block.lines)
                index = block.nextIndex
                continue
            }

            scalars[key] = decodeScalar(rawValue, key, issues)
            index++
        }

        return ParsedFrontmatter(scalars = scalars, metadata = metadata)
    }

    private fun parseMetadata(
        lines: List<String>,
        startIndex: Int,
        metadata: MutableMap<String, String>,
        issues: MutableList<AgentSkillValidationIssue>
    ): Int {
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }
            if (line.firstOrNull()?.isWhitespace() != true) break

            val match = nestedField.matchEntire(line)
            if (match == null) {
                issues += AgentSkillValidationIssue(
                    "metadata",
                    "Malformed metadata entry at line ${index + 2}"
                )
                index++
                continue
            }
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2].trim()
            if (key in metadata) {
                issues += AgentSkillValidationIssue("metadata.$key", "Duplicate metadata key '$key'")
            } else if (rawValue.isBlockScalarIndicator()) {
                val block = collectIndentedBlock(lines, index + 1, minimumIndent = leadingWhitespace(line) + 1)
                metadata[key] = decodeBlockScalar(rawValue, block.lines)
                index = block.nextIndex
                continue
            } else {
                metadata[key] = decodeScalar(rawValue, "metadata.$key", issues)
            }
            index++
        }
        return index
    }

    private data class IndentedBlock(
        val lines: List<String>,
        val nextIndex: Int
    )

    private fun collectIndentedBlock(
        lines: List<String>,
        startIndex: Int,
        minimumIndent: Int = 1
    ): IndentedBlock {
        var index = startIndex
        val rawLines = mutableListOf<String>()
        while (index < lines.size) {
            val line = lines[index]
            if (line.isNotBlank() && leadingWhitespace(line) < minimumIndent) break
            if (line.isNotBlank() && line.firstOrNull()?.isWhitespace() != true) break
            rawLines += line
            index++
        }

        val indent = rawLines
            .filter(String::isNotBlank)
            .minOfOrNull(::leadingWhitespace)
            ?: 0
        val normalized = rawLines.map { line ->
            if (line.isBlank()) "" else line.drop(indent.coerceAtMost(line.length))
        }
        return IndentedBlock(normalized, index)
    }

    private fun decodeBlockScalar(indicator: String, lines: List<String>): String {
        val folded = indicator.startsWith('>')
        val keepTrailing = indicator.endsWith('+')
        val stripTrailing = indicator.endsWith('-')
        val body = if (folded) foldLines(lines) else lines.joinToString("\n")
        return when {
            keepTrailing -> body + "\n"
            stripTrailing -> body.trimEnd('\n')
            body.isEmpty() -> body
            else -> body.trimEnd('\n') + "\n"
        }
    }

    private fun foldLines(lines: List<String>): String = buildString {
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                val previous = lines[index - 1]
                append(if (previous.isBlank() || line.isBlank()) '\n' else ' ')
            }
            append(line)
        }
    }

    private fun decodeScalar(
        rawValue: String,
        field: String,
        issues: MutableList<AgentSkillValidationIssue>
    ): String {
        if (rawValue.length >= 2 && rawValue.startsWith('\'') && rawValue.endsWith('\'')) {
            return rawValue.substring(1, rawValue.length - 1).replace("''", "'")
        }
        if (rawValue.startsWith('"')) {
            if (rawValue.length < 2 || !rawValue.endsWith('"')) {
                issues += AgentSkillValidationIssue(field, "Unterminated quoted scalar")
                return rawValue.removePrefix("\"")
            }
            return decodeDoubleQuoted(rawValue.substring(1, rawValue.length - 1), field, issues)
        }
        return rawValue
    }

    private fun decodeDoubleQuoted(
        value: String,
        field: String,
        issues: MutableList<AgentSkillValidationIssue>
    ): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\') {
                append(char)
                index++
                continue
            }
            if (index + 1 >= value.length) {
                issues += AgentSkillValidationIssue(field, "Trailing escape in quoted scalar")
                append('\\')
                break
            }
            when (val escaped = value[index + 1]) {
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                '"' -> append('"')
                '\\' -> append('\\')
                else -> {
                    issues += AgentSkillValidationIssue(field, "Unsupported YAML escape \\$escaped")
                    append(escaped)
                }
            }
            index += 2
        }
    }

    private fun String.isBlockScalarIndicator(): Boolean =
        this == "|" || this == "|-" || this == "|+" ||
            this == ">" || this == ">-" || this == ">+"

    private fun leadingWhitespace(value: String): Int =
        value.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) value.length else it }

    private fun <T> List<T>.indexOfFirstAfter(startIndex: Int, predicate: (T) -> Boolean): Int {
        for (index in startIndex + 1 until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    private fun invalid(message: String): AgentSkillParseResult = AgentSkillParseResult(
        manifest = null,
        issues = listOf(AgentSkillValidationIssue(field = null, message = message))
    )
}
