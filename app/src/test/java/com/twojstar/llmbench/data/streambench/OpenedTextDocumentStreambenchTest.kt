package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.document.OpenedTextDocument
import com.twojstar.llmbench.data.document.TextDocumentCodec
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenedTextDocumentStreambenchTest {
    @Test
    fun forwardsOpenedSafTextIntoPortablePlaylistAction() {
        val opened = openedPlaylist(
            """
                #EXTM3U
                #EXTINF:-1 group-title="Radio",Station One
                https://stream.example/live
            """.trimIndent()
        )

        val result = opened.executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val completed = assertIs<StreambenchImportedPlaylistActionResult.Completed>(result)
        assertEquals(1, completed.entries.size)
        assertEquals("Station One", completed.entries.single().title)
    }

    @Test
    fun preservesPortableActionPolicyBlockers() {
        val opened = openedPlaylist("https://stream.example/live")

        val result = opened.executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = emptySet()
        )

        assertIs<StreambenchImportedPlaylistActionResult.Blocked>(result)
    }

    @Test
    fun forwardsArtworkOptInWithoutLoadingArtwork() {
        val opened = openedPlaylist(
            "#EXTINF:-1 tvg-logo=\"https://cdn.example.com/logo.png\",Station\n" +
                "https://stream.example/live"
        )

        val result = opened.executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            allowArtwork = true
        )

        val completed = assertIs<StreambenchImportedPlaylistActionResult.Completed>(result)
        assertEquals("https://cdn.example.com/logo.png", completed.entries.single().logo)
    }

    private fun openedPlaylist(source: String): OpenedTextDocument = OpenedTextDocument(
        document = TextDocumentCodec.decodeUtf8(source.encodeToByteArray()),
        displayName = "stations.m3u",
        hasProviderDisplayName = true,
        mimeType = "audio/x-mpegurl"
    )
}
