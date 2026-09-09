package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twojstar.llmbench.data.skills.LocalSkillLibraryStore
import com.twojstar.llmbench.ui.viewmodel.MarkdownExportSnapshot
import com.twojstar.llmbench.ui.viewmodel.MarkdownWorkspaceOrigin
import com.twojstar.llmbench.ui.viewmodel.MarkdownWorkspaceViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface LocalSkillSourceSaveOutcome {
    data class Saved(val name: String, val current: Boolean) : LocalSkillSourceSaveOutcome
    data class Failed(val message: String) : LocalSkillSourceSaveOutcome
}

internal suspend fun persistLocalSkillSource(
    store: LocalSkillLibraryStore,
    workspaceViewModel: MarkdownWorkspaceViewModel,
    snapshot: MarkdownExportSnapshot,
    snapshotOrigin: MarkdownWorkspaceOrigin.LocalSkill
): LocalSkillSourceSaveOutcome = withContext(NonCancellable) {
    val saveResult = runCatching {
        store.replace(
            name = snapshotOrigin.name,
            expectedSourceDigest = snapshotOrigin.sourceDigest,
            source = snapshot.document.text
        )
    }
    val failure = saveResult.exceptionOrNull()
    if (failure != null) {
        workspaceViewModel.failExport(snapshot)
        val message = when (failure) {
            is IllegalArgumentException ->
                failure.message ?: "SKILL.md is not valid and was not saved."
            is IOException ->
                failure.message ?: "Could not save the local skill source."
            else -> throw failure
        }
        return@withContext LocalSkillSourceSaveOutcome.Failed(message)
    }

    val saved = requireNotNull(saveResult.getOrNull())
    val persistedOrigin = MarkdownWorkspaceOrigin.LocalSkill(
        name = snapshotOrigin.name,
        sourceDigest = saved.sourceDigest
    )
    LocalSkillSourceSaveOutcome.Saved(
        name = snapshotOrigin.name,
        current = workspaceViewModel.completeSourceSave(snapshot, persistedOrigin)
    )
}

@Composable
internal fun LocalSkillWorkspaceSourceBar(
    workspaceViewModel: MarkdownWorkspaceViewModel,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val origin = uiState.origin as? MarkdownWorkspaceOrigin.LocalSkill ?: return
    val store = remember(context.applicationContext) {
        LocalSkillLibraryStore(
            File(context.noBackupFilesDir, LocalSkillLibraryStore.LIBRARY_DIRECTORY_NAME)
        )
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Local skill: ${origin.name}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                enabled = uiState.isDirty && !uiState.isBusy && !uiState.isLargeDocumentReadOnly,
                onClick = {
                    val snapshot = workspaceViewModel.beginExport() ?: return@Button
                    val snapshotOrigin = snapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill
                    if (snapshotOrigin == null) {
                        workspaceViewModel.failExport(snapshot)
                        return@Button
                    }
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        when (
                            val outcome = persistLocalSkillSource(
                                store = store,
                                workspaceViewModel = workspaceViewModel,
                                snapshot = snapshot,
                                snapshotOrigin = snapshotOrigin
                            )
                        ) {
                            is LocalSkillSourceSaveOutcome.Failed -> onMessage(outcome.message)
                            is LocalSkillSourceSaveOutcome.Saved -> onMessage(
                                if (outcome.current) {
                                    "Saved '${outcome.name}' to local skills."
                                } else {
                                    "Saved '${outcome.name}' snapshot; newer edits remain unsaved."
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("save_local_skill_source")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save source")
            }
        }
    }
}
