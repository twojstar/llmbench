package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.skills.LocalSkillLibraryStore
import com.twojstar.llmbench.ui.viewmodel.ExternalMarkdownOpenResult
import com.twojstar.llmbench.ui.viewmodel.MarkdownWorkspaceOrigin
import com.twojstar.llmbench.ui.viewmodel.MarkdownWorkspaceViewModel
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSkillWorkspaceSourceSaveTest {
    @Test
    fun cancelledSaveCoroutineStillReconcilesCommittedSource() = runBlocking {
        val root = Files.createTempDirectory("llmbench-skill-save").toFile()
        try {
            val store = LocalSkillLibraryStore(root)
            val original = skillSource(SKILL_NAME, FIRST_VERSION)
            val edited = skillSource(SKILL_NAME, "Edited version.")
            store.add(original)
            val opened = requireNotNull(store.read(SKILL_NAME))
            val viewModel = boundWorkspace(opened.source, opened.sourceDigest)
            assertTrue(viewModel.updateText(edited))
            val snapshot = requireNotNull(viewModel.beginExport())
            val snapshotOrigin = requireNotNull(snapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill)

            val saveJob = launch(start = CoroutineStart.UNDISPATCHED) {
                cancel()
                persistLocalSkillSource(
                    store = store,
                    workspaceViewModel = viewModel,
                    snapshot = snapshot,
                    snapshotOrigin = snapshotOrigin
                )
            }
            saveJob.join()

            val persisted = requireNotNull(store.read(SKILL_NAME))
            val state = viewModel.uiState.value
            val persistedOrigin = requireNotNull(state.origin as? MarkdownWorkspaceOrigin.LocalSkill)
            assertEquals(edited, persisted.source)
            assertEquals(persisted.sourceDigest, persistedOrigin.sourceDigest)
            assertEquals(SKILL_NAME, persistedOrigin.name)
            assertFalse(state.isDirty)
            assertFalse(state.isExporting)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sourceSaveRequestsConfirmationBeforeRenaming() = runBlocking {
        val root = Files.createTempDirectory("llmbench-skill-rename-prompt").toFile()
        try {
            val store = LocalSkillLibraryStore(root)
            val original = skillSource(SKILL_NAME, FIRST_VERSION)
            val renamed = skillSource(RENAMED_SKILL_NAME, "Renamed version.")
            store.add(original)
            val opened = requireNotNull(store.read(SKILL_NAME))
            val viewModel = boundWorkspace(opened.source, opened.sourceDigest)
            assertTrue(viewModel.updateText(renamed))
            val snapshot = requireNotNull(viewModel.beginExport())
            val snapshotOrigin = requireNotNull(snapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill)

            val outcome = persistLocalSkillSource(store, viewModel, snapshot, snapshotOrigin)

            assertEquals(
                LocalSkillSourceSaveOutcome.RenameRequired(
                    existingName = SKILL_NAME,
                    newName = RENAMED_SKILL_NAME,
                    revision = snapshot.revision
                ),
                outcome
            )
            assertEquals(original, store.read(SKILL_NAME)?.source)
            assertNull(store.read(RENAMED_SKILL_NAME))
            assertTrue(viewModel.uiState.value.isDirty)
            assertFalse(viewModel.uiState.value.isExporting)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun confirmedRenameMovesSourceAndUpdatesWorkspaceOrigin() = runBlocking {
        val root = Files.createTempDirectory("llmbench-skill-rename-confirm").toFile()
        try {
            val store = LocalSkillLibraryStore(root)
            val original = skillSource(SKILL_NAME, FIRST_VERSION)
            val renamed = skillSource(RENAMED_SKILL_NAME, "Renamed version.")
            store.add(original)
            val opened = requireNotNull(store.read(SKILL_NAME))
            val viewModel = boundWorkspace(opened.source, opened.sourceDigest)
            assertTrue(viewModel.updateText(renamed))

            val firstSnapshot = requireNotNull(viewModel.beginExport())
            val firstOrigin = requireNotNull(firstSnapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill)
            assertTrue(
                persistLocalSkillSource(store, viewModel, firstSnapshot, firstOrigin) is
                    LocalSkillSourceSaveOutcome.RenameRequired
            )

            val renameSnapshot = requireNotNull(viewModel.beginExport())
            val renameOrigin = requireNotNull(renameSnapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill)
            val outcome = persistLocalSkillRename(store, viewModel, renameSnapshot, renameOrigin)

            assertTrue(outcome is LocalSkillSourceSaveOutcome.Saved)
            assertEquals(RENAMED_SKILL_NAME, (outcome as LocalSkillSourceSaveOutcome.Saved).name)
            val persisted = requireNotNull(store.read(RENAMED_SKILL_NAME))
            assertNull(store.read(SKILL_NAME))
            assertEquals(renamed, persisted.source)
            val state = viewModel.uiState.value
            val persistedOrigin = requireNotNull(state.origin as? MarkdownWorkspaceOrigin.LocalSkill)
            assertEquals(RENAMED_SKILL_NAME, persistedOrigin.name)
            assertEquals(persisted.sourceDigest, persistedOrigin.sourceDigest)
            assertFalse(state.isDirty)
            assertFalse(state.isExporting)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun boundWorkspace(source: String, sourceDigest: String): MarkdownWorkspaceViewModel =
        MarkdownWorkspaceViewModel().also { viewModel ->
            val origin = MarkdownWorkspaceOrigin.LocalSkill(
                name = SKILL_NAME,
                sourceDigest = sourceDigest
            )
            assertEquals(
                ExternalMarkdownOpenResult.OPENED,
                viewModel.openExternalText(
                    text = source,
                    displayName = SKILL_FILE_NAME,
                    origin = origin,
                    markDirty = false
                )
            )
        }

    private fun skillSource(name: String, description: String): String = """
        ---
        name: $name
        description: $description
        ---
        # Instructions
        Keep the save transaction consistent.
    """.trimIndent()

    private companion object {
        const val SKILL_NAME = "cancellation-safe"
        const val RENAMED_SKILL_NAME = "renamed-cancellation-safe"
        const val SKILL_FILE_NAME = "SKILL.md"
        const val FIRST_VERSION = "First version."
    }
}
