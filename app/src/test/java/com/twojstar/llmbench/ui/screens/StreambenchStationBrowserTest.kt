package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class StreambenchStationBrowserTest {
    @Test
    fun searchMatchesUsefulStationMetadataCaseInsensitively() {
        val rows = rows()

        assertEquals(
            listOf(TITLE_NEWS),
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
            listOf(TITLE_JAZZ, TITLE_ROCK),
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
            listOf(TITLE_ROCK, TITLE_NEWS),
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
        row(KEY_NEWS, 0, TITLE_NEWS, "News", "PL", "Polish"),
        row(KEY_JAZZ, 1, TITLE_JAZZ, GROUP_MUSIC, "US", LANGUAGE_ENGLISH),
        row(KEY_ROCK, 2, TITLE_ROCK, GROUP_MUSIC, "GB", LANGUAGE_ENGLISH)
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
            providerId = PROVIDER_LOCAL,
            providerLabel = PROVIDER_LOCAL
        ),
        key = key,
        sourceIndex = index
    )

    private companion object {
        const val KEY_NEWS = "news"
        const val KEY_JAZZ = "jazz"
        const val KEY_ROCK = "rock"
        const val TITLE_NEWS = "News PL"
        const val TITLE_JAZZ = "Jazz"
        const val TITLE_ROCK = "Rock"
        const val GROUP_MUSIC = "Music"
        const val LANGUAGE_ENGLISH = "English"
        const val PROVIDER_LOCAL = "local"
    }
}
