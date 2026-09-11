package com.twojstar.llmbench.data.streambench

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreambenchPlaybackStateTest {
    @After
    fun resetState() {
        StreambenchPlaybackState.clear()
    }

    @Test
    fun mediaAndPlaybackIntentStayCoherent() {
        StreambenchPlaybackState.setMedia(
            title = STATION_TITLE,
            group = STATION_GROUP
        )

        val loaded = StreambenchPlaybackState.state.value
        assertTrue(loaded.active)
        assertEquals(STATION_TITLE, loaded.title)
        assertEquals(STATION_GROUP, loaded.group)
        assertFalse(loaded.playWhenReady)

        StreambenchPlaybackState.setPlayWhenReady(true)

        assertTrue(StreambenchPlaybackState.state.value.playWhenReady)
    }

    @Test
    fun clearResetsMiniPlayerSnapshot() {
        StreambenchPlaybackState.setMedia(STATION_TITLE, STATION_GROUP)
        StreambenchPlaybackState.setPlayWhenReady(true)

        StreambenchPlaybackState.clear()

        assertEquals(StreambenchPlaybackSnapshot(), StreambenchPlaybackState.state.value)
    }

    private companion object {
        const val STATION_TITLE = "Station One"
        const val STATION_GROUP = "Radio"
    }
}
