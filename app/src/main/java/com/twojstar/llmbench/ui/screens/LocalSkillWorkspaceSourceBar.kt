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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface LocalSkillSourceSaveOutcome {
    data class Saved(val name: String, val current: Boolean) : LocalSkillSourceSaveOutcome
    data class RenameRequired(
        val existingName: String,
        val newName: String,
        val revision: Long
    ) : LocalSkillSourceSaveOutcome
    data class Failed(val message: String) : LocalSkillSourceSaveOutcome
}

private data class ConfirmedLocalSkillRename(
    val snapshot: MarkdownExportSnapshot,
    val origin: MarkdownWorkspaceOrigin.LocalSkill
)

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
                newName = failure.newName,
                revision = snapshot.revision
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

private fun routeLocalSkillSourceSaveOutcome(
    outcome: LocalSkillSourceSaveOutcome,
    onMessage: (String) -> Unit,
    onRenameRequired: (LocalSkillSourceSaveOutcome.RenameRequired) -> Unit
) {
    when (outcome) {
        is LocalSkillSourceSaveOutcome.Failed -> onMessage(outcome.message)
        is LocalSkillSourceSaveOutcome.RenameRequired -> onRenameRequired(outcome)
        is LocalSkillSourceSaveOutcome.Saved -> onMessage(
            if (outcome.current) {
                "Saved '${outcome.name}' to local skills."
            } else {
                "Saved '${outcome.name}' snapshot; newer edits remain unsaved."
            }
        )
    }
}

private fun prepareConfirmedLocalSkillRename(
    workspaceViewModel: MarkdownWorkspaceViewModel,
    rename: LocalSkillSourceSaveOutcome.RenameRequired
): ConfirmedLocalSkillRename? {
    val snapshot = workspaceViewModel.beginExport() ?: return null
    val origin = snapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill
    if (
        origin == null ||
        origin.name != rename.existingName ||
        snapshot.revision != rename.revision
    ) {
        workspaceViewModel.failExport(snapshot)
        return null
    }
    return ConfirmedLocalSkillRename(snapshot = snapshot, origin = origin)
}

private fun CoroutineScope.launchLocalSkillSourceSave(
    store: LocalSkillLibraryStore,
    workspaceViewModel: MarkdownWorkspaceViewModel,
    onOutcome: (LocalSkillSourceSaveOutcome) -> Unit
) {
    val snapshot = workspaceViewModel.beginExport() ?: return
    val origin = snapshot.origin as? MarkdownWorkspaceOrigin.LocalSkill
    if (origin == null) {
        workspaceViewModel.failExport(snapshot)
        return
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        onOutcome(
            persistLocalSkillSource(
                store = store,
                workspaceViewModel = workspaceViewModel,
                snapshot = snapshot,
                snapshotOrigin = origin
            )
        )
    }
}

private fun CoroutineScope.launchConfirmedLocalSkillRename(
    store: LocalSkillLibraryStore,
    workspaceViewModel: MarkdownWorkspaceViewModel,
    confirmed: ConfirmedLocalSkillRename,
    onOutcome: (LocalSkillSourceSaveOutcome) -> Unit
) = launch(start = CoroutineStart.UNDISPATCHED) {
    onOutcome(
        persistLocalSkillRename(
            store = store,
            workspaceViewModel = workspaceViewModel,
            snapshot = confirmed.snapshot,
            snapshotOrigin = confirmed.origin
        )
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
    var pendingRename by remember { mutableStateOf<LocalSkillSourceSaveOutcome.RenameRequired?>(null) }
    val onOutcome: (LocalSkillSourceSaveOutcome) -> Unit = { outcome ->
        routeLocalSkillSourceSaveOutcome(
            outcome = outcome,
            onMessage = onMessage,
            onRenameRequired = { pendingRename = it }
        )
    }

    LocalSkillSourceBarContent(
        skillName = origin.name,
        saveEnabled = uiState.isDirty && !uiState.isBusy && !uiState.isLargeDocumentReadOnly,
        onSave = {
            scope.launchLocalSkillSourceSave(
                store = store,
                workspaceViewModel = workspaceViewModel,
                onOutcome = onOutcome
            )
        }
    )

    pendingRename?.let { rename ->
        ConfirmLocalSkillRenameDialog(
            rename = rename,
            onDismiss = { pendingRename = null },
            onConfirm = {
                pendingRename = null
                val confirmed = prepareConfirmedLocalSkillRename(workspaceViewModel, rename)
                if (confirmed == null) {
                    onMessage("Local skill source changed before rename confirmation. Try Save source again.")
                } else {
                    scope.launchConfirmedLocalSkillRename(
                        store = store,
                        workspaceViewModel = workspaceViewModel,
                        confirmed = confirmed,
                        onOutcome = onOutcome
                    )
                }
            }
        )
    }
}

@Composable
private fun LocalSkillSourceBarContent(
    skillName: String,
    saveEnabled: Boolean,
    onSave: () -> Unit
) {
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
                text = "Local skill: $skillName",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                enabled = saveEnabled,
                onClick = onSave,
                modifier = Modifier.testTag("save_local_skill_source")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save source")
            }
        }
    }
}

@Composable
private fun ConfirmLocalSkillRenameDialog(
    rename: LocalSkillSourceSaveOutcome.RenameRequired,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename '${rename.existingName}'?") },
        text = {
            Text(
                "The edited SKILL.md changes its portable skill name to '${rename.newName}'. " +
                    "Rename the local skill and keep its current enabled/disabled state?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_local_skill_rename")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
