package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.ProfileOverlay
import org.junit.Assert.assertEquals
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

        assertEquals(customOverlay.id, snapshot.selectedOverlayId)
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

        assertEquals(builtIn.id, snapshot.selectedOverlayId)
        assertTrue(snapshot.customOverlays.isEmpty())
    }
}
