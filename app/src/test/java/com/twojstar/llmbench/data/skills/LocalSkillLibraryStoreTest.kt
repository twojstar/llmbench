package com.twojstar.llmbench.data.skills

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSkillLibraryStoreTest {
    private val root = Files.createTempDirectory("llmbench-local-skills").toFile()
    private val store = LocalSkillLibraryStore(root)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun storesSourceAsCanonicalSkillDocumentAndLoadsIt() = runBlocking {
        val source = skillSource("release-checklist", "First version.")

        val added = store.add(source)
        val loaded = store.load()

        assertEquals("release-checklist", added.manifest.name)
        assertEquals(source, added.source)
        assertEquals(source, root.resolve("release-checklist/SKILL.md").readText())
        assertEquals(listOf("release-checklist"), loaded.map { it.manifest.name })
        assertEquals(source, loaded.single().source)
    }

    @Test
    fun addingSameSkillNameReplacesItsStoredSource() = runBlocking {
        store.add(skillSource("release-checklist", "First version."))
        val replacement = skillSource("release-checklist", "Second version.")

        store.add(replacement)

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(replacement, loaded.single().source)
    }

    @Test
    fun removesOnlyRequestedSkillDirectory() = runBlocking {
        store.add(skillSource("alpha-skill", "Alpha."))
        store.add(skillSource("beta-skill", "Beta."))

        store.remove("alpha-skill")

        assertTrue(!root.resolve("alpha-skill").exists())
        assertEquals(listOf("beta-skill"), store.load().map { it.manifest.name })
    }

    @Test
    fun rejectsInvalidPortableSkillBeforeWriting() {
        val invalid = """
            ---
            name: Bad--Skill
            description: Invalid portable name.
            ---
            Instructions.
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.add(invalid) }
        }
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun ignoresStoredDocumentWhoseDirectoryDoesNotMatchManifestName() = runBlocking {
        val mismatchedDirectory = root.resolve("wrong-directory")
        mismatchedDirectory.mkdirs()
        mismatchedDirectory.resolve("SKILL.md")
            .writeText(skillSource("right-directory", "Mismatch."))

        assertTrue(store.load().isEmpty())
    }

    private fun skillSource(name: String, description: String): String = """
        ---
        name: $name
        description: $description
        ---
        # Instructions
        Keep imported content inert until explicitly used.
    """.trimIndent()
}
