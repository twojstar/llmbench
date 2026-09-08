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
            x-llmbench-note: preview-only
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
        assertEquals("preview-only", manifest.extraFrontmatter["x-llmbench-note"])
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
