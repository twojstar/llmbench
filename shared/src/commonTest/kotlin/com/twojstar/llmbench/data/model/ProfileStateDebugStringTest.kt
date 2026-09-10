package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileStateDebugStringTest {
    @Test
    fun profileDebugStringRedactsUserAuthoredState() {
        val secrets = listOf(
            "private-schema",
            PRIVATE_PROFILE_ID,
            "private-locale",
            "private-personality",
            "private-preamble",
            "private-output-format",
            "private-extension-key",
            "private-extension-value"
        )
        val profile = Profile(
            schemaVersion = secrets[0],
            id = secrets[1],
            locale = secrets[2],
            personality = PersonalityConfig(base = secrets[3]),
            collaboration = CollaborationConfig(preamble = secrets[4]),
            output = OutputConfig(defaultFormat = secrets[5]),
            extensions = mapOf(secrets[6] to mapOf(LOCAL_NOTE_KEY to secrets[7]))
        )

        val debug = profile.toString()

        secrets.forEach { secret -> assertFalse(secret in debug) }
        assertEquals("Profile(<redacted>)", debug)
    }

    @Test
    fun overlayDebugStringRedactsImportedMetadataAndNotes() {
        val secrets = listOf(
            "private-overlay-id",
            "private-overlay-name",
            "private-overlay-description",
            "private-overlay-locale",
            "private-overlay-personality",
            "private-overlay-preamble",
            "private-overlay-note"
        )
        val overlay = ProfileOverlay(
            id = secrets[0],
            name = secrets[1],
            description = secrets[2],
            locale = secrets[3],
            personalityBase = secrets[4],
            preamble = secrets[5],
            customNote = secrets[6]
        )

        val debug = overlay.toString()

        secrets.forEach { secret -> assertFalse(secret in debug) }
        assertEquals("ProfileOverlay(<redacted>)", debug)
    }

    @Test
    fun studioSnapshotDebugStringRedactsProfilesButCodecRemainsLossless() {
        val profileSecret = "persisted-profile-secret"
        val overlaySecret = "persisted-overlay-secret"
        val builtInOverlayId = "private-selected-overlay"
        val language = "private-language"
        val snapshot = StudioStateSnapshot(
            baseProfile = Profile(
                id = PRIVATE_PROFILE_ID,
                extensions = mapOf("local" to mapOf(LOCAL_NOTE_KEY to profileSecret))
            ),
            selectedBuiltInOverlayId = builtInOverlayId,
            selectedCustomOverlayIndex = 2,
            customOverlays = listOf(ProfileOverlay(customNote = overlaySecret)),
            language = language
        )

        val debug = snapshot.toString()
        val decodeDebug = StudioStateDecodeResult.Success(snapshot).toString()

        listOf(profileSecret, overlaySecret, builtInOverlayId, language, PRIVATE_PROFILE_ID).forEach { secret ->
            assertFalse(secret in debug)
            assertFalse(secret in decodeDebug)
        }
        assertTrue("hasSelectedCustomOverlay=true" in debug)
        assertFalse("selectedCustomOverlayIndex=2" in debug)
        assertTrue("customOverlayCount=1" in debug)

        val encoded = StudioStateCodec.encode(snapshot)
        assertTrue(profileSecret in encoded)
        assertTrue(overlaySecret in encoded)
        assertEquals(snapshot, assertNotNull(StudioStateCodec.decode(encoded)))
    }

    private companion object {
        const val PRIVATE_PROFILE_ID = "private-profile-id"
        const val LOCAL_NOTE_KEY = "note"
    }
}
