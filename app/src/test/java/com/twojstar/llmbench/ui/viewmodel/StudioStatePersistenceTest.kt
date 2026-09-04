package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.ProfileOverlay
import com.twojstar.llmbench.data.model.StudioStateCodec
import com.twojstar.llmbench.data.model.StudioStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioStatePersistenceTest {
    @Test
    fun snapshotKeepsCustomOverlaysWithoutDuplicatingBuiltIns() {
        val customOverlay = ProfileOverlay(
            id = "saved-review",
            name = "Saved Review",
            personalityBase = "professional",
            verification = "strict"
        )
        val state = StudioUiState(
            selectedOverlay = customOverlay,
            availableOverlays = PresetProfiles.BuiltInOverlays + customOverlay,
            language = "pl"
        )

        val snapshot = state.toStudioStateSnapshot()

        assertNull(snapshot.selectedBuiltInOverlayId)
        assertEquals(0, snapshot.selectedCustomOverlayIndex)
        assertEquals(listOf(customOverlay), snapshot.customOverlays)
        assertEquals("pl", snapshot.language)
    }

    @Test
    fun builtInSelectionStoresOnlyItsId() {
        val builtIn = PresetProfiles.BuiltInOverlays.first()
        val state = StudioUiState(
            selectedOverlay = builtIn,
            availableOverlays = PresetProfiles.BuiltInOverlays
        )

        val snapshot = state.toStudioStateSnapshot()

        assertEquals(builtIn.id, snapshot.selectedBuiltInOverlayId)
        assertNull(snapshot.selectedCustomOverlayIndex)
        assertTrue(snapshot.customOverlays.isEmpty())
    }

    @Test
    fun collidingAndDuplicateCustomIdsKeepOrderAndSelectedIdentity() {
        val builtIn = PresetProfiles.BuiltInOverlays.first()
        val builtInCollision = ProfileOverlay(
            id = builtIn.id,
            name = builtIn.name,
            description = "User custom with a built-in ID"
        )
        val duplicateOne = ProfileOverlay(id = "duplicate", name = "Duplicate", description = "one")
        val duplicateTwo = ProfileOverlay(id = "duplicate", name = "Duplicate", description = "two")
        val customs = listOf(builtInCollision, duplicateOne, duplicateTwo)
        val state = StudioUiState(
            selectedOverlay = duplicateTwo,
            availableOverlays = PresetProfiles.BuiltInOverlays + customs
        )

        val snapshot = state.toStudioStateSnapshot()

        assertEquals(customs, snapshot.customOverlays)
        assertEquals(2, snapshot.selectedCustomOverlayIndex)
        assertNull(snapshot.selectedBuiltInOverlayId)
    }

    @Test
    fun decodedSnapshotRestoresFullCustomOverlayAndSelectionWithoutIdMatching() {
        val builtInId = PresetProfiles.BuiltInOverlays.first().id
        val firstCustom = ProfileOverlay(
            id = builtInId,
            name = "Imported Full Overlay",
            description = "Preserve every field",
            locale = "pl-PL",
            personalityBase = "cynical",
            personalityIntensity = null,
            modifierOverrides = mapOf("emoji" to null, "critical" to 3),
            adaptationOverrides = mapOf("mirrorLanguage" to false),
            preamble = "always",
            initiative = "proactive",
            verification = "strict",
            questionPolicy = "earlyAlignment",
            assumptionPolicy = "decisive",
            collabBoolOverrides = mapOf("answerFirst" to false),
            knowledgeOverrides = mapOf("requireTraceableClaims" to true),
            defaultFormat = "markdown",
            maxHeadingDepth = null,
            preferShortParagraphs = false,
            tables = "prefer",
            codeExamples = "explanatory",
            citations = "requiredForExternalFacts",
            customNote = "keep-this-note"
        )
        val selectedCustom = ProfileOverlay(
            id = builtInId,
            name = "Selected Collision",
            description = "Same ID, distinct custom",
            customNote = "selected-custom"
        )
        val encoded = StudioStateCodec.encode(
            StudioStateSnapshot(
                baseProfile = PresetProfiles.DefaultBaseProfile,
                selectedCustomOverlayIndex = 1,
                customOverlays = listOf(firstCustom, selectedCustom),
                language = "pl"
            )
        )
        val decoded = requireNotNull(StudioStateCodec.decode(encoded))

        val restored = decoded.resolveStudioState()

        assertEquals(decoded.customOverlays, restored.customOverlays)
        assertSame(decoded.customOverlays[1], restored.selectedOverlay)
        assertEquals("selected-custom", restored.selectedOverlay?.customNote)
        assertEquals(firstCustom, restored.customOverlays[0])
        assertEquals("pl", restored.language)
    }

    @Test
    fun unsupportedRenderLanguageFallsBackToAuto() {
        val restored = StudioStateSnapshot(
            baseProfile = PresetProfiles.DefaultBaseProfile,
            language = "xx-invalid"
        ).resolveStudioState()

        assertEquals("auto", restored.language)
    }
}
