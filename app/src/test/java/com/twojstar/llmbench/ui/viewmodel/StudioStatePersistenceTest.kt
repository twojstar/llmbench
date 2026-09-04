package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.ProfileOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun restoredNamesAvoidOccupiedIdsDeterministically() {
        val occupied = setOf("implementation-engineer", "implementation-engineer-(2)")

        assertEquals(
            "Implementation Engineer (3)",
            uniqueRestoredOverlayName("Implementation Engineer", occupied)
        )
    }
}
