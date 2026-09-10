package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.document.LineEndingCounts
import com.twojstar.llmbench.data.document.OpenedTextDocument
import com.twojstar.llmbench.data.document.TextDocument
import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenedTextDocumentStreambenchTest {
    private fun opened(source: String) = OpenedTextDocument(
        document = TextDocument(
            text = source,
            hadUtf8Bom = false,
            lineEndings = LineEndingCounts(lf = source.count { it == '\n' }, crlf = 0, cr = 0)
        ),
        displayName = "stations.m3u",
        hasProviderDisplayName = true,
        mimeType = "audio/x-mpegurl"
    )

    @Test
    fun openedPlaylistForwardsTextIntoPortableImportAction() {
        val result = opened("#EXTINF:-1 group-title=\"Radio\",Station One\nhttps://stream.example/live")
            .executeStreambenchPlaylistImportAction(
                surface = BenchToolSurface.COMPANION_UI,
                isEnabled = true,
                grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
            )

        val completed = result as StreambenchImportedPlaylistActionResult.Completed
        assertEquals(1, completed.entries.size)
        assertEquals("Station One", completed.entries.single().title)
        assertEquals("Radio", completed.entries.single().group)
    }

    @Test
    fun disabledBenchRemainsBlockedAtAndroidBridge() {
        val result = opened("https://stream.example/live")
            .executeStreambenchPlaylistImportAction(
                surface = BenchToolSurface.COMPANION_UI,
                isEnabled = false,
                grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
            )

        val blocked = result as StreambenchImportedPlaylistActionResult.Blocked
        assertFalse(blocked.availability.canOffer)
        assertTrue(BenchToolAvailabilityBlocker.DISABLED in blocked.availability.blockers)
    }

    @Test
    fun artworkOptInOnlyForwardsMetadataPolicy() {
        val source = "#EXTINF:-1 tvg-logo=\"https://cdn.example/logo.png\",Station\nhttps://stream.example/live"
        val withoutArtwork = opened(source).executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        ) as StreambenchImportedPlaylistActionResult.Completed
        val withArtwork = opened(source).executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            allowArtwork = true
        ) as StreambenchImportedPlaylistActionResult.Completed

        assertEquals(null, withoutArtwork.entries.single().artworkUrl)
        assertEquals("https://cdn.example/logo.png", withArtwork.entries.single().artworkUrl)
    }
}
