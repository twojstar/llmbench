package com.twojstar.llmbench.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSkillPromptComposerTest {
    @Test
    fun noEnabledSkillsPreservesExistingSystemInstruction() {
        assertEquals(
            "Base profile",
            composeLocalSkillSystemInstruction("Base profile", emptyList())
        )
    }

    @Test
    fun enabledSkillsAppendOnlyMarkdownInstructionsInStableOrder() {
        val beta = manifest(
            name = "beta-skill",
            instructions = "Follow beta.",
            allowedTools = "shell",
            metadata = mapOf("secret-ish-label" to "not-runtime")
        )
        val alpha = manifest(
            name = "alpha-skill",
            instructions = "Follow alpha.",
            allowedTools = "network"
        )

        val result = composeLocalSkillSystemInstruction("Base profile", listOf(beta, alpha)).orEmpty()

        assertTrue(result.startsWith("Base profile\n\n## User-enabled local skills"))
        assertTrue(result.indexOf("### Skill: alpha-skill") < result.indexOf("### Skill: beta-skill"))
        assertTrue(result.contains("Follow alpha."))
        assertTrue(result.contains("Follow beta."))
        assertFalse(result.contains("shell"))
        assertFalse(result.contains("network"))
        assertFalse(result.contains("secret-ish-label"))
        assertFalse(result.contains("not-runtime"))
    }

    @Test
    fun enabledSkillsCreateSystemInstructionWithoutProfile() {
        val result = composeLocalSkillSystemInstruction(
            null,
            listOf(manifest("standalone-skill", "Standalone instructions."))
        ).orEmpty()

        assertTrue(result.startsWith("## User-enabled local skills"))
        assertTrue(result.contains("Standalone instructions."))
    }

    @Test
    fun exactlyPerSkillAndCombinedRuntimeBudgetsAreAccepted() {
        val first = manifest("alpha-skill", "a".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val second = manifest("beta-skill", "b".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))

        assertNull(localSkillRuntimeBudgetError(listOf(first)))
        assertNull(localSkillRuntimeBudgetError(listOf(first, second)))
    }

    @Test
    fun oneSkillOverRuntimeBudgetIsRejected() {
        val oversized = manifest(
            "oversized-skill",
            "x".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS + 1)
        )

        val error = localSkillRuntimeBudgetError(listOf(oversized)).orEmpty()

        assertTrue(error.contains("oversized-skill"))
        assertThrows(IllegalStateException::class.java) {
            composeLocalSkillSystemInstruction(null, listOf(oversized))
        }
    }

    @Test
    fun combinedRuntimeBudgetIsRejectedBeforePromptComposition() {
        val first = manifest("alpha-skill", "a".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val second = manifest("beta-skill", "b".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val extra = manifest("gamma-skill", "c")

        val error = localSkillRuntimeBudgetError(listOf(first, second, extra)).orEmpty()

        assertTrue(error.contains("combined instruction limit"))
        assertThrows(IllegalStateException::class.java) {
            composeLocalSkillSystemInstruction(null, listOf(first, second, extra))
        }
    }

    private fun manifest(
        name: String,
        instructions: String,
        allowedTools: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) = AgentSkillManifest(
        name = name,
        description = "$name description",
        instructions = instructions,
        metadata = metadata,
        allowedTools = allowedTools
    )
}
