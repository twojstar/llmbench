package com.twojstar.llmbench.data.model

import com.twojstar.llmbench.data.engine.InstructionRenderer
import com.twojstar.llmbench.data.engine.ProfileMerger
import kotlin.test.Test
import kotlin.test.assertContains
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
    fun implementationEngineerRendersTechnicalProactiveBehavior() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.ImplementationEngineerOverlay
        )
        val rendered = InstructionRenderer.render(merged, "en")

        assertEquals(3, merged.personality.modifiers["technical"])
        assertEquals("proactive", merged.collaboration.initiative)
        assertEquals("runnable", merged.output.codeExamples)
        assertContains(
            rendered,
            "Favor precise technical terminology, constraints, edge cases, and implementation details whenever they materially improve the answer."
        )
        assertContains(
            rendered,
            "Actively surface related problems and useful improvements while respecting scope."
        )
        assertContains(rendered, "Prefer runnable code examples.")
    }

    @Test
    fun researchAnalystRendersTraceableEvidenceRules() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.ResearchAnalystOverlay
        )
        val rendered = InstructionRenderer.render(merged, "en")

        assertTrue(merged.knowledge.requireTraceableClaims)
        assertTrue(merged.knowledge.surfaceSourceConflicts)
        assertEquals("strict", merged.collaboration.verification)
        assertEquals("requiredForExternalFacts", merged.output.citations)
        assertContains(rendered, "Require strong evidence and complete validation before making a firm conclusion.")
        assertContains(rendered, "Keep externally verifiable claims traceable to supporting evidence.")
        assertContains(rendered, "Support external factual claims with citations.")
    }

    @Test
    fun translatorEditorRendersStylePreservationWithoutLanguageMirroring() {
        val merged = ProfileMerger.merge(
            PresetProfiles.DefaultBaseProfile,
            PresetProfiles.TranslatorEditorOverlay
        )
        val rendered = InstructionRenderer.render(merged, "en")

        assertTrue(merged.personality.adaptation.preserveRequestedArtifactStyle)
        assertFalse(merged.personality.adaptation.mirrorLanguage)
        assertEquals(2, merged.personality.intensity)
        assertEquals("off", merged.collaboration.preamble)
        assertEquals("conservative", merged.collaboration.initiative)
        assertContains(
            rendered,
            "Make the selected base voice clearly visible while keeping it subordinate to content and context."
        )
        assertContains(rendered, "The requested artifact style outranks conversational personality.")
        assertFalse(rendered.contains("Reply in the user's language unless asked otherwise."))
    }
}
