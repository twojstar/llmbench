package com.twojstar.llmbench.data.skills

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun storesSourceAsCanonicalSkillDocumentAndLoadsDisabledSummary() = runBlocking {
        val source = skillSource(RELEASE_SKILL, FIRST_VERSION)

        val added = store.add(source)
        val loaded = store.load()
        val storedDirectory = root.listFiles().orEmpty().single()
        val opened = store.read(RELEASE_SKILL)

        assertEquals(RELEASE_SKILL, added.name)
        assertEquals(FIRST_VERSION, added.description)
        assertFalse(added.enabled)
        assertNotEquals(RELEASE_SKILL, storedDirectory.name)
        assertEquals(source, storedDirectory.resolve(SKILL_FILE_NAME).readText())
        assertEquals(listOf(RELEASE_SKILL), loaded.map(LocalSkillSummary::name))
        assertFalse(loaded.single().enabled)
        assertEquals(source, opened?.source)
    }

    @Test
    fun activationPersistsAndOnlyEnabledManifestsReachRuntime() = runBlocking {
        store.add(skillSource(ALPHA_SKILL, ALPHA_DESCRIPTION, "Follow alpha instructions."))
        store.add(skillSource(BETA_SKILL, BETA_DESCRIPTION, "Follow beta instructions."))

        val enabled = store.setEnabled(BETA_SKILL, true)
        val reloadedStore = LocalSkillLibraryStore(root)
        val summaries = reloadedStore.load()
        val runtime = reloadedStore.loadEnabledManifests()

        assertEquals(BETA_SKILL, enabled?.name)
        assertTrue(enabled?.enabled == true)
        assertFalse(summaries.first { it.name == ALPHA_SKILL }.enabled)
        assertTrue(summaries.first { it.name == BETA_SKILL }.enabled)
        assertEquals(listOf(BETA_SKILL), runtime.map(AgentSkillManifest::name))
        assertEquals("# Instructions\nFollow beta instructions.", runtime.single().instructions)
    }

    @Test
    fun disablingRemovesSkillFromRuntimeSet() = runBlocking {
        store.add(skillSource(TOGGLE_SKILL, "Toggle."))
        store.setEnabled(TOGGLE_SKILL, true)

        val disabled = store.setEnabled(TOGGLE_SKILL, false)

        assertFalse(disabled?.enabled ?: true)
        assertTrue(store.loadEnabledManifests().isEmpty())
    }

    @Test
    fun replacingSameSkillRequiresExplicitFlagAndPreservesActivation() = runBlocking {
        val original = skillSource(RELEASE_SKILL, FIRST_VERSION)
        val replacement = skillSource(RELEASE_SKILL, SECOND_VERSION)
        store.add(original)
        store.setEnabled(RELEASE_SKILL, true)

        val conflict = runCatching { store.add(replacement) }.exceptionOrNull()

        assertTrue(conflict is LocalSkillAlreadyExistsException)
        assertEquals(original, store.read(RELEASE_SKILL)?.source)

        val replaced = store.add(replacement, replaceExisting = true)

        assertTrue(replaced.enabled)
        assertEquals(1, store.load().size)
        assertEquals(replacement, store.read(RELEASE_SKILL)?.source)
        assertEquals(listOf(RELEASE_SKILL), store.loadEnabledManifests().map(AgentSkillManifest::name))
    }

    @Test
    fun boundReplacementUpdatesExistingSkillAndPreservesActivation() = runBlocking {
        val original = skillSource(RELEASE_SKILL, FIRST_VERSION)
        val replacement = skillSource(RELEASE_SKILL, SECOND_VERSION)
        store.add(original)
        store.setEnabled(RELEASE_SKILL, true)

        val replaced = store.replace(RELEASE_SKILL, replacement)

        assertTrue(replaced.enabled)
        assertEquals(SECOND_VERSION, replaced.description)
        assertEquals(replacement, store.read(RELEASE_SKILL)?.source)
        assertEquals(listOf(RELEASE_SKILL), store.loadEnabledManifests().map(AgentSkillManifest::name))
    }

    @Test
    fun boundReplacementRejectsManifestRenameWithoutCreatingAnotherSkill() = runBlocking {
        val original = skillSource(RELEASE_SKILL, FIRST_VERSION)
        val renamed = skillSource(RENAMED_SKILL, SECOND_VERSION)
        store.add(original)

        val error = runCatching { store.replace(RELEASE_SKILL, renamed) }.exceptionOrNull()

        assertTrue(error is LocalSkillRenameRequiredException)
        assertEquals(original, store.read(RELEASE_SKILL)?.source)
        assertNull(store.read(RENAMED_SKILL))
        assertEquals(listOf(RELEASE_SKILL), store.load().map(LocalSkillSummary::name))
    }

    @Test
    fun boundReplacementRequiresExistingSkill() = runBlocking {
        val replacement = skillSource(RELEASE_SKILL, SECOND_VERSION)

        val error = runCatching { store.replace(RELEASE_SKILL, replacement) }.exceptionOrNull()

        assertTrue(error is LocalSkillNotFoundException)
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun boundReplacementRejectsOverBudgetEditBeforeTouchingEnabledSource() = runBlocking {
        val original = skillSource(RELEASE_SKILL, FIRST_VERSION)
        store.add(original)
        store.setEnabled(RELEASE_SKILL, true)
        val oversized = skillSource(
            RELEASE_SKILL,
            SECOND_VERSION,
            "x".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS)
        )

        val error = runCatching { store.replace(RELEASE_SKILL, oversized) }.exceptionOrNull()

        assertTrue(error is LocalSkillActivationException)
        assertEquals(original, store.read(RELEASE_SKILL)?.source)
        assertTrue(store.load().single().enabled)
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
        assertFalse(saved.enabled)
        assertEquals(1, root.listFiles().orEmpty().size)
    }

    @Test
    fun activationRejectsOneSkillOverRuntimeBudget() = runBlocking {
        store.add(
            skillSource(
                OVERSIZED_SKILL,
                OVERSIZED_DESCRIPTION,
                "x".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS)
            )
        )

        val error = runCatching { store.setEnabled(OVERSIZED_SKILL, true) }.exceptionOrNull()

        assertTrue(error is LocalSkillActivationException)
        assertFalse(store.load().single().enabled)
        assertTrue(store.loadEnabledManifests().isEmpty())
    }

    @Test
    fun activationRejectsSkillThatWouldExceedCombinedRuntimeBudget() = runBlocking {
        val chunk = "x".repeat(22 * 1024)
        store.add(skillSource(ALPHA_SKILL, ALPHA_DESCRIPTION, chunk))
        store.add(skillSource(BETA_SKILL, BETA_DESCRIPTION, chunk))
        store.add(skillSource(GAMMA_SKILL, "Gamma.", chunk))
        store.setEnabled(ALPHA_SKILL, true)
        store.setEnabled(BETA_SKILL, true)

        val error = runCatching { store.setEnabled(GAMMA_SKILL, true) }.exceptionOrNull()
        val runtime = store.loadEnabledManifests()

        assertTrue(error is LocalSkillActivationException)
        assertEquals(listOf(ALPHA_SKILL, BETA_SKILL), runtime.map(AgentSkillManifest::name))
        assertFalse(store.load().first { it.name == GAMMA_SKILL }.enabled)
    }

    @Test
    fun runtimeLoadPrunesStaleOverBudgetEnabledMarker() = runBlocking {
        store.add(
            skillSource(
                OVERSIZED_SKILL,
                OVERSIZED_DESCRIPTION,
                "x".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS)
            )
        )
        val storedDirectory = root.listFiles().orEmpty().single()
        storedDirectory.resolve(ENABLED_FILE_NAME).writeBytes(ByteArray(0))

        val runtime = store.loadEnabledManifests()

        assertTrue(runtime.isEmpty())
        assertFalse(storedDirectory.resolve(ENABLED_FILE_NAME).exists())
        assertFalse(store.load().single().enabled)
    }

    @Test
    fun removesOnlyRequestedSkill() = runBlocking {
        store.add(skillSource(ALPHA_SKILL, ALPHA_DESCRIPTION))
        store.add(skillSource(BETA_SKILL, BETA_DESCRIPTION))
        store.setEnabled(ALPHA_SKILL, true)

        store.remove(ALPHA_SKILL)

        assertNull(store.read(ALPHA_SKILL))
        assertNotNull(store.read(BETA_SKILL))
        assertEquals(listOf(BETA_SKILL), store.load().map(LocalSkillSummary::name))
        assertTrue(store.loadEnabledManifests().isEmpty())
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
        store.add(skillSource(STABLE_SKILL, "Original."))
        store.setEnabled(STABLE_SKILL, true)
        val storedFile = root.listFiles().orEmpty().single().resolve(SKILL_FILE_NAME)
        storedFile.writeText(skillSource("different-name", "Tampered."))

        assertTrue(store.loadEnabledManifests().isEmpty())
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
        const val RENAMED_SKILL = "renamed-skill"
        const val ALPHA_SKILL = "alpha-skill"
        const val BETA_SKILL = "beta-skill"
        const val GAMMA_SKILL = "gamma-skill"
        const val TOGGLE_SKILL = "toggle-skill"
        const val OVERSIZED_SKILL = "oversized-skill"
        const val STABLE_SKILL = "stable-name"
        const val REPLACEMENT_SLOT = "replacement-slot"
        const val SKILL_FILE_NAME = "SKILL.md"
        const val ENABLED_FILE_NAME = ".enabled"
        const val FIRST_VERSION = "First version."
        const val SECOND_VERSION = "Second version."
        const val ALPHA_DESCRIPTION = "Alpha."
        const val BETA_DESCRIPTION = "Beta."
        const val OVERSIZED_DESCRIPTION = "Too large for runtime."
    }
}
