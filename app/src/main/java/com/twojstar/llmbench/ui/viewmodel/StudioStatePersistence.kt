package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay
import com.twojstar.llmbench.data.model.StudioStateSnapshot

private val SUPPORTED_RENDER_LANGUAGES = setOf("auto", "en", "pl")

internal data class RestoredStudioState(
    val baseProfile: Profile,
    val customOverlays: List<ProfileOverlay>,
    val selectedOverlay: ProfileOverlay?,
    val language: String
)

internal fun StudioUiState.toStudioStateSnapshot(): StudioStateSnapshot {
    val builtIns = PresetProfiles.BuiltInOverlays
    val customOverlays = availableOverlays.filterNot { candidate ->
        builtIns.any { builtIn -> builtIn === candidate }
    }
    val selectedBuiltIn = selectedOverlay?.let { selected ->
        builtIns.firstOrNull { builtIn -> builtIn === selected }
    }
    val selectedCustomIndex = selectedOverlay?.let { selected ->
        customOverlays.indexOfFirst { custom -> custom === selected }.takeIf { it >= 0 }
    }

    return StudioStateSnapshot(
        baseProfile = baseProfile,
        selectedBuiltInOverlayId = selectedBuiltIn?.id,
        selectedCustomOverlayIndex = selectedCustomIndex,
        customOverlays = customOverlays,
        language = language
    )
}

internal fun StudioStateSnapshot.resolveStudioState(): RestoredStudioState {
    val builtInOverlayId = selectedBuiltInOverlayId
    val customOverlayIndex = selectedCustomOverlayIndex
    val selectedOverlay = when {
        builtInOverlayId != null -> PresetProfiles.BuiltInOverlays.firstOrNull {
            it.id == builtInOverlayId
        }
        customOverlayIndex != null -> customOverlays.getOrNull(customOverlayIndex)
        else -> null
    }

    return RestoredStudioState(
        baseProfile = baseProfile,
        customOverlays = customOverlays,
        selectedOverlay = selectedOverlay,
        language = language.takeIf { it in SUPPORTED_RENDER_LANGUAGES } ?: "auto"
    )
}

internal fun StudioViewModel.restoreStudioSnapshot(snapshot: StudioStateSnapshot) {
    val restored = snapshot.resolveStudioState()
    replacePersistedStudioState(
        baseProfile = restored.baseProfile,
        customOverlays = restored.customOverlays,
        selectedOverlay = restored.selectedOverlay,
        language = restored.language
    )
}
