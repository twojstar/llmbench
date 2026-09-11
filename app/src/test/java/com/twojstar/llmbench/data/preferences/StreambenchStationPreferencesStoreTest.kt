package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreambenchStationPreferencesStoreTest {
    @Test
    fun stationKeyIsDeterministicAndDoesNotPersistRawUrl() {
        val entry = station("https://radio.example/live?token=secret")

        val first = streambenchStationKey(entry)
        val second = streambenchStationKey(entry)

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(first.contains("secret"))
        assertNotEquals(first, streambenchStationKey(station("https://radio.example/other")))
    }

    @Test
    fun favoriteKeysRejectMalformedValues() {
        val valid = hexKey(7)

        assertEquals(setOf(valid), resolveStreambenchFavoriteKeys(setOf(valid, "not-a-key")))
    }

    @Test
    fun recentKeysStayOrderedDistinctAndBounded() {
        val keys = (0 until STREAMBENCH_MAX_RECENTS + 5).map(::hexKey)
        val encoded = (keys + keys.first()).joinToString(",")

        val decoded = decodeStreambenchRecentKeys(encoded)

        assertEquals(STREAMBENCH_MAX_RECENTS, decoded.size)
        assertEquals(keys.take(STREAMBENCH_MAX_RECENTS), decoded)
    }

    private fun station(url: String) = StreambenchPlaylistEntry(
        id = "station-id",
        url = url,
        title = "Station",
        group = "Radio",
        logo = "",
        country = "PL",
        language = "Polish",
        quality = "",
        radio = true,
        providerId = "local",
        providerLabel = "Local"
    )

    private fun hexKey(index: Int): String = index.toString(16).padStart(64, '0')
}
