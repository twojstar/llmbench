package com.twojstar.llmbench.data.skills

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun storesSourceAsCanonicalSkillDocumentAndLoadsSummary() = runBlocking {
        val source = skillSource("release-checklist", "First version.")

        val added = store.add(source)
        val loaded = store.load()
        val storedDirectory = root.listFiles().orEmpty().single()
        val opened = store.read("release-checklist")

        assertEquals("release-checklist", added.name)
        assertEquals("First version.", added.description)
        assertNotEquals("release-checklist", storedDirectory.name)
        assertEquals(source, storedDirectory.resolve("SKILL.md").readText())
        assertEquals(listOf("release-checklist"), loaded.map(LocalSkillSummary::name))
        assertEquals(source, opened?.source)
    }

    @Test
    fun addingSameSkillNameRequiresExplicitReplacement() = runBlocking {
        val original = skillSource("release-checklist", "First version.")
        val replacement = skillSource("release-checklist", "Second version.")
        store.add(original)

        val conflict = assertThrows(LocalSkillAlreadyExistsException::class.java) {
            runBlocking { store.add(replacement) }
        }

        assertEquals("release-checklist", conflict.skillName)
        assertEquals(original, store.read("release-checklist")?.source)

        store.add(replacement, replaceExisting = true)

        assertEquals(1, store.load().size)
        assertEquals(replacement, store.read("release-checklist")?.source)
    }

    @Test
    fun invalidTargetDirectoryIsReclaimedOnRetry() = runBlocking {
        val original = skillSource("retry-skill", "First attempt.")
        val retry = skillSource("retry-skill", "Retry succeeds.")
        store.add(original)
        val storedDirectory = root.listFiles().orEmpty().single()
        assertTrue(storedDirectory.resolve("SKILL.md").delete())

        val saved = store.add(retry)

        assertEquals("retry-skill", saved.name)
        assertEquals(retry, store.read("retry-skill")?.source)
        assertEquals(1, root.listFiles().orEmpty().size)
    }

    @Test
    fun removesOnlyRequestedSkill() = runBlocking {
        store.add(skillSource("alpha-skill", "Alpha."))
        store.add(skillSource("beta-skill", "Beta."))

        store.remove("alpha-skill")

        assertNull(store.read("alpha-skill"))
        assertNotNull(store.read("beta-skill"))
        assertEquals(listOf("beta-skill"), store.load().map(LocalSkillSummary::name))
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
    fun prunesStoredDocumentWhenManifestIdentityChanges() = runBlocking {
        store.add(skillSource("stable-name", "Original."))
        val storedFile = root.listFiles().orEmpty().single().resolve("SKILL.md")
        storedFile.writeText(skillSource("different-name", "Tampered."))

        assertTrue(store.load().isEmpty())
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun storesMaximumLengthSupplementaryUnicodeNameWithSafeDirectoryKey() = runBlocking {
        val supplementaryLetter = "\uD801\uDC28"
        val name = supplementaryLetter.repeat(64)
        val source = skillSource(name, "Unicode boundary.")

        val added = store.add(source)

        assertEquals(name, added.name)
        assertEquals(name, store.load().single().name)
        assertNotNull(store.read(name))
        assertTrue(root.listFiles().orEmpty().single().name.encodeToByteArray().size < 255)
    }

    @Test
    fun corruptEntryDoesNotConsumeFullLibraryQuota() = runBlocking {
        repeat(LocalSkillLibraryStore.MAX_LOCAL_SKILLS) { index ->
            store.add(skillSource("skill-$index", "Entry $index."))
        }
        root.listFiles().orEmpty().first().resolve("SKILL.md").writeText("not a skill")

        store.add(skillSource("replacement-slot", "Uses reclaimed capacity."))

        val loaded = store.load()
        assertEquals(LocalSkillLibraryStore.MAX_LOCAL_SKILLS, loaded.size)
        assertTrue(loaded.any { it.name == "replacement-slot" })
    }

    @Test
    fun fullLibraryLoadsAsBoundedSummariesInsteadOfRetainingEverySource() = runBlocking {
        val instructions = "x".repeat(32 * 1024)
        repeat(LocalSkillLibraryStore.MAX_LOCAL_SKILLS) { index ->
            store.add(skillSource("bulk-$index", "Bulk entry $index.", instructions))
        }

        val loaded = store.load()

        assertEquals(LocalSkillLibraryStore.MAX_LOCAL_SKILLS, loaded.size)
        assertTrue(loaded.all { it.description.startsWith("Bulk entry") })
    }

    private fun skillSource(
        name: String,
        description: String,
        instructions: String = "Keep imported content inert until explicitly used."
    ): String = """
        ---
        name: $name
        description: $description
        ---
        # Instructions
        $instructions
    """.trimIndent()
}
