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
        val source = skillSource(RELEASE_SKILL, FIRST_VERSION)

        val added = store.add(source)
        val loaded = store.load()
        val storedDirectory = root.listFiles().orEmpty().single()
        val opened = store.read(RELEASE_SKILL)

        assertEquals(RELEASE_SKILL, added.name)
        assertEquals(FIRST_VERSION, added.description)
        assertNotEquals(RELEASE_SKILL, storedDirectory.name)
        assertEquals(source, storedDirectory.resolve(SKILL_FILE_NAME).readText())
        assertEquals(listOf(RELEASE_SKILL), loaded.map(LocalSkillSummary::name))
        assertEquals(source, opened?.source)
    }

    @Test
    fun addingSameSkillNameRequiresExplicitReplacement() = runBlocking {
        val original = skillSource(RELEASE_SKILL, FIRST_VERSION)
        val replacement = skillSource(RELEASE_SKILL, "Second version.")
        store.add(original)

        val conflict = assertThrows(LocalSkillAlreadyExistsException::class.java) {
            runBlocking { store.add(replacement) }
        }

        assertEquals(RELEASE_SKILL, conflict.skillName)
        assertEquals(original, store.read(RELEASE_SKILL)?.source)

        store.add(replacement, replaceExisting = true)

        assertEquals(1, store.load().size)
        assertEquals(replacement, store.read(RELEASE_SKILL)?.source)
    }

    @Test
    fun invalidTargetDirectoryIsReclaimedOnRetry() = runBlocking {
        val original = skillSource(RETRY_SKILL, "First attempt.")
        val retry = skillSource(RETRY_SKILL, "Retry succeeds.")
        store.add(original)
        val storedDirectory = root.listFiles().orEmpty().single()
        assertTrue(storedDirectory.resolve(SKILL_FILE_NAME).delete())

        val saved = store.add(retry)

        assertEquals(RETRY_SKILL, saved.name)
        assertEquals(retry, store.read(RETRY_SKILL)?.source)
        assertEquals(1, root.listFiles().orEmpty().size)
    }

    @Test
    fun removesOnlyRequestedSkill() = runBlocking {
        store.add(skillSource(ALPHA_SKILL, "Alpha."))
        store.add(skillSource(BETA_SKILL, "Beta."))

        store.remove(ALPHA_SKILL)

        assertNull(store.read(ALPHA_SKILL))
        assertNotNull(store.read(BETA_SKILL))
        assertEquals(listOf(BETA_SKILL), store.load().map(LocalSkillSummary::name))
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
        val storedFile = root.listFiles().orEmpty().single().resolve(SKILL_FILE_NAME)
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
        root.listFiles().orEmpty().first().resolve(SKILL_FILE_NAME).writeText("not a skill")

        store.add(skillSource(REPLACEMENT_SLOT, "Uses reclaimed capacity."))

        val loaded = store.load()
        assertEquals(LocalSkillLibraryStore.MAX_LOCAL_SKILLS, loaded.size)
        assertTrue(loaded.any { it.name == REPLACEMENT_SLOT })
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

    private companion object {
        const val RELEASE_SKILL = "release-checklist"
        const val RETRY_SKILL = "retry-skill"
        const val ALPHA_SKILL = "alpha-skill"
        const val BETA_SKILL = "beta-skill"
        const val REPLACEMENT_SLOT = "replacement-slot"
        const val SKILL_FILE_NAME = "SKILL.md"
        const val FIRST_VERSION = "First version."
    }
}
