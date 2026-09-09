package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twojstar.llmbench.data.skills.LocalSkillLibraryStore
import com.twojstar.llmbench.data.skills.LocalSkillRenameRequiredException
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
    data class RenameRequired(val existingName: String, val newName: String) : LocalSkillSourceSaveOutcome
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
        if (failure is LocalSkillRenameRequiredException) {
            return@withContext LocalSkillSourceSaveOutcome.RenameRequired(
                existingName = failure.existingName,
                newName = failure.newName
            )
        }
        return@withContext LocalSkillSourceSaveOutcome.Failed(localSkillSaveFailureMessage(failure))
    }

    val saved = requireNotNull(saveResult.getOrNull())
    completeLocalSkillSourceSave(
        workspaceViewModel = workspaceViewModel,
        snapshot = snapshot,
        name = snapshotOrigin.name,
        sourceDigest = saved.sourceDigest
    )
}

internal suspend fun persistLocalSkillRename(
    store: LocalSkillLibraryStore,
    workspaceViewModel: MarkdownWorkspaceViewModel,
    snapshot: MarkdownExportSnapshot,
    snapshotOrigin: MarkdownWorkspaceOrigin.LocalSkill
): LocalSkillSourceSaveOutcome = withContext(NonCancellable) {
    val renameResult = runCatching {
        store.rename(
            existingName = snapshotOrigin.name,
            expectedSourceDigest = snapshotOrigin.sourceDigest,
            source = snapshot.document.text
        )
    }
    val failure = renameResult.exceptionOrNull()
    if (failure != null) {
        workspaceViewModel.failExport(snapshot)
        return@withContext LocalSkillSourceSaveOutcome.Failed(localSkillSaveFailureMessage(failure))
    }

    val renamed = requireNotNull(renameResult.getOrNull())
    completeLocalSkillSourceSave(
        workspaceViewModel = workspaceViewModel,
        snapshot = snapshot,
        name = renamed.skill.name,
        sourceDigest = renamed.sourceDigest
    )
}

private fun completeLocalSkillSourceSave(
    workspaceViewModel: MarkdownWorkspaceViewModel,
    snapshot: MarkdownExportSnapshot,
    name: String,
    sourceDigest: String
): LocalSkillSourceSaveOutcome.Saved {
    val persistedOrigin = MarkdownWorkspaceOrigin.LocalSkill(
        name = name,
        sourceDigest = sourceDigest
    )
    return LocalSkillSourceSaveOutcome.Saved(
        name = name,
        current = workspaceViewModel.completeSourceSave(snapshot, persistedOrigin)
    )
}

private fun localSkillSaveFailureMessage(failure: Throwable): String = when (failure) {
    is IllegalArgumentException -> failure.message ?: "SKILL.md is not valid and was not saved."
    is IOException -> failure.message ?: "Could not save the local skill source."
    else -> throw failure
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
    var pendingRename by remember { mutableStateOf<LocalSkillSourceSaveOutcome.RenameRequired?>(null) }

    fun showSaveOutcome(outcome: LocalSkillSourceSaveOutcome) {
        when (outcome) {
            is LocalSkillSourceSaveOutcome.Failed -> onMessage(outcome.message)
            is LocalSkillSourceSaveOutcome.RenameRequired -> pendingRename = outcome
            is LocalSkillSourceSaveOutcome.Saved -> onMessage(
                if (outcome.current) {
                    "Saved '${outcome.name}' to local skills."
                } else {
                    "Saved '${outcome.name}' snapshot; newer edits remain unsaved."
                }
            )
        }
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
                        showSaveOutcome(
                            persistLocalSkillSource(
                                store = store,
                                workspaceViewModel = workspaceViewModel,
                                snapshot = snapshot,
                                snapshotOrigin = snapshotOrigin
                            )
                        )
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

    pendingRename?.let { rename ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename '${rename.existingName}'?") },
            text = {
                Text(
                    "The edited SKILL.md changes its portable skill name to '${rename.newName}'. " +
                        "Rename the local skill and keep its current enabled/disabled state?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRename = null
                        val snapshot = workspaceViewModel.beginExport()
                        val snapshotOrigin = snapshot?.origin as? MarkdownWorkspaceOrigin.LocalSkill
                        if (snapshot == null || snapshotOrigin == null || snapshotOrigin.name != rename.existingName) {
                            snapshot?.let(workspaceViewModel::failExport)
                            onMessage("Local skill source changed before rename confirmation. Try Save source again.")
                            return@Button
                        }
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            showSaveOutcome(
                                persistLocalSkillRename(
                                    store = store,
                                    workspaceViewModel = workspaceViewModel,
                                    snapshot = snapshot,
                                    snapshotOrigin = snapshotOrigin
                                )
                            )
                        }
                    },
                    modifier = Modifier.testTag("confirm_local_skill_rename")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
