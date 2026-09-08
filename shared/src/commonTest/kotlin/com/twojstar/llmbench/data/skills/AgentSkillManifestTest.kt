package com.twojstar.llmbench.data.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSkillManifestTest {
    @Test
    fun parsesPortableSkillManifest() {
        val source = """
            ---
            name: release-checklist
            description: Run release checks. Use before tagging or publishing a release.
            license: Apache-2.0
            compatibility: "Requires git and a POSIX shell"
            metadata:
              author: twojstar
              version: '1.0'
            allowed-tools: shell git
            ---

            # Release checklist

            1. Run tests.
            2. Verify the release artifact.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source, directoryName = "release-checklist")

        assertTrue(result.isValid)
        val manifest = requireNotNull(result.manifest)
        assertEquals("release-checklist", manifest.name)
        assertEquals(
            "Run release checks. Use before tagging or publishing a release.",
            manifest.description
        )
        assertEquals("Apache-2.0", manifest.license)
        assertEquals("Requires git and a POSIX shell", manifest.compatibility)
        assertEquals(mapOf("author" to "twojstar", "version" to "1.0"), manifest.metadata)
        assertEquals("shell git", manifest.allowedTools)
        assertTrue(manifest.instructions.startsWith("# Release checklist"))
    }

    @Test
    fun supportsFoldedDescription() {
        val source = """
            ---
            name: markdown-cleanup
            description: >-
              Normalize Markdown safely.
              Use when imported Markdown has inconsistent formatting.
            ---
            Keep the source editable.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        assertEquals(
            "Normalize Markdown safely. Use when imported Markdown has inconsistent formatting.",
            result.manifest?.description
        )
    }

    @Test
    fun preservesFoldedParagraphAndMoreIndentedLines() {
        val source = """
            ---
            name: folded-details
            description: >-
              First paragraph.

              Second paragraph.
                indented detail
              Tail.
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        val description = requireNotNull(result.manifest).description
        assertTrue(description.contains("First paragraph.\nSecond paragraph."))
        assertTrue(description.contains("\n  indented detail\n"))
    }

    @Test
    fun keepsIndentedLiteralDelimiterInsideFrontmatter() {
        val source = """
            ---
            name: literal-delimiter
            description: Preview literal metadata safely.
            metadata:
              note: |-
                before
                ---
                after
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        assertEquals("before\n---\nafter", result.manifest?.metadata?.get("note"))
        assertEquals("Instructions.", result.manifest?.instructions)
    }

    @Test
    fun keepsIndentedFoldedDelimiterInsideFrontmatter() {
        val source = """
            ---
            name: folded-delimiter
            description: >-
              before
              ---
              after
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        assertEquals("before --- after", result.manifest?.description)
        assertEquals("Instructions.", result.manifest?.instructions)
    }

    @Test
    fun handlesInlineYamlCommentsWithoutChangingScalarContent() {
        val source = """
            ---
            name: commented-skill # documented name
            description: "Quoted description" # documented description
            license: 'Apache-2.0' # documented license
            metadata:
              url: https://example.invalid/page#anchor
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        val manifest = requireNotNull(result.manifest)
        assertEquals("commented-skill", manifest.name)
        assertEquals("Quoted description", manifest.description)
        assertEquals("Apache-2.0", manifest.license)
        assertEquals("https://example.invalid/page#anchor", manifest.metadata["url"])
    }

    @Test
    fun acceptsUnicodeLowercaseNames() {
        val chinese = AgentSkillManifestParser.parse(
            """
                ---
                name: 数据分析
                description: Analyze imported data.
                ---
                Instructions.
            """.trimIndent(),
            directoryName = "数据分析"
        )
        val cyrillic = AgentSkillManifestParser.parse(
            """
                ---
                name: анализ-данных
                description: Analyze imported data.
                ---
                Instructions.
            """.trimIndent(),
            directoryName = "анализ-данных"
        )

        assertTrue(chinese.isValid)
        assertTrue(cyrillic.isValid)
    }

    @Test
    fun acceptsSupplementaryPlaneLetterAndCountsNameCodePoints() {
        val supplementaryLetter = "\uD801\uDC28"
        val validName = supplementaryLetter.repeat(64)
        val tooLongName = supplementaryLetter.repeat(65)
        val valid = AgentSkillManifestParser.parse(
            """
                ---
                name: $validName
                description: Accept a 64-code-point portable name.
                ---
                Instructions.
            """.trimIndent(),
            directoryName = validName
        )
        val tooLong = AgentSkillManifestParser.parse(
            """
                ---
                name: $tooLongName
                description: Reject a 65-code-point portable name.
                ---
                Instructions.
            """.trimIndent()
        )

        assertTrue(valid.isValid)
        assertEquals(validName, valid.manifest?.name)
        assertFalse(tooLong.isValid)
        assertTrue(tooLong.issues.any { it.field == "name" && it.message.contains("at most 64") })
    }

    @Test
    fun rejectsUnicodeUppercaseNames() {
        val result = AgentSkillManifestParser.parse(
            """
                ---
                name: Анализ-данных
                description: Analyze imported data.
                ---
                Instructions.
            """.trimIndent(),
            directoryName = "Анализ-данных"
        )

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "name" && it.message.contains("lowercase") })
    }

    @Test
    fun normalizesNameAndDirectoryBeforeComparing() {
        val decomposedName = "cafe\u0301-tools"
        val composedName = "café-tools"
        val source = """
            ---
            name: $decomposedName
            description: Work with normalized identifiers.
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source, directoryName = composedName)

        assertTrue(result.isValid)
        assertEquals(composedName, result.manifest?.name)
    }

    @Test
    fun countsDescriptionLimitByUnicodeCodePoint() {
        val supplementary = "😀"
        val validDescription = supplementary.repeat(1_024)
        val tooLongDescription = supplementary.repeat(1_025)
        val valid = AgentSkillManifestParser.parse(
            """
                ---
                name: description-boundary
                description: $validDescription
                ---
                Instructions.
            """.trimIndent()
        )
        val tooLong = AgentSkillManifestParser.parse(
            """
                ---
                name: description-overflow
                description: $tooLongDescription
                ---
                Instructions.
            """.trimIndent()
        )

        assertTrue(valid.isValid)
        assertFalse(tooLong.isValid)
        assertTrue(
            tooLong.issues.any {
                it.field == "description" && it.message.contains("at most 1024")
            }
        )
    }

    @Test
    fun countsCompatibilityLimitByUnicodeCodePoint() {
        val supplementary = "😀"
        val validCompatibility = supplementary.repeat(500)
        val tooLongCompatibility = supplementary.repeat(501)
        val valid = AgentSkillManifestParser.parse(
            """
                ---
                name: compatibility-boundary
                description: Validate supplementary compatibility text.
                compatibility: $validCompatibility
                ---
                Instructions.
            """.trimIndent()
        )
        val tooLong = AgentSkillManifestParser.parse(
            """
                ---
                name: compatibility-overflow
                description: Reject compatibility beyond the code-point limit.
                compatibility: $tooLongCompatibility
                ---
                Instructions.
            """.trimIndent()
        )

        assertTrue(valid.isValid)
        assertFalse(tooLong.isValid)
        assertTrue(
            tooLong.issues.any {
                it.field == "compatibility" && it.message.contains("at most 500")
            }
        )
    }

    @Test
    fun rejectsMissingFrontmatter() {
        val result = AgentSkillManifestParser.parse("# Not a skill")

        assertFalse(result.isValid)
        assertNull(result.manifest)
        assertTrue(result.issues.single().message.contains("frontmatter"))
    }

    @Test
    fun rejectsInvalidNameAndDirectoryMismatch() {
        val source = """
            ---
            name: Bad--Skill
            description: Useful description.
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source, directoryName = "good-skill")

        assertFalse(result.isValid)
        assertNull(result.manifest)
        assertTrue(result.issues.any { it.field == "name" && it.message.contains("lowercase") })
        assertTrue(result.issues.any { it.field == "name" && it.message.contains("parent skill directory") })
    }

    @Test
    fun rejectsMissingDescription() {
        val source = """
            ---
            name: valid-skill
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "description" && it.message.contains("required") })
    }

    @Test
    fun rejectsBlankCompatibility() {
        val source = """
            ---
            name: blank-compatibility
            description: Validate optional compatibility when present.
            compatibility: "   "
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertTrue(
            result.issues.any {
                it.field == "compatibility" && it.message.contains("must not be blank")
            }
        )
    }

    @Test
    fun rejectsUnknownFrontmatterFields() {
        val source = """
            ---
            name: strict-frontmatter
            description: Reject fields outside the portable Agent Skills contract.
            x-llmbench-note: preview-only
            custom:
              nested: value
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "x-llmbench-note" })
        assertTrue(result.issues.any { it.field == "custom" })
    }

    @Test
    fun rejectsDuplicateMetadataField() {
        val source = """
            ---
            name: duplicate-metadata
            description: Reject ambiguous frontmatter before importing a skill.
            metadata: {}
            metadata:
              author: twojstar
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertTrue(result.issues.single().message.contains("Duplicate key"))
    }

    @Test
    fun rejectsNestedMetadataInsteadOfFlatteningIt() {
        val source = """
            ---
            name: nested-metadata
            description: Reject metadata that cannot fit the preview model.
            metadata:
              author: twojstar
              nested:
                channel: stable
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertNull(result.manifest)
        assertTrue(
            result.issues.any {
                it.field == "metadata.nested" && it.message.contains("must be YAML scalars")
            }
        )
    }

    @Test
    fun rejectsUnterminatedSingleQuotedScalar() {
        val source = """
            ---
            name: malformed-quote
            description: 'unterminated
            ---
            Instructions.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertFalse(result.isValid)
        assertNull(result.manifest)
        assertTrue(result.issues.single().message.contains("Invalid YAML frontmatter"))
    }

    @Test
    fun parsesLiteralMetadataWithoutExecutingIt() {
        val source = """
            ---
            name: inert-import
            description: Preview an imported skill. Use before enabling third-party content.
            metadata:
              note: |-
                scripts/run.sh
                https://example.invalid/payload
            ---
            Do not execute anything during import.
        """.trimIndent()

        val result = AgentSkillManifestParser.parse(source)

        assertTrue(result.isValid)
        assertEquals(
            "scripts/run.sh\nhttps://example.invalid/payload",
            result.manifest?.metadata?.get("note")
        )
        assertEquals("Do not execute anything during import.", result.manifest?.instructions)
    }
}
