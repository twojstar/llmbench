package com.twojstar.llmbench.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private data class StreambenchImportUiResult(
    val entries: List<StreambenchPlaylistEntry>,
    val message: String
)

internal fun updatedBenchSelection(
    current: Set<BuiltInBenchTool>,
    tool: BuiltInBenchTool,
    enabled: Boolean
): Set<BuiltInBenchTool> = current.toMutableSet().apply {
    if (enabled) add(tool) else remove(tool)
}.toSet()

private suspend fun importStreambenchPlaylist(
    context: Context,
    uri: Uri,
    isEnabled: Boolean
): StreambenchImportUiResult = runCatching {
    val opened = TextDocumentFileAccess.import(
        context = context,
        uri = uri,
        fallbackName = "playlist.m3u"
    )
    when (
        val result = opened.executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = isEnabled,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )
    ) {
        is StreambenchImportedPlaylistActionResult.Completed -> {
            val suffix = if (result.entries.size == 1) "station" else "stations"
            StreambenchImportUiResult(result.entries, "Loaded ${result.entries.size} $suffix locally.")
        }
        is StreambenchImportedPlaylistActionResult.Blocked -> StreambenchImportUiResult(
            emptyList(),
            "Playlist import is blocked by the current Bench policy."
        )
        is StreambenchImportedPlaylistActionResult.Rejected -> StreambenchImportUiResult(
            emptyList(),
            "Could not import this playlist. Check its format and size."
        )
    }
}.getOrElse { error ->
    if (error is CancellationException) throw error
    StreambenchImportUiResult(
        entries = emptyList(),
        message = when (error) {
            is SecurityException -> "LlmBench could not access the selected playlist."
            is IOException -> error.message ?: "Could not read the selected playlist."
            else -> "Could not import the selected playlist."
        }
    )
}

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
        uri?.let { selectedUri ->
            scope.launch {
                streambenchImporting = true
                val imported = importStreambenchPlaylist(
                    context = context,
                    uri = selectedUri,
                    isEnabled = BuiltInBenchTool.STREAMBENCH_PLAYER in enabledTools
                )
                if (BuiltInBenchTool.STREAMBENCH_PLAYER in store.loadEnabledTools()) {
                    streambenchEntries = imported.entries
                    streambenchImportMessage = imported.message
                } else {
                    streambenchEntries = emptyList()
                    streambenchImportMessage = null
                }
                streambenchImporting = false
            }
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
                                    val updated = updatedBenchSelection(enabledTools, tool, shouldEnable)
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
                                    onClick = { streambenchImportLauncher.launch(STREAMBENCH_PLAYLIST_MIME_TYPES) },
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
                                        "• ${entry.title.take(160)}${entry.group.takeIf(String::isNotBlank)?.let { " · ${it.take(96)}" }.orEmpty()}",
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
