package com.twojstar.llmbench.data.tokenizer

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class PromptTournamentTest {
    @Test
    fun plannedRunsExpandVariantsAcrossTargetsWithoutDuplicatingPromptText() {
        val concise = variant("concise", "Answer in one sentence.")
        val markdown = variant("markdown", "## Task\nAnswer in one sentence.")
        val plan = PromptTournamentPlan.create(
            experiment = experiment(concise, markdown),
            targets = listOf(
                PromptTournamentTarget("openai", "gpt-test"),
                PromptTournamentTarget("anthropic", "claude-test")
            ),
            profiles = listOf(
                concise.tournamentProfile(
                    format = PromptTournamentFormat.PLAIN_TEXT,
                    verbosity = PromptTournamentVerbosity.CONCISE,
                    languageTag = "en"
                ),
                markdown.tournamentProfile(
                    format = PromptTournamentFormat.MARKDOWN,
                    verbosity = PromptTournamentVerbosity.CONCISE,
                    languageTag = "en"
                )
            )
        )

        assertEquals(4L, plan.plannedRunCount)
        assertEquals(
            listOf(
                run(concise, "openai", "gpt-test"),
                run(concise, "anthropic", "claude-test"),
                run(markdown, "openai", "gpt-test"),
                run(markdown, "anthropic", "claude-test")
            ),
            plan.plannedRuns().toList()
        )
    }

    @Test
    fun tournamentRequiresAtLeastTwoVariantsAndOneTarget() {
        val only = variant("only", "same intent")
        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = experiment(only),
                targets = listOf(PromptTournamentTarget("provider", "model")),
                profiles = listOf(only.tournamentProfile())
            )
        }

        val second = variant("second", "same intent, second form")
        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = experiment(only, second),
                targets = emptyList(),
                profiles = profiles(only, second)
            )
        }
    }

    @Test
    fun tournamentRejectsDuplicateTargetsAndIncompleteProfiles() {
        val first = variant("first", "first")
        val second = variant("second", "second")
        val target = PromptTournamentTarget("provider", "model")
        val arena = experiment(first, second)

        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = arena,
                targets = listOf(target, target),
                profiles = profiles(first, second)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = arena,
                targets = listOf(target),
                profiles = listOf(first.tournamentProfile())
            )
        }
    }

    @Test
    fun tournamentRejectsStalePromptProfiles() {
        val first = variant("first", "first")
        val second = variant("second", "second")
        val stale = first.copy(prompt = "edited")

        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = experiment(stale, second),
                targets = listOf(PromptTournamentTarget("provider", "model")),
                profiles = profiles(first, second)
            )
        }
    }

    @Test
    fun collectionInputsAreSnapshottedAndGettersReturnFreshCopies() {
        val first = variant("first", "first")
        val second = variant("second", "second")
        val targets = mutableListOf(PromptTournamentTarget("provider", "model"))
        val metadata = profiles(first, second).toMutableList()
        val plan = PromptTournamentPlan.create(
            experiment = experiment(first, second),
            targets = targets,
            profiles = metadata
        )

        targets.clear()
        metadata.clear()

        assertEquals(1, plan.targets.size)
        assertEquals(2, plan.profiles.size)
        assertNotSame(plan.targets, plan.targets)
        assertNotSame(plan.profiles, plan.profiles)
    }

    @Test
    fun serializationRoundTripPreservesValueSemanticsAndMatrix() {
        val first = variant("plain", "Explain the result.")
        val second = variant("json", "{\"task\":\"Explain the result.\"}")
        val plan = PromptTournamentPlan.create(
            experiment = experiment(first, second),
            targets = listOf(PromptTournamentTarget("provider", "model")),
            profiles = listOf(
                first.tournamentProfile(format = PromptTournamentFormat.PLAIN_TEXT),
                second.tournamentProfile(format = PromptTournamentFormat.JSON)
            )
        )

        val encoded = Json.encodeToString(PromptTournamentPlan.serializer(), plan)
        val decoded = Json.decodeFromString(PromptTournamentPlan.serializer(), encoded)

        assertEquals(plan, decoded)
        assertEquals(plan.hashCode(), decoded.hashCode())
        assertEquals(plan.plannedRuns().toList(), decoded.plannedRuns().toList())
    }

    private fun variant(id: String, prompt: String) = TokenArenaVariant(id, id, prompt)

    private fun experiment(vararg variants: TokenArenaVariant) = TokenArenaExperiment.create(
        id = "tournament",
        intentLabel = "same intent",
        variants = variants.toList()
    )

    private fun profiles(vararg variants: TokenArenaVariant) =
        variants.map(TokenArenaVariant::tournamentProfile)

    private fun run(
        variant: TokenArenaVariant,
        providerId: String,
        modelName: String
    ) = PromptTournamentRun(
        variantId = variant.id,
        promptFingerprint = variant.promptFingerprint,
        providerId = providerId,
        modelName = modelName
    )
}
