package com.twojstar.llmbench.ui.screens

import android.content.ActivityNotFoundException
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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val skillName = pendingExportSkill
        pendingExportSkill = null
        if (uri != null && skillName != null) {
            scope.launch {
                busySkill = skillName
                try {
                    val stored = store.read(skillName)
                    if (stored == null) {
                        skills = store.load()
                        onCountChanged(skills.size)
                        onMessage("Local skill is no longer available.")
                    } else {
                        val source = stored.source
                        MarkdownDocumentFileAccess.export(
                            context = context,
                            uri = uri,
                            document = TextDocument(
                                text = source,
                                hadUtf8Bom = false,
                                lineEndings = TextDocumentCodec.detectLineEndings(source)
                            )
                        )
                        onMessage("Exported '$skillName' as SKILL.md.")
                    }
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    onMessage(error.message ?: "Could not export local skill.")
                } finally {
                    busySkill = null
                }
            }
        }
    }

    LaunchedEffect(store, refreshToken) {
        skills = store.load()
        onCountChanged(skills.size)
    }

    val filtered = skills.filter { skill ->
        skill.name.contains(searchQuery, ignoreCase = true) ||
            skill.description.contains(searchQuery, ignoreCase = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderCopy, contentDescription = null)
            Text(
                text = "Local skills (${skills.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (skills.isEmpty()) {
            Text(
                text = "No local skills saved yet. Preview a valid SKILL.md above to add one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (filtered.isEmpty()) {
            Text(
                text = "No local skills match this search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        filtered.forEach { skill ->
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
                        OutlinedButton(
                            enabled = busySkill == null,
                            onClick = {
                                scope.launch {
                                    busySkill = skill.name
                                    try {
                                        val document = store.read(skill.name)
                                        if (document == null) {
                                            skills = store.load()
                                            onCountChanged(skills.size)
                                            onMessage("Local skill is no longer available.")
                                        } else {
                                            onViewSource(
                                                skill.name,
                                                boundedSkillSourceForDisplay(document.source)
                                            )
                                        }
                                    } finally {
                                        busySkill = null
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("View")
                        }
                        OutlinedButton(
                            enabled = busySkill == null,
                            onClick = {
                                pendingExportSkill = skill.name
                                try {
                                    exportLauncher.launch("SKILL.md")
                                } catch (_: ActivityNotFoundException) {
                                    pendingExportSkill = null
                                    onMessage("No document picker is available for export.")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            enabled = busySkill == null,
                            onClick = {
                                scope.launch {
                                    busySkill = skill.name
                                    try {
                                        store.remove(skill.name)
                                        skills = store.load()
                                        onCountChanged(skills.size)
                                        onMessage("Removed '${skill.name}' from local skills.")
                                    } catch (error: IOException) {
                                        onMessage(error.message ?: "Could not remove local skill.")
                                    } finally {
                                        busySkill = null
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}
