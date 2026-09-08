package com.twojstar.llmbench.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.skills.LocalSkillLibraryStore
import com.twojstar.llmbench.data.skills.LocalSkillSummary
import java.io.IOException
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
    val scope = rememberCoroutineScope()
    var skills by mutableStateOfRememberedSummaries()
    var busySkill by mutableStateOfRememberedBusySkill()

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
                        horizontalArrangement = Arrangement.End,
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
                                            onViewSource(skill.name, document.source)
                                        }
                                    } finally {
                                        busySkill = null
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("View source")
                        }
                        Spacer(Modifier.width(8.dp))
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

@Composable
private fun mutableStateOfRememberedSummaries() =
    androidx.compose.runtime.remember { mutableStateOf<List<LocalSkillSummary>>(emptyList()) }

@Composable
private fun mutableStateOfRememberedBusySkill() =
    androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
