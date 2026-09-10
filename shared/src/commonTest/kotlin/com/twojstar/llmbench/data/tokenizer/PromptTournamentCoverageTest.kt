package com.twojstar.llmbench.data.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class PromptTournamentCoverageTest {
    @Test
    fun classifiesExactPlannedRunsWithoutPromotingNonLiveOrPartialObservations() {
        val first = variant(FIRST_ID)
        val second = variant(SECOND_ID)
        val alpha = PromptTournamentTarget(ALPHA_PROVIDER, ALPHA_MODEL)
        val beta = PromptTournamentTarget(BETA_PROVIDER, BETA_MODEL)
        val unplanned = PromptTournamentTarget(UNPLANNED_PROVIDER, UNPLANNED_MODEL)
        val experiment = TokenArenaExperiment.create(
            id = TOURNAMENT_ID,
            intentLabel = INTENT_LABEL,
            variants = listOf(first, second),
            responseObservations = listOf(
                observation(first, alpha, ArenaResponseProvenance.LIVE_PROVIDER),
                observation(first, alpha, ArenaResponseProvenance.CACHED_REPLAY),
                observation(first, beta, ArenaResponseProvenance.CACHED_REPLAY),
                observation(
                    first,
                    beta,
                    ArenaResponseProvenance.LIVE_PROVIDER,
                    isError = true
                ),
                observation(
                    second,
                    beta,
                    ArenaResponseProvenance.LIVE_PROVIDER,
                    isPartial = true
                ),
                observation(second, unplanned, ArenaResponseProvenance.LIVE_PROVIDER)
            )
        )
        val plan = PromptTournamentPlan.create(
            experiment = experiment,
            targets = listOf(alpha, beta),
            profiles = listOf(first.tournamentProfile(), second.tournamentProfile())
        )

        val coverage = plan.coverage()

        assertEquals(4, coverage.plannedRunCount)
        assertEquals(1, coverage.completeLiveRunCount)
        assertEquals(2, coverage.nonRankableObservedRunCount)
        assertEquals(1, coverage.missingRunCount)
        assertNotSame(coverage.runs, coverage.runs)
        assertEquals(
            listOf(
                PromptTournamentRunCoverageState.COMPLETE_LIVE,
                PromptTournamentRunCoverageState.NON_RANKABLE_OBSERVED,
                PromptTournamentRunCoverageState.MISSING,
                PromptTournamentRunCoverageState.NON_RANKABLE_OBSERVED
            ),
            coverage.runs.map(PromptTournamentRunCoverage::state)
        )
        assertEquals(
            listOf(2, 2, 0, 1),
            coverage.runs.map(PromptTournamentRunCoverage::observationCount)
        )
        assertEquals(
            listOf(run(second, alpha)),
            coverage.missingRuns
        )
    }

    private fun variant(id: String) = TokenArenaVariant(
        id = id,
        label = id,
        prompt = "prompt-$id"
    )

    private fun observation(
        variant: TokenArenaVariant,
        target: PromptTournamentTarget,
        provenance: ArenaResponseProvenance,
        isError: Boolean = false,
        isPartial: Boolean = false
    ) = TokenArenaResponseObservation(
        variantId = variant.id,
        promptFingerprint = variant.promptFingerprint,
        providerId = target.providerId,
        modelName = target.modelName,
        provenance = provenance,
        isError = isError,
        isPartial = isPartial
    )

    private fun run(
        variant: TokenArenaVariant,
        target: PromptTournamentTarget
    ) = PromptTournamentRun(
        variantId = variant.id,
        promptFingerprint = variant.promptFingerprint,
        providerId = target.providerId,
        modelName = target.modelName
    )

    private companion object {
        const val TOURNAMENT_ID = "coverage"
        const val INTENT_LABEL = "same intent"
        const val FIRST_ID = "first"
        const val SECOND_ID = "second"
        const val ALPHA_PROVIDER = "alpha"
        const val ALPHA_MODEL = "alpha-model"
        const val BETA_PROVIDER = "beta"
        const val BETA_MODEL = "beta-model"
        const val UNPLANNED_PROVIDER = "outside"
        const val UNPLANNED_MODEL = "outside-model"
    }
}
