package com.twojstar.llmbench.data.streambench

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

enum class StreambenchPlaybackRejection {
    INVALID_STREAM_URL
}

/**
 * Sanitized playback candidate. This is not authorization to connect.
 *
 * Platform playback must route this request through a network loader that resolves the hostname and
 * rejects private, loopback, link-local and otherwise non-public destinations immediately before the
 * socket is opened.
 */
data class StreambenchPlaybackRequest(
    val url: String,
    val title: String,
    val group: String,
    val radio: Boolean
) {
    override fun toString(): String =
        "StreambenchPlaybackRequest(radio=$radio, stream=<redacted>, metadata=<redacted>)"
}

sealed interface StreambenchPlaybackRequestActionResult {
    /** Eligible only for a guarded loader that performs resolved-address validation before connect. */
    data class EligibleForGuardedLoader(
        val request: StreambenchPlaybackRequest
    ) : StreambenchPlaybackRequestActionResult {
        override fun toString(): String =
            "StreambenchPlaybackRequestActionResult.EligibleForGuardedLoader(request=<redacted>)"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : StreambenchPlaybackRequestActionResult

    data class Rejected(
        val reason: StreambenchPlaybackRejection
    ) : StreambenchPlaybackRequestActionResult
}

/**
 * Side-effect-free policy and sanitization boundary before a guarded platform loader sees a stream.
 *
 * The action promotes the registry-declared NETWORK scope to required, requires live connectivity,
 * applies the canonical strict remote-stream URL policy, and bounds user-controlled display metadata
 * without splitting Unicode surrogate pairs. It intentionally does not resolve DNS or perform a
 * network request; resolved-address enforcement belongs to the loader that owns the actual socket.
 */
object StreambenchPlaybackRequestAction {
    const val MAX_TITLE_CHARS: Int = 160
    const val MAX_GROUP_CHARS: Int = 96

    private val playbackPermissions = setOf(BenchToolPermission.NETWORK)

    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>,
        networkAvailable: Boolean
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.STREAMBENCH_PLAYER.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.MEDIA_STREAM,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = networkAvailable,
        actionRequiredPermissions = playbackPermissions
    )

    fun execute(
        entry: StreambenchPlaylistEntry,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>,
        networkAvailable: Boolean
    ): StreambenchPlaybackRequestActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions,
            networkAvailable = networkAvailable
        )
        if (!availability.canOffer) {
            return StreambenchPlaybackRequestActionResult.Blocked(availability)
        }

        val validatedUrl = StreambenchM3uParser.validateRemotePlaybackUrl(entry.url)
            ?: return StreambenchPlaybackRequestActionResult.Rejected(
                StreambenchPlaybackRejection.INVALID_STREAM_URL
            )

        return StreambenchPlaybackRequestActionResult.EligibleForGuardedLoader(
            StreambenchPlaybackRequest(
                url = validatedUrl,
                title = boundedMetadata(entry.title, MAX_TITLE_CHARS),
                group = boundedMetadata(entry.group, MAX_GROUP_CHARS),
                radio = entry.radio
            )
        )
    }

    private fun boundedMetadata(value: String, maxUtf16Units: Int): String = buildString {
        var index = 0
        while (index < value.length && length < maxUtf16Units) {
            val character = value[index]
            when {
                character < ' ' || character == '\u007F' -> index += 1
                character in '\uD800'..'\uDBFF' -> {
                    val lowSurrogate = value.getOrNull(index + 1)
                    if (lowSurrogate != null && lowSurrogate in '\uDC00'..'\uDFFF') {
                        if (length + 2 > maxUtf16Units) break
                        append(character)
                        append(lowSurrogate)
                        index += 2
                    } else {
                        index += 1
                    }
                }
                character in '\uDC00'..'\uDFFF' -> index += 1
                else -> {
                    append(character)
                    index += 1
                }
            }
        }
    }.trim()
}
