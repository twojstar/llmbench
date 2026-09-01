package com.twojstar.llmbench.data.engine

import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ProfileSerializationTest {
    @Test
    fun customNotePreservesExistingLocalExtensionValues() {
        val base = Profile(
            extensions = mapOf(
                "local" to mapOf("theme" to "dark", "note" to "old"),
                "other" to mapOf("enabled" to "yes")
            )
        )

        val merged = ProfileMerger.merge(
            base,
            ProfileOverlay(customNote = "new note")
        )

        assertEquals("dark", merged.extensions["local"]?.get("theme"))
        assertEquals("new note", merged.extensions["local"]?.get("note"))
        assertEquals("yes", merged.extensions["other"]?.get("enabled"))
    }

    @Test
    fun dumpOverlayIncludesKnowledgeAndOutputOverrides() {
        val yaml = YamlParser.dumpOverlay(
            ProfileOverlay(
                knowledgeOverrides = mapOf("requireTraceableClaims" to true),
                defaultFormat = "markdown",
                maxHeadingDepth = 2,
                preferShortParagraphs = false,
                tables = "prefer",
                codeExamples = "runnable",
                citations = "required"
            )
        )

        assertContains(yaml, "knowledge:")
        assertContains(yaml, "  \"requireTraceableClaims\": true")
        assertContains(yaml, "output:")
        assertContains(yaml, "  defaultFormat: \"markdown\"")
        assertContains(yaml, "  maxHeadingDepth: 2")
        assertContains(yaml, "  preferShortParagraphs: false")
        assertContains(yaml, "  tables: \"prefer\"")
        assertContains(yaml, "  codeExamples: \"runnable\"")
        assertContains(yaml, "  citations: \"required\"")
    }

    @Test
    fun dumpOverlayEscapesStringsAndDynamicKeys() {
        val yaml = YamlParser.dumpOverlay(
            ProfileOverlay(
                id = "id: #1",
                modifierOverrides = mapOf("tone: #1" to 2),
                customNote = "line1\n\"quoted\" \\ path"
            )
        )

        assertContains(yaml, "id: \"id: #1\"")
        assertContains(yaml, "    \"tone: #1\": 2")
        assertContains(yaml, "    note: \"line1\\n\\\"quoted\\\" \\\\ path\"")
    }
}
