package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioStateCodecTest {
    @Test
    fun roundTripPreservesStudioSnapshot() {
        val base = PresetProfiles.DefaultBaseProfile.copy(
            personality = PresetProfiles.DefaultBaseProfile.personality.copy(
                base = "professional",
                intensity = 3,
                modifiers = PresetProfiles.DefaultBaseProfile.personality.modifiers + ("technical" to 3)
            )
        )
        val customOverlay = ProfileOverlay(
            id = "my-review-profile",
            name = "My Review Profile",
            description = "Strict review settings",
            personalityBase = "professional",
            personalityIntensity = 2,
            modifierOverrides = mapOf("critical" to 3),
            verification = "strict",
            initiative = "proactive"
        )
        val snapshot = StudioStateSnapshot(
            baseProfile = base,
            selectedCustomOverlayIndex = 0,
            customOverlays = listOf(customOverlay),
            language = "pl"
        )

        assertEquals(snapshot, StudioStateCodec.decode(StudioStateCodec.encode(snapshot)))
    }

    @Test
    fun malformedOrUnsupportedSnapshotFallsBackToNoState() {
        assertNull(StudioStateCodec.decode("{ definitely-not-json"))
        assertNull(StudioStateCodec.decode(null))
        assertNull(StudioStateCodec.decode(""))

        val future = StudioStateCodec.encode(
            StudioStateSnapshot(baseProfile = PresetProfiles.DefaultBaseProfile)
        ).replace("\"version\":1", "\"version\":2")
        assertNull(StudioStateCodec.decode(future))
        val futureResult = StudioStateCodec.decodeResult(future)
        assertTrue(futureResult is StudioStateDecodeResult.UnsupportedVersion)
        assertEquals(2, (futureResult as StudioStateDecodeResult.UnsupportedVersion).version)
        assertEquals(
            StudioStateDecodeResult.MissingOrInvalid,
            StudioStateCodec.decodeResult("{ definitely-not-json")
        )
    }

    @Test
    fun unknownFutureFieldsDoNotBreakCurrentVersion() {
        val snapshot = StudioStateSnapshot(
            baseProfile = PresetProfiles.DefaultBaseProfile,
            language = "en"
        )
        val encoded = StudioStateCodec.encode(snapshot)
        val withFutureField = encoded.dropLast(1) + ",\"futureField\":true}"
        val decoded = StudioStateCodec.decode(withFutureField)

        assertEquals(snapshot, decoded)
        assertTrue(withFutureField.contains("futureField"))
    }
}
