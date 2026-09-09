package com.twojstar.llmbench.data.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptTournamentDesktopTest {
    @Test
    fun mutatingJvmGetterCopiesDoesNotChangeTournamentState() {
        val first = TokenArenaVariant("first", "First", "one")
        val second = TokenArenaVariant("second", "Second", "two")
        val plan = PromptTournamentPlan.create(
            experiment = TokenArenaExperiment.create(
                id = "jvm-tournament-copy",
                intentLabel = "same intent",
                variants = listOf(first, second)
            ),
            targets = listOf(
                PromptTournamentTarget("provider-a", "model-a"),
                PromptTournamentTarget("provider-b", "model-b")
            ),
            profiles = listOf(first.tournamentProfile(), second.tournamentProfile())
        )

        val exposedTargets = plan.targets
        exposedTargets.javaClass.getMethod("clear").invoke(exposedTargets)
        val exposedProfiles = plan.profiles
        exposedProfiles.javaClass.getMethod("clear").invoke(exposedProfiles)

        assertTrue(exposedTargets.isEmpty())
        assertTrue(exposedProfiles.isEmpty())
        assertEquals(2, plan.targets.size)
        assertEquals(2, plan.profiles.size)
        assertEquals(4L, plan.plannedRunCount)
    }
}
