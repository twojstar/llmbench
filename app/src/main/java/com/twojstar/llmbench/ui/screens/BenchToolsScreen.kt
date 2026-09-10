package com.twojstar.llmbench.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.document.TextDocumentFileAccess
import com.twojstar.llmbench.data.model.BenchToolNetworkBehavior
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.capabilities
import com.twojstar.llmbench.data.preferences.BuiltInBenchPreferencesStore
import com.twojstar.llmbench.data.streambench.StreambenchImportedPlaylistActionResult
import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import com.twojstar.llmbench.data.streambench.executeStreambenchPlaylistImportAction
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val STREAMBENCH_PLAYLIST_MIME_TYPES = arrayOf(
    "application/vnd.apple.mpegurl",
    "audio/mpegurl",
    "audio/x-mpegurl",
    "text/plain",
    "application/octet-stream"
)

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
    val scope = rememberCoroutineScope()
    val store = remember(context) { BuiltInBenchPreferencesStore(context.applicationContext) }
    var enabledTools by remember(store) { mutableStateOf(store.loadEnabledTools()) }
    var streambenchEntries by remember { mutableStateOf<List<StreambenchPlaylistEntry>>(emptyList()) }
    var streambenchImportMessage by remember { mutableStateOf<String?>(null) }
    var streambenchImporting by remember { mutableStateOf(false) }

    val streambenchImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                streambenchImporting = true
                try {
                    val opened = TextDocumentFileAccess.import(
                        context = context,
                        uri = uri,
                        fallbackName = "playlist.m3u"
                    )
                    when (
                        val result = opened.executeStreambenchPlaylistImportAction(
                            surface = BenchToolSurface.COMPANION_UI,
                            isEnabled = BuiltInBenchTool.STREAMBENCH_PLAYER in enabledTools,
                            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
                        )
                    ) {
                        is StreambenchImportedPlaylistActionResult.Completed -> {
                            streambenchEntries = result.entries
                            val suffix = if (result.entries.size == 1) "station" else "stations"
                            streambenchImportMessage = "Loaded ${result.entries.size} $suffix locally."
                        }
                        is StreambenchImportedPlaylistActionResult.Blocked -> {
                            streambenchEntries = emptyList()
                            streambenchImportMessage = "Playlist import is blocked by the current Bench policy."
                        }
                        is StreambenchImportedPlaylistActionResult.Rejected -> {
                            streambenchEntries = emptyList()
                            streambenchImportMessage = "Could not import this playlist. Check its format and size."
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    streambenchEntries = emptyList()
                    streambenchImportMessage = error.message ?: "Could not read the selected playlist."
                } catch (_: SecurityException) {
                    streambenchEntries = emptyList()
                    streambenchImportMessage = "LlmBench could not access the selected playlist."
                } finally {
                    streambenchImporting = false
                }
            }
        }
    }

    fun launchStreambenchImport() {
        try {
            streambenchImportLauncher.launch(STREAMBENCH_PLAYLIST_MIME_TYPES)
        } catch (_: ActivityNotFoundException) {
            streambenchImportMessage = "No document picker is available."
        }
    }

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
                    Column {
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
                                    if (tool == BuiltInBenchTool.STREAMBENCH_PLAYER && !shouldEnable) {
                                        streambenchEntries = emptyList()
                                        streambenchImportMessage = null
                                    }
                                },
                                modifier = Modifier.testTag("bench_toggle_${tool.id}")
                            )
                        }

                        if (tool == BuiltInBenchTool.STREAMBENCH_PLAYER && enabled) {
                            HorizontalDivider()
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "Import an M3U/M3U8 playlist from Android. Parsing stays local; playback and network access are separate actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = ::launchStreambenchImport,
                                    enabled = !streambenchImporting,
                                    modifier = Modifier.testTag("streambench_import_playlist")
                                ) {
                                    Text(if (streambenchImporting) "Importing…" else "Import playlist")
                                }
                                streambenchImportMessage?.let { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall)
                                }
                                streambenchEntries.take(5).forEach { entry ->
                                    Text(
                                        buildString {
                                            append("• ")
                                            append(entry.title)
                                            entry.group?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (streambenchEntries.size > 5) {
                                    Text(
                                        "+${streambenchEntries.size - 5} more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
