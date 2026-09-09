package com.twojstar.llmbench.data.tokenizer

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class PromptTournamentTest {
    @Test
    fun plannedRunsExpandVariantsAcrossTargetsWithoutDuplicatingPromptText() {
        val concise = variant(CONCISE_ID, ANSWER_SENTENCE)
        val markdown = variant(MARKDOWN_ID, "## Task\n$ANSWER_SENTENCE")
        val plan = PromptTournamentPlan.create(
            experiment = experiment(concise, markdown),
            targets = listOf(
                PromptTournamentTarget(OPENAI_PROVIDER, GPT_MODEL),
                PromptTournamentTarget(ANTHROPIC_PROVIDER, CLAUDE_MODEL)
            ),
            profiles = listOf(
                concise.tournamentProfile(
                    format = PromptTournamentFormat.PLAIN_TEXT,
                    verbosity = PromptTournamentVerbosity.CONCISE,
                    languageTag = EN_LANGUAGE
                ),
                markdown.tournamentProfile(
                    format = PromptTournamentFormat.MARKDOWN,
                    verbosity = PromptTournamentVerbosity.CONCISE,
                    languageTag = EN_LANGUAGE
                )
            )
        )

        assertEquals(4L, plan.plannedRunCount)
        assertEquals(
            listOf(
                run(concise, OPENAI_PROVIDER, GPT_MODEL),
                run(concise, ANTHROPIC_PROVIDER, CLAUDE_MODEL),
                run(markdown, OPENAI_PROVIDER, GPT_MODEL),
                run(markdown, ANTHROPIC_PROVIDER, CLAUDE_MODEL)
            ),
            plan.plannedRuns().toList()
        )
    }

    @Test
    fun tournamentRequiresAtLeastTwoVariantsAndOneTarget() {
        val only = variant(ONLY_ID, SAME_INTENT)
        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = experiment(only),
                targets = listOf(defaultTarget()),
                profiles = listOf(only.tournamentProfile())
            )
        }

        val second = variant(SECOND_ID, "$SAME_INTENT, second form")
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
        val first = variant(FIRST_ID, FIRST_ID)
        val second = variant(SECOND_ID, SECOND_ID)
        val target = defaultTarget()
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
        val first = variant(FIRST_ID, FIRST_ID)
        val second = variant(SECOND_ID, SECOND_ID)
        val stale = first.copy(prompt = EDITED_PROMPT)

        assertFailsWith<IllegalArgumentException> {
            PromptTournamentPlan.create(
                experiment = experiment(stale, second),
                targets = listOf(defaultTarget()),
                profiles = profiles(first, second)
            )
        }
    }

    @Test
    fun collectionInputsAreSnapshottedAndGettersReturnFreshCopies() {
        val first = variant(FIRST_ID, FIRST_ID)
        val second = variant(SECOND_ID, SECOND_ID)
        val targets = mutableListOf(defaultTarget())
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
        val first = variant("plain", EXPLAIN_RESULT)
        val second = variant("json", "{\"task\":\"$EXPLAIN_RESULT\"}")
        val plan = PromptTournamentPlan.create(
            experiment = experiment(first, second),
            targets = listOf(defaultTarget()),
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
        id = TOURNAMENT_ID,
        intentLabel = SAME_INTENT,
        variants = variants.toList()
    )

    private fun profiles(vararg variants: TokenArenaVariant) =
        variants.map { variant -> variant.tournamentProfile() }

    private fun defaultTarget() = PromptTournamentTarget(DEFAULT_PROVIDER, DEFAULT_MODEL)

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

    companion object {
        private const val TOURNAMENT_ID = "tournament"
        private const val SAME_INTENT = "same intent"
        private const val FIRST_ID = "first"
        private const val SECOND_ID = "second"
        private const val ONLY_ID = "only"
        private const val CONCISE_ID = "concise"
        private const val MARKDOWN_ID = "markdown"
        private const val DEFAULT_PROVIDER = "provider"
        private const val DEFAULT_MODEL = "model"
        private const val OPENAI_PROVIDER = "openai"
        private const val GPT_MODEL = "gpt-test"
        private const val ANTHROPIC_PROVIDER = "anthropic"
        private const val CLAUDE_MODEL = "claude-test"
        private const val EN_LANGUAGE = "en"
        private const val ANSWER_SENTENCE = "Answer in one sentence."
        private const val EDITED_PROMPT = "edited"
        private const val EXPLAIN_RESULT = "Explain the result."
    }
}
