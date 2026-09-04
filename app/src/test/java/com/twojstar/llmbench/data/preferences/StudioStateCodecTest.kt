package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.ProfileOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
            selectedOverlayId = customOverlay.id,
            customOverlays = listOf(customOverlay),
            language = "pl"
        )

        assertEquals(snapshot, StudioStateCodec.decode(StudioStateCodec.encode(snapshot)))
    }

    @Test
    fun malformedSnapshotFallsBackToNoState() {
        assertNull(StudioStateCodec.decode("{ definitely-not-json"))
        assertNull(StudioStateCodec.decode(null))
        assertNull(StudioStateCodec.decode(""))
    }

    @Test
    fun unknownFutureFieldsDoNotBreakRestore() {
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
