package com.twojstar.llmbench.data.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSkillPromptComposerTest {
    @Test
    fun noEnabledSkillsPreservesExistingSystemInstruction() {
        assertEquals(BASE_PROFILE, composeLocalSkillSystemInstruction(BASE_PROFILE, emptyList()))
    }

    @Test
    fun enabledSkillsAppendOnlyMarkdownInstructionsInStableOrder() {
        val beta = manifest(
            name = BETA_SKILL,
            instructions = FOLLOW_BETA,
            allowedTools = SHELL_TOOL,
            metadata = mapOf(SECRET_LABEL to NOT_RUNTIME)
        )
        val alpha = manifest(
            name = ALPHA_SKILL,
            instructions = FOLLOW_ALPHA,
            allowedTools = NETWORK_TOOL
        )

        val result = composeLocalSkillSystemInstruction(BASE_PROFILE, listOf(beta, alpha)).orEmpty()

        assertTrue(result.startsWith("$BASE_PROFILE\n\n## User-enabled local skills"))
        assertTrue(result.indexOf("### Skill: $ALPHA_SKILL") < result.indexOf("### Skill: $BETA_SKILL"))
        assertTrue(result.contains(FOLLOW_ALPHA))
        assertTrue(result.contains(FOLLOW_BETA))
        assertFalse(result.contains(SHELL_TOOL))
        assertFalse(result.contains(NETWORK_TOOL))
        assertFalse(result.contains(SECRET_LABEL))
        assertFalse(result.contains(NOT_RUNTIME))
    }

    @Test
    fun enabledSkillsCreateSystemInstructionWithoutProfile() {
        val result = composeLocalSkillSystemInstruction(
            null,
            listOf(manifest(STANDALONE_SKILL, STANDALONE_INSTRUCTIONS))
        ).orEmpty()

        assertTrue(result.startsWith("## User-enabled local skills"))
        assertTrue(result.contains(STANDALONE_INSTRUCTIONS))
    }

    @Test
    fun exactlyPerSkillAndCombinedRuntimeBudgetsAreAccepted() {
        val first = manifest(ALPHA_SKILL, "a".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val second = manifest(BETA_SKILL, "b".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))

        assertNull(localSkillRuntimeBudgetError(listOf(first)))
        assertNull(localSkillRuntimeBudgetError(listOf(first, second)))
    }

    @Test
    fun oneSkillOverRuntimeBudgetIsRejected() {
        val oversized = manifest(
            OVERSIZED_SKILL,
            "x".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS + 1)
        )

        val error = localSkillRuntimeBudgetError(listOf(oversized)).orEmpty()

        assertTrue(error.contains(OVERSIZED_SKILL))
        assertFailsWith<IllegalStateException> {
            composeLocalSkillSystemInstruction(null, listOf(oversized))
        }
    }

    @Test
    fun combinedRuntimeBudgetIsRejectedBeforePromptComposition() {
        val first = manifest(ALPHA_SKILL, "a".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val second = manifest(BETA_SKILL, "b".repeat(MAX_RUNTIME_SKILL_INSTRUCTION_CHARS))
        val extra = manifest(GAMMA_SKILL, "c")
        val skills = listOf(first, second, extra)

        val error = localSkillRuntimeBudgetError(skills).orEmpty()

        assertTrue(error.contains("combined instruction limit"))
        assertFailsWith<IllegalStateException> {
            composeLocalSkillSystemInstruction(null, skills)
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

    private companion object {
        const val BASE_PROFILE = "Base profile"
        const val ALPHA_SKILL = "alpha-skill"
        const val BETA_SKILL = "beta-skill"
        const val GAMMA_SKILL = "gamma-skill"
        const val OVERSIZED_SKILL = "oversized-skill"
        const val STANDALONE_SKILL = "standalone-skill"
        const val FOLLOW_ALPHA = "Follow alpha."
        const val FOLLOW_BETA = "Follow beta."
        const val STANDALONE_INSTRUCTIONS = "Standalone instructions."
        const val SHELL_TOOL = "shell"
        const val NETWORK_TOOL = "network"
        const val SECRET_LABEL = "secret-ish-label"
        const val NOT_RUNTIME = "not-runtime"
    }
}
