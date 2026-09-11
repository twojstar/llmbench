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
    fun mediaAndPlayingStateStayCoherent() {
        StreambenchPlaybackState.setMedia(
            title = "Station One",
            group = "Radio"
        )

        val loaded = StreambenchPlaybackState.state.value
        assertTrue(loaded.active)
        assertEquals("Station One", loaded.title)
        assertEquals("Radio", loaded.group)
        assertFalse(loaded.isPlaying)

        StreambenchPlaybackState.setPlaying(true)

        assertTrue(StreambenchPlaybackState.state.value.isPlaying)
    }

    @Test
    fun clearResetsMiniPlayerSnapshot() {
        StreambenchPlaybackState.setMedia("Station One", "Radio")
        StreambenchPlaybackState.setPlaying(true)

        StreambenchPlaybackState.clear()

        assertEquals(StreambenchPlaybackSnapshot(), StreambenchPlaybackState.state.value)
    }
}
