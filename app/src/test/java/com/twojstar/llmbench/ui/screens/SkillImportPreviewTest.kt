package com.twojstar.llmbench.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillImportPreviewTest {
    @Test
    fun buildsValidReadOnlyPreview() {
        val source = """
            ---
            name: release-checklist
            description: Review release state before publishing.
            metadata:
              author: twojstar
            ---
            # Instructions
            Do not execute scripts while previewing.
        """.trimIndent()

        val preview = buildSkillImportPreview("SKILL.md", source)

        assertTrue(preview.isValid)
        assertEquals("SKILL.md", preview.displayName)
        assertEquals(source, preview.source)
        assertEquals("release-checklist", preview.manifest?.name)
        assertEquals("twojstar", preview.manifest?.metadata?.get("author"))
        assertTrue(preview.manifest?.instructions?.contains("Do not execute scripts") == true)
    }

    @Test
    fun keepsInvalidSourceAvailableForInspection() {
        val source = """
            ---
            name: Bad--Skill
            description: Inspect malformed imports without accepting them.
            ---
            Instructions.
        """.trimIndent()

        val preview = buildSkillImportPreview("broken.md", source)

        assertFalse(preview.isValid)
        assertNull(preview.manifest)
        assertEquals(source, preview.source)
        assertTrue(preview.issues.any { it.field == "name" })
        assertTrue(preview.issues.any { it.message.contains("SKILL.md") })
    }

    @Test
    fun flagsNonPortableFilenameWithoutDiscardingParsedPreview() {
        val source = """
            ---
            name: release-checklist
            description: Inspect a valid manifest under the wrong filename.
            ---
            Instructions.
        """.trimIndent()

        val preview = buildSkillImportPreview("release.md", source)

        assertFalse(preview.isValid)
        assertNotNull(preview.manifest)
        assertEquals("release-checklist", preview.manifest?.name)
        assertTrue(preview.issues.single().message.contains("SKILL.md"))
    }

    @Test
    fun boundsRenderedSourcePreviewWithoutChangingSource() {
        val source = "x".repeat(30 * 1024)
        val preview = buildSkillImportPreview("SKILL.md", source)

        assertEquals(source, preview.source)
        assertTrue(preview.sourceForDisplay().length < source.length)
        assertTrue(preview.sourceForDisplay().contains("preview truncated"))
    }
}
