package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay
import com.twojstar.llmbench.data.model.StudioStateSnapshot

private val SUPPORTED_RENDER_LANGUAGES = setOf("auto", "en", "pl")

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

internal fun StudioViewModel.restoreStudioSnapshot(snapshot: StudioStateSnapshot) {
    val builtIns = PresetProfiles.BuiltInOverlays
    val existingCustoms = uiState.value.availableOverlays
        .filterNot { candidate -> builtIns.any { builtIn -> builtIn === candidate } }
        .toMutableList()
    val occupiedIds = uiState.value.availableOverlays.mapTo(mutableSetOf()) { it.id }

    val restoredCustoms = snapshot.customOverlays.map { persisted ->
        val existingIndex = existingCustoms.indexOfFirst { existing ->
            existing.matchesPersistedCustom(persisted)
        }
        if (existingIndex >= 0) {
            existingCustoms.removeAt(existingIndex)
        } else {
            restoreCustomOverlay(persisted, occupiedIds)
        }
    }

    restoreEditableBaseProfile(snapshot.baseProfile)

    val selectedBuiltInOverlayId = snapshot.selectedBuiltInOverlayId
    val selectedCustomOverlayIndex = snapshot.selectedCustomOverlayIndex
    val selectedOverlay = when {
        selectedBuiltInOverlayId != null -> builtIns.firstOrNull {
            it.id == selectedBuiltInOverlayId
        }
        selectedCustomOverlayIndex != null -> restoredCustoms.getOrNull(selectedCustomOverlayIndex)
        else -> null
    }
    applyOverlay(selectedOverlay)
    setLanguage(snapshot.language.takeIf { it in SUPPORTED_RENDER_LANGUAGES } ?: "auto")

    // Custom-overlay restoration reuses the normal save action, which emits a snackbar.
    // Startup restoration itself should stay silent.
    dismissSnackbar()
}

private fun ProfileOverlay.matchesPersistedCustom(persisted: ProfileOverlay): Boolean {
    val nameMatches = name == persisted.name || name.startsWith("${persisted.name} (")
    return nameMatches &&
        description == persisted.description &&
        personalityBase == persisted.personalityBase &&
        personalityIntensity == persisted.personalityIntensity &&
        modifierOverrides == persisted.modifierOverrides &&
        verification == persisted.verification &&
        initiative == persisted.initiative
}

internal fun uniqueRestoredOverlayName(preferredName: String, occupiedIds: Set<String>): String {
    val baseName = preferredName.ifBlank { "Custom Overlay" }
    var candidate = baseName
    var suffix = 2
    while (overlayIdForName(candidate) in occupiedIds) {
        candidate = "$baseName ($suffix)"
        suffix += 1
    }
    return candidate
}

private fun overlayIdForName(name: String): String = name.lowercase().replace(" ", "-")

private fun StudioViewModel.restoreCustomOverlay(
    overlay: ProfileOverlay,
    occupiedIds: MutableSet<String>
): ProfileOverlay {
    val defaultBase = PresetProfiles.DefaultBaseProfile
    val modifierNames = uiState.value.baseProfile.personality.modifiers.keys + overlay.modifierOverrides.keys

    modifierNames.forEach { setModifier(it, null) }
    setBasePersonality(overlay.personalityBase ?: defaultBase.personality.base)
    setPersonalityIntensity(overlay.personalityIntensity)
    overlay.modifierOverrides.forEach { (name, value) -> setModifier(name, value) }
    setCollaborationEnum(
        "verification",
        overlay.verification ?: defaultBase.collaboration.verification
    )
    setCollaborationEnum(
        "initiative",
        overlay.initiative ?: defaultBase.collaboration.initiative
    )

    val restoredName = uniqueRestoredOverlayName(overlay.name, occupiedIds)
    saveCustomOverlay(restoredName, overlay.description)
    return requireNotNull(uiState.value.selectedOverlay).also { restored ->
        occupiedIds += restored.id
    }
}

private fun StudioViewModel.restoreEditableBaseProfile(profile: Profile) {
    setBasePersonality(profile.personality.base)
    setPersonalityIntensity(profile.personality.intensity)

    val modifierNames = uiState.value.baseProfile.personality.modifiers.keys + profile.personality.modifiers.keys
    modifierNames.forEach { name -> setModifier(name, profile.personality.modifiers[name]) }

    val adaptation = profile.personality.adaptation
    setAdaptation("followUserRegister", adaptation.followUserRegister)
    setAdaptation("preserveRequestedArtifactStyle", adaptation.preserveRequestedArtifactStyle)
    setAdaptation("reduceHumorInSeriousContexts", adaptation.reduceHumorInSeriousContexts)
    setAdaptation("mirrorLanguage", adaptation.mirrorLanguage)
    setAdaptation("allowCasualProfanity", adaptation.allowCasualProfanity)

    val collaboration = profile.collaboration
    setCollaborationEnum("preamble", collaboration.preamble)
    setCollaborationEnum("initiative", collaboration.initiative)
    setCollaborationEnum("verification", collaboration.verification)
    setCollaborationEnum("questionPolicy", collaboration.questionPolicy)
    setCollaborationEnum("assumptionPolicy", collaboration.assumptionPolicy)
    setCollaborationBool("answerFirst", collaboration.answerFirst)
    setCollaborationBool("plainChatIsDefault", collaboration.plainChatIsDefault)
    setCollaborationBool("respectExplicitTurnInstructions", collaboration.respectExplicitTurnInstructions)
    setCollaborationBool("avoidRoutinePraise", collaboration.avoidRoutinePraise)
    setCollaborationBool("avoidRoutineFollowUpOffer", collaboration.avoidRoutineFollowUpOffer)
    setCollaborationBool("announceOnlyMaterialActions", collaboration.announceOnlyMaterialActions)
    setCollaborationBool("reportPartialFailures", collaboration.reportPartialFailures)
    setCollaborationBool("preferResultOverProcess", collaboration.preferResultOverProcess)

    val output = profile.output
    setOutputSetting(
        defaultFormat = output.defaultFormat,
        maxHeadingDepth = output.maxHeadingDepth,
        preferShortParagraphs = output.preferShortParagraphs,
        tables = output.tables,
        codeExamples = output.codeExamples,
        citations = output.citations
    )
}
