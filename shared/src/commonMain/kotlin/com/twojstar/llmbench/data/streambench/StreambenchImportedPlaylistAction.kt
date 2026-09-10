package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

enum class StreambenchPlaylistImportRejection {
    PARSER_REJECTED_INPUT
}

/** Result of one explicit, policy-gated parse of an already opened user-selected playlist. */
sealed interface StreambenchImportedPlaylistActionResult {
    data class Completed(
        val entries: List<StreambenchPlaylistEntry>
    ) : StreambenchImportedPlaylistActionResult {
        override fun toString(): String =
            "StreambenchImportedPlaylistActionResult.Completed(entries=${entries.size}, content=<redacted>)"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : StreambenchImportedPlaylistActionResult

    data class Rejected(
        val reason: StreambenchPlaylistImportRejection
    ) : StreambenchImportedPlaylistActionResult
}

/**
 * Explicit-user Streambench `PLAYLIST -> PLAYLIST` boundary for already opened local M3U/M3U8 text.
 *
 * Import requires the registry-declared user-selected-content grant, but parsing itself is local and
 * does not promote NETWORK to a required permission. Playback remains a separate future action that
 * must explicitly require network scope and live connectivity before opening any parsed stream URL.
 */
object StreambenchImportedPlaylistAction {
    private val importedPlaylistPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)

    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.PLAYLIST,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false,
        actionRequiredPermissions = importedPlaylistPermissions
    )

    fun execute(
        source: String,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>,
        allowArtwork: Boolean = false
    ): StreambenchImportedPlaylistActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return StreambenchImportedPlaylistActionResult.Blocked(availability)
        }

        val entries = try {
            StreambenchM3uParser.parse(
                source = source,
                allowArtwork = allowArtwork
            )
        } catch (_: IllegalArgumentException) {
            return StreambenchImportedPlaylistActionResult.Rejected(
                StreambenchPlaylistImportRejection.PARSER_REJECTED_INPUT
            )
        }
        return StreambenchImportedPlaylistActionResult.Completed(entries.toList())
    }
}
