package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.preferences.streambenchStationKey
import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry

private const val MAX_SEARCH_QUERY_LENGTH = 128

internal enum class StreambenchStationView {
    ALL,
    FAVORITES,
    RECENT
}

internal data class StreambenchStationRow(
    val entry: StreambenchPlaylistEntry,
    val key: String,
    val sourceIndex: Int
)

internal fun filterStreambenchStationRows(
    rows: List<StreambenchStationRow>,
    query: String,
    view: StreambenchStationView,
    favoriteKeys: Set<String>,
    recentKeys: List<String>
): List<StreambenchStationRow> {
    val ordered = when (view) {
        StreambenchStationView.ALL -> rows
        StreambenchStationView.FAVORITES -> rows.filter { it.key in favoriteKeys }
        StreambenchStationView.RECENT -> {
            val firstByKey = buildMap {
                rows.forEach { row ->
                    if (row.key !in this) put(row.key, row)
                }
            }
            recentKeys.mapNotNull(firstByKey::get)
        }
    }
    val needle = query.trim()
    if (needle.isEmpty()) return ordered
    return ordered.filter { row -> row.entry.matchesStreambenchQuery(needle) }
}

private fun StreambenchPlaylistEntry.matchesStreambenchQuery(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        group.contains(query, ignoreCase = true) ||
        country.contains(query, ignoreCase = true) ||
        language.contains(query, ignoreCase = true) ||
        quality.contains(query, ignoreCase = true) ||
        providerLabel.contains(query, ignoreCase = true)

@Composable
internal fun StreambenchStationBrowser(
    entries: List<StreambenchPlaylistEntry>,
    favoriteKeys: Set<String>,
    recentKeys: List<String>,
    onToggleFavorite: (String) -> Unit,
    onPlay: (StreambenchPlaylistEntry, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(entries) {
        entries.mapIndexed { index, entry ->
            StreambenchStationRow(entry, streambenchStationKey(entry), index)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var viewName by rememberSaveable { mutableStateOf(StreambenchStationView.ALL.name) }
    val view = StreambenchStationView.entries.firstOrNull { it.name == viewName }
        ?: StreambenchStationView.ALL
    val visibleRows = remember(rows, query, view, favoriteKeys, recentKeys) {
        filterStreambenchStationRows(rows, query, view, favoriteKeys, recentKeys)
    }
    val favoriteCount = remember(rows, favoriteKeys) { rows.count { it.key in favoriteKeys } }
    val recentCount = remember(rows, recentKeys) {
        val available = rows.asSequence().map { it.key }.toHashSet()
        recentKeys.count { it in available }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(MAX_SEARCH_QUERY_LENGTH) },
            singleLine = true,
            label = { Text("Search stations") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear station search")
                    }
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("streambench_station_search")
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            StationViewChip(
                label = "All (${rows.size})",
                selected = view == StreambenchStationView.ALL,
                onClick = { viewName = StreambenchStationView.ALL.name }
            )
            StationViewChip(
                label = "Favorites ($favoriteCount)",
                selected = view == StreambenchStationView.FAVORITES,
                onClick = { viewName = StreambenchStationView.FAVORITES.name }
            )
            StationViewChip(
                label = "Recent selections ($recentCount)",
                selected = view == StreambenchStationView.RECENT,
                onClick = { viewName = StreambenchStationView.RECENT.name }
            )
        }
        if (visibleRows.isEmpty()) {
            Text(
                when {
                    query.isNotBlank() -> "No stations match this search."
                    view == StreambenchStationView.FAVORITES -> "No favorites in this playlist yet."
                    view == StreambenchStationView.RECENT -> "No recent selections in this playlist yet."
                    else -> "No stations available."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .testTag("streambench_playlist_entries")
            ) {
                items(
                    items = visibleRows,
                    key = { row -> "${row.key}:${row.sourceIndex}" }
                ) { row ->
                    StreambenchEntryRow(
                        entry = row.entry,
                        isFavorite = row.key in favoriteKeys,
                        onToggleFavorite = { onToggleFavorite(row.key) },
                        onPlay = { onPlay(row.entry, row.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StationViewChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
