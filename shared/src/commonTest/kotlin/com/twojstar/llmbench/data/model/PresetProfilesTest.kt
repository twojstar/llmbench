package com.twojstar.llmbench.data.model

import com.twojstar.llmbench.data.engine.ProfileMerger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetProfilesTest {
    @Test
    fun builtInOverlayIdsAreUnique() {
        val ids = PresetProfiles.BuiltInOverlays.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun taskFocusedPresetsAreBuiltIn() {
        val ids = PresetProfiles.BuiltInOverlays.map { it.id }.toSet()

        assertTrue("implementation-engineer" in ids)
        assertTrue("research-analyst" in ids)
        assertTrue("translator-editor" in ids)
    }

    @Test
    fun implementationEngineerProducesRunnableProactiveProfile() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.ImplementationEngineerOverlay
        )

        assertEquals(3, merged.personality.modifiers["technical"])
        assertEquals("proactive", merged.collaboration.initiative)
        assertEquals("runnable", merged.output.codeExamples)
    }

    @Test
    fun researchAnalystRequiresTraceableExternalClaims() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.ResearchAnalystOverlay
        )

        assertTrue(merged.knowledge.requireTraceableClaims)
        assertTrue(merged.knowledge.surfaceSourceConflicts)
        assertEquals("strict", merged.collaboration.verification)
        assertEquals("requiredForExternalFacts", merged.output.citations)
    }

    @Test
    fun translatorEditorKeepsArtifactStyleWithoutMirroringInputLanguage() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.TranslatorEditorOverlay
        )

        assertTrue(merged.personality.adaptation.preserveRequestedArtifactStyle)
        assertFalse(merged.personality.adaptation.mirrorLanguage)
        assertEquals("off", merged.collaboration.preamble)
        assertEquals("conservative", merged.collaboration.initiative)
    }
}
