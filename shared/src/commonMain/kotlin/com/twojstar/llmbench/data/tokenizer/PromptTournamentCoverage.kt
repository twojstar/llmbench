package com.twojstar.llmbench.data.tokenizer

/** Coverage state for one exact planned Tournament run. */
enum class PromptTournamentRunCoverageState {
    MISSING,
    NON_RANKABLE_OBSERVED,
    COMPLETE_LIVE
}

/** Read-only coverage for one planned variant x provider/model run. */
data class PromptTournamentRunCoverage(
    val run: PromptTournamentRun,
    val state: PromptTournamentRunCoverageState,
    val observationCount: Int
)

/**
 * Derived coverage for a Prompt Tournament plan.
 *
 * Matching uses the complete immutable run identity, including the prompt fingerprint. A cached,
 * simulated, failed or partial observation proves that a run was observed, but it does not count as
 * a complete live result. Unplanned provider/model observations never fill a planned matrix cell.
 */
data class PromptTournamentCoverage(
    val runs: List<PromptTournamentRunCoverage>
) {
    val plannedRunCount: Int
        get() = runs.size

    val missingRunCount: Int
        get() = runs.count { it.state == PromptTournamentRunCoverageState.MISSING }

    val nonRankableObservedRunCount: Int
        get() = runs.count { it.state == PromptTournamentRunCoverageState.NON_RANKABLE_OBSERVED }

    val completeLiveRunCount: Int
        get() = runs.count { it.state == PromptTournamentRunCoverageState.COMPLETE_LIVE }

    val missingRuns: List<PromptTournamentRun>
        get() = runs
            .asSequence()
            .filter { coverage -> coverage.state == PromptTournamentRunCoverageState.MISSING }
            .map(PromptTournamentRunCoverage::run)
            .toList()
}

fun PromptTournamentPlan.coverage(): PromptTournamentCoverage {
    val observationsByRun = experiment.responseObservations.groupBy { observation ->
        TournamentRunKey(
            variantId = observation.variantId,
            promptFingerprint = observation.promptFingerprint,
            providerId = observation.providerId,
            modelName = observation.modelName
        )
    }

    val coverage = plannedRuns().map { run ->
        val observations = observationsByRun[run.key()].orEmpty()
        val state = when {
            observations.any(TokenArenaResponseObservation::canEnterLiveEfficiencyRanking) ->
                PromptTournamentRunCoverageState.COMPLETE_LIVE
            observations.isNotEmpty() -> PromptTournamentRunCoverageState.NON_RANKABLE_OBSERVED
            else -> PromptTournamentRunCoverageState.MISSING
        }
        PromptTournamentRunCoverage(
            run = run,
            state = state,
            observationCount = observations.size
        )
    }.toList()

    return PromptTournamentCoverage(coverage)
}

private data class TournamentRunKey(
    val variantId: String,
    val promptFingerprint: String,
    val providerId: String,
    val modelName: String
)

private fun PromptTournamentRun.key(): TournamentRunKey = TournamentRunKey(
    variantId = variantId,
    promptFingerprint = promptFingerprint,
    providerId = providerId,
    modelName = modelName
)
