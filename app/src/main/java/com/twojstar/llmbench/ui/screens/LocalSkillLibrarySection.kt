package com.twojstar.llmbench.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.document.MarkdownDocumentFileAccess
import com.twojstar.llmbench.data.document.TextDocument
import com.twojstar.llmbench.data.document.TextDocumentCodec
import com.twojstar.llmbench.data.skills.LocalSkillLibraryStore
import com.twojstar.llmbench.data.skills.LocalSkillSummary
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private sealed interface LocalSkillExportResult {
    data object Missing : LocalSkillExportResult
    data object Exported : LocalSkillExportResult
    data class Failed(val message: String) : LocalSkillExportResult
}

private suspend fun exportLocalSkill(
    context: Context,
    uri: Uri,
    skillName: String,
    store: LocalSkillLibraryStore
): LocalSkillExportResult {
    val stored = store.read(skillName) ?: return LocalSkillExportResult.Missing
    val source = stored.source
    val failure = runCatching {
        MarkdownDocumentFileAccess.export(
            context = context,
            uri = uri,
            document = TextDocument(
                text = source,
                hadUtf8Bom = false,
                lineEndings = TextDocumentCodec.detectLineEndings(source)
            )
        )
    }.exceptionOrNull()
    if (failure == null) return LocalSkillExportResult.Exported

    currentCoroutineContext().ensureActive()
    return when (failure) {
        is IOException, is SecurityException -> LocalSkillExportResult.Failed(
            failure.message ?: "Could not export local skill."
        )
        else -> throw failure
    }
}

@Composable
internal fun LocalSkillLibrarySection(
    store: LocalSkillLibraryStore,
    searchQuery: String,
    refreshToken: Int,
    onCountChanged: (Int) -> Unit,
    onViewSource: (String, String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var skills by remember { mutableStateOf<List<LocalSkillSummary>>(emptyList()) }
    var busySkill by remember { mutableStateOf<String?>(null) }
    var pendingExportSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveSkill by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun reloadSkills() {
        skills = store.load()
        onCountChanged(skills.size)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val skillName = pendingExportSkill
        pendingExportSkill = null
        if (uri != null && skillName != null) {
            scope.launch {
                busySkill = skillName
                try {
                    when (val result = exportLocalSkill(context, uri, skillName, store)) {
                        LocalSkillExportResult.Missing -> {
                            reloadSkills()
                            onMessage("Local skill is no longer available.")
                        }
                        LocalSkillExportResult.Exported -> onMessage("Exported '$skillName' as SKILL.md.")
                        is LocalSkillExportResult.Failed -> onMessage(result.message)
                    }
                } finally {
                    busySkill = null
                }
            }
        }
    }

    LaunchedEffect(store, refreshToken) {
        reloadSkills()
    }

    val filtered = skills.filter { skill ->
        skill.name.contains(searchQuery, ignoreCase = true) ||
            skill.description.contains(searchQuery, ignoreCase = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LocalSkillLibraryHeader(skills.size)
        LocalSkillLibraryEmptyMessage(
            allSkillsEmpty = skills.isEmpty(),
            filteredSkillsEmpty = filtered.isEmpty()
        )
        filtered.forEach { skill ->
            LocalSkillCard(
                skill = skill,
                enabled = busySkill == null,
                onView = {
                    scope.launch {
                        busySkill = skill.name
                        try {
                            val document = store.read(skill.name)
                            if (document == null) {
                                reloadSkills()
                                onMessage("Local skill is no longer available.")
                            } else {
                                onViewSource(skill.name, boundedSkillSourceForDisplay(document.source))
                            }
                        } finally {
                            busySkill = null
                        }
                    }
                },
                onExport = {
                    pendingExportSkill = skill.name
                    try {
                        exportLauncher.launch("SKILL.md")
                    } catch (_: ActivityNotFoundException) {
                        pendingExportSkill = null
                        onMessage("No document picker is available for export.")
                    }
                },
                onRemove = { pendingRemoveSkill = skill.name }
            )
        }
    }

    pendingRemoveSkill?.let { skillName ->
        ConfirmRemoveLocalSkillDialog(
            skillName = skillName,
            onDismiss = { pendingRemoveSkill = null },
            onConfirm = {
                pendingRemoveSkill = null
                scope.launch {
                    busySkill = skillName
                    try {
                        store.remove(skillName)
                        reloadSkills()
                        onMessage("Removed '$skillName' from local skills.")
                    } catch (error: IOException) {
                        onMessage(error.message ?: "Could not remove local skill.")
                    } finally {
                        busySkill = null
                    }
                }
            }
        )
    }
}

@Composable
private fun LocalSkillLibraryHeader(skillCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FolderCopy, contentDescription = null)
        Text(
            text = "Local skills ($skillCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LocalSkillLibraryEmptyMessage(
    allSkillsEmpty: Boolean,
    filteredSkillsEmpty: Boolean
) {
    val message = when {
        allSkillsEmpty -> "No local skills saved yet. Preview a valid SKILL.md above to add one."
        filteredSkillsEmpty -> "No local skills match this search."
        else -> return
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LocalSkillCard(
    skill: LocalSkillSummary,
    enabled: Boolean,
    onView: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(enabled = enabled, onClick = onView) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("View")
                }
                OutlinedButton(enabled = enabled, onClick = onExport) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(enabled = enabled, onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun ConfirmRemoveLocalSkillDialog(
    skillName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove '$skillName'?") },
        text = {
            Text(
                "This permanently deletes the local stored copy of SKILL.md. " +
                    "Export it first if this is your only copy."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
