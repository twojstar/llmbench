package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class StreambenchStationBrowserTest {
    @Test
    fun searchMatchesUsefulStationMetadataCaseInsensitively() {
        val rows = rows()

        assertEquals(
            listOf("News PL"),
            filterStreambenchStationRows(
                rows = rows,
                query = "polish",
                view = StreambenchStationView.ALL,
                favoriteKeys = emptySet(),
                recentKeys = emptyList()
            ).map { it.entry.title }
        )
    }

    @Test
    fun favoritesUseCurrentPlaylistOrder() {
        val rows = rows()

        assertEquals(
            listOf("Jazz", "Rock"),
            filterStreambenchStationRows(
                rows = rows,
                query = "",
                view = StreambenchStationView.FAVORITES,
                favoriteKeys = setOf(KEY_JAZZ, KEY_ROCK),
                recentKeys = emptyList()
            ).map { it.entry.title }
        )
    }

    @Test
    fun recentsUseStoredRecencyOrderAndIgnoreMissingStations() {
        val rows = rows()

        assertEquals(
            listOf("Rock", "News PL"),
            filterStreambenchStationRows(
                rows = rows,
                query = "",
                view = StreambenchStationView.RECENT,
                favoriteKeys = emptySet(),
                recentKeys = listOf(KEY_ROCK, "missing", KEY_NEWS)
            ).map { it.entry.title }
        )
    }

    private fun rows(): List<StreambenchStationRow> = listOf(
        row(KEY_NEWS, 0, "News PL", "News", "PL", "Polish"),
        row(KEY_JAZZ, 1, "Jazz", "Music", "US", "English"),
        row(KEY_ROCK, 2, "Rock", "Music", "GB", "English")
    )

    private fun row(
        key: String,
        index: Int,
        title: String,
        group: String,
        country: String,
        language: String
    ) = StreambenchStationRow(
        entry = StreambenchPlaylistEntry(
            id = title,
            url = "https://radio.example/$index",
            title = title,
            group = group,
            logo = "",
            country = country,
            language = language,
            quality = "",
            radio = true,
            providerId = "local",
            providerLabel = "Local"
        ),
        key = key,
        sourceIndex = index
    )

    private companion object {
        const val KEY_NEWS = "news"
        const val KEY_JAZZ = "jazz"
        const val KEY_ROCK = "rock"
    }
}
