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
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSkillWorkspaceSourceSaveTest {
    @Test
    fun cancelledSaveCoroutineStillReconcilesCommittedSource() = runBlocking {
        val root = Files.createTempDirectory("llmbench-skill-save").toFile()
        try {
            val store = LocalSkillLibraryStore(root)
            val original = skillSource("First version.")
            val edited = skillSource("Edited version.")
            store.add(original)
            val opened = requireNotNull(store.read(SKILL_NAME))
            val viewModel = MarkdownWorkspaceViewModel()
            val origin = MarkdownWorkspaceOrigin.LocalSkill(
                name = SKILL_NAME,
                sourceDigest = opened.sourceDigest
            )
            assertEquals(
                ExternalMarkdownOpenResult.OPENED,
                viewModel.openExternalText(
                    text = opened.source,
                    displayName = "SKILL.md",
                    origin = origin,
                    markDirty = false
                )
            )
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

    private fun skillSource(description: String): String = """
        ---
        name: $SKILL_NAME
        description: $description
        ---
        # Instructions
        Keep the save transaction consistent.
    """.trimIndent()

    private companion object {
        const val SKILL_NAME = "cancellation-safe"
    }
}
