package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay
import com.twojstar.llmbench.data.preferences.StudioStateSnapshot

private val SUPPORTED_RENDER_LANGUAGES = setOf("auto", "en", "pl")

internal fun StudioUiState.toStudioStateSnapshot(): StudioStateSnapshot {
    val builtInIds = PresetProfiles.BuiltInOverlays.mapTo(mutableSetOf()) { it.id }
    return StudioStateSnapshot(
        baseProfile = baseProfile,
        selectedOverlayId = selectedOverlay?.id,
        customOverlays = availableOverlays
            .filterNot { it.id in builtInIds }
            .distinctBy { it.id },
        language = language
    )
}

internal fun StudioViewModel.restoreStudioSnapshot(snapshot: StudioStateSnapshot) {
    val builtInIds = PresetProfiles.BuiltInOverlays.mapTo(mutableSetOf()) { it.id }

    snapshot.customOverlays
        .filterNot { it.id in builtInIds }
        .distinctBy { it.id }
        .forEach { overlay -> restoreCustomOverlay(overlay) }

    restoreEditableBaseProfile(snapshot.baseProfile)

    val selectedOverlay = snapshot.selectedOverlayId?.let { selectedId ->
        uiState.value.availableOverlays.firstOrNull { it.id == selectedId }
    }
    applyOverlay(selectedOverlay)
    setLanguage(snapshot.language.takeIf { it in SUPPORTED_RENDER_LANGUAGES } ?: "auto")

    // Custom-overlay restoration reuses the normal save action, which emits a snackbar.
    // Startup restoration itself should stay silent.
    dismissSnackbar()
}

private fun StudioViewModel.restoreCustomOverlay(overlay: ProfileOverlay) {
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
    saveCustomOverlay(overlay.name, overlay.description)
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
