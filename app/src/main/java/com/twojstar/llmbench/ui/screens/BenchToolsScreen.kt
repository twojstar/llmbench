package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.model.BenchToolNetworkBehavior
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.capabilities
import com.twojstar.llmbench.data.preferences.BuiltInBenchPreferencesStore

internal fun updatedBenchSelection(
    current: Set<BuiltInBenchTool>,
    tool: BuiltInBenchTool,
    enabled: Boolean
): Set<BuiltInBenchTool> = current.toMutableSet().apply {
    if (enabled) add(tool) else remove(tool)
}.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { BuiltInBenchPreferencesStore(context.applicationContext) }
    var enabledTools by remember(store) { mutableStateOf(store.loadEnabledTools()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Built-in Benches", fontWeight = FontWeight.Bold)
                        Text(
                            "Enable only the first-party tools you want available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    "Enabling a Bench does not grant file, camera, export or network access. Each action still checks its own policy before running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(BuiltInBenchTool.entries, key = { it.id }) { tool ->
                val capabilities = tool.capabilities()
                val enabled = tool in enabledTools
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bench_tool_${tool.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                capabilities.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                capabilities.networkBehavior.displayLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { shouldEnable ->
                                val updated = updatedBenchSelection(
                                    current = enabledTools,
                                    tool = tool,
                                    enabled = shouldEnable
                                )
                                enabledTools = updated
                                store.saveEnabledTools(updated)
                            },
                            modifier = Modifier.testTag("bench_toggle_${tool.id}")
                        )
                    }
                }
            }
        }
    }
}

private fun BenchToolNetworkBehavior.displayLabel(): String = when (this) {
    BenchToolNetworkBehavior.LOCAL_ONLY -> "Local only"
    BenchToolNetworkBehavior.NETWORK_OPTIONAL -> "Network only when a concrete action requires it"
    BenchToolNetworkBehavior.NETWORK_REQUIRED -> "Network required"
}
