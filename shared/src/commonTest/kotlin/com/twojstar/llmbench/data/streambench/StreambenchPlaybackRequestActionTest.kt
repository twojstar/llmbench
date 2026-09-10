package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreambenchPlaybackRequestActionTest {
    private fun entry(
        url: String = "https://stream.example/live",
        title: String = "Station One",
        group: String = "Radio"
    ) = StreambenchPlaylistEntry(
        id = "station-1",
        url = url,
        title = title,
        group = group,
        logo = "",
        country = "",
        language = "",
        quality = "",
        radio = true,
        providerId = "local",
        providerLabel = "Local"
    )

    @Test
    fun playbackRequiresNetworkGrantAndConnectivityBeforeUrlValidation() {
        val result = StreambenchPlaybackRequestAction.execute(
            entry = entry(url = "not a url"),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = emptySet(),
            networkAvailable = false
        )

        val blocked = assertIs<StreambenchPlaybackRequestActionResult.Blocked>(result)
        assertEquals(setOf(BenchToolPermission.NETWORK), blocked.availability.missingRequiredPermissions)
        assertTrue(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION in blocked.availability.blockers)
        assertTrue(BenchToolAvailabilityBlocker.NETWORK_UNAVAILABLE in blocked.availability.blockers)
    }

    @Test
    fun grantedNetworkStillBlocksWhileOffline() {
        val availability = StreambenchPlaybackRequestAction.availability(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = false
        )

        assertFalse(availability.canOffer)
        assertEquals(setOf(BenchToolAvailabilityBlocker.NETWORK_UNAVAILABLE), availability.blockers)
    }

    @Test
    fun readyRequestRevalidatesUrlAndBoundsDisplayMetadata() {
        val title = "x".repeat(StreambenchPlaybackRequestAction.MAX_TITLE_CHARS + 20) + "\u0000hidden"
        val group = "g".repeat(StreambenchPlaybackRequestAction.MAX_GROUP_CHARS + 20)
        val result = StreambenchPlaybackRequestAction.execute(
            entry = entry(url = " https://stream.example/live ", title = title, group = group),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        val ready = assertIs<StreambenchPlaybackRequestActionResult.Ready>(result)
        assertEquals("https://stream.example/live", ready.request.url)
        assertEquals(StreambenchPlaybackRequestAction.MAX_TITLE_CHARS, ready.request.title.length)
        assertEquals(StreambenchPlaybackRequestAction.MAX_GROUP_CHARS, ready.request.group.length)
        assertTrue(ready.request.radio)
    }

    @Test
    fun malformedStreamUrlIsRejectedAfterPolicyPasses() {
        val result = StreambenchPlaybackRequestAction.execute(
            entry = entry(url = "file:///etc/passwd"),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        val rejected = assertIs<StreambenchPlaybackRequestActionResult.Rejected>(result)
        assertEquals(StreambenchPlaybackRejection.INVALID_STREAM_URL, rejected.reason)
    }

    @Test
    fun extraPlaylistLinesCannotBeSmuggledIntoPlaybackUrl() {
        val result = StreambenchPlaybackRequestAction.execute(
            entry = entry(url = "https://stream.example/live\nhttps://other.example/live"),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        val rejected = assertIs<StreambenchPlaybackRequestActionResult.Rejected>(result)
        assertEquals(StreambenchPlaybackRejection.INVALID_STREAM_URL, rejected.reason)
    }

    @Test
    fun playbackRequestDebugStringsRedactStreamAndMetadata() {
        val result = StreambenchPlaybackRequestAction.execute(
            entry = entry(url = "https://stream.example/live?token=private", title = "secret station"),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.NETWORK),
            networkAvailable = true
        )

        val ready = assertIs<StreambenchPlaybackRequestActionResult.Ready>(result)
        val debug = ready.toString() + ready.request.toString()
        assertFalse("token=private" in debug)
        assertFalse("secret station" in debug)
        assertTrue("<redacted>" in debug)
    }
}
