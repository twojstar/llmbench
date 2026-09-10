package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreambenchImportedPlaylistActionTest {
    @Test
    fun missingContentGrantBlocksBeforePlaylistParsing() {
        val oversized = "x".repeat(StreambenchM3uParser.MAX_SOURCE_UTF16_UNITS + 1)

        val result = StreambenchImportedPlaylistAction.execute(
            source = oversized,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = emptySet()
        )

        val blocked = assertIs<StreambenchImportedPlaylistActionResult.Blocked>(result)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.availability.missingRequiredPermissions
        )
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION),
            blocked.availability.blockers
        )
    }

    @Test
    fun authorizedPlaylistImportParsesOfflineWithoutNetworkGrant() {
        val source = """
            #EXTM3U
            #EXTINF:-1 group-title="Radio",Station One
            https://stream.example/live
        """.trimIndent()

        val result = StreambenchImportedPlaylistAction.execute(
            source = source,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val completed = assertIs<StreambenchImportedPlaylistActionResult.Completed>(result)
        assertEquals(1, completed.entries.size)
        assertEquals("Station One", completed.entries.single().title)
        assertEquals("https://stream.example/live", completed.entries.single().url)
        assertEquals("Radio", completed.entries.single().group)
    }

    @Test
    fun unsupportedSurfaceIsRejectedByCanonicalBenchPolicy() {
        val availability = StreambenchImportedPlaylistAction.availability(
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        assertFalse(availability.canOffer)
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.UNSUPPORTED_SURFACE),
            availability.blockers
        )
    }

    @Test
    fun parserLimitFailureReturnsTypedRejection() {
        val oversized = "x".repeat(StreambenchM3uParser.MAX_SOURCE_UTF16_UNITS + 1)

        val result = StreambenchImportedPlaylistAction.execute(
            source = oversized,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val rejected = assertIs<StreambenchImportedPlaylistActionResult.Rejected>(result)
        assertEquals(StreambenchPlaylistImportRejection.PARSER_REJECTED_INPUT, rejected.reason)
    }

    @Test
    fun completedDebugStringRedactsPlaylistContent() {
        val secretTitle = "secret station"
        val secretUrl = "https://stream.example/live?token=private"
        val result = StreambenchImportedPlaylistAction.execute(
            source = "#EXTINF:-1,$secretTitle\n$secretUrl",
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val completed = assertIs<StreambenchImportedPlaylistActionResult.Completed>(result)
        val debug = completed.toString()
        assertFalse(secretTitle in debug)
        assertFalse(secretUrl in debug)
        assertFalse("token=private" in debug)
        assertTrue("entries=1" in debug)
        assertTrue("content=<redacted>" in debug)
    }
}
