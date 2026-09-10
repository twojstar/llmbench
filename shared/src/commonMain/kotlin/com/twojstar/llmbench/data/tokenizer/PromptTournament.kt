package com.twojstar.llmbench.data.tokenizer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Prompt representation metadata used to explain Tournament comparisons without rewriting prompts. */
@Serializable
enum class PromptTournamentFormat {
    PLAIN_TEXT,
    MARKDOWN,
    JSON,
    YAML,
    OTHER
}

@Serializable
enum class PromptTournamentVerbosity {
    CONCISE,
    STANDARD,
    VERBOSE
}

/** One provider/model destination planned for every prompt variant in the Tournament matrix. */
@Serializable
data class PromptTournamentTarget(
    val providerId: String,
    val modelName: String
) {
    init {
        require(providerId.isNotBlank()) { "Prompt Tournament provider id must not be blank" }
        require(modelName.isNotBlank()) { "Prompt Tournament model name must not be blank" }
    }
}

/**
 * Describes one existing Token Arena variant without becoming a second source of prompt text.
 * The prompt fingerprint prevents metadata from silently surviving a prompt edit under the same id.
 */
@Serializable
data class PromptTournamentVariantProfile(
    val variantId: String,
    val promptFingerprint: String,
    val format: PromptTournamentFormat = PromptTournamentFormat.OTHER,
    val verbosity: PromptTournamentVerbosity? = null,
    val languageTag: String? = null
) {
    init {
        require(variantId.isNotBlank()) { "Prompt Tournament variant id must not be blank" }
        require(promptFingerprint.isNotBlank()) { "Prompt Tournament prompt fingerprint must not be blank" }
        require(languageTag == null || languageTag.isNotBlank()) {
            "Prompt Tournament language tag must not be blank when present"
        }
    }
}

/** Exact planned provider/model run for one immutable prompt version. */
@Serializable
data class PromptTournamentRun(
    val variantId: String,
    val promptFingerprint: String,
    val providerId: String,
    val modelName: String
)

/**
 * Portable Prompt Tournament plan layered over one canonical Token Arena experiment.
 *
 * The experiment owns intent, exact prompt text and recorded evidence. This plan adds only descriptive
 * variant profiles and provider/model targets. Collection inputs are snapshotted and getters return
 * defensive copies so a validated matrix cannot be mutated through retained aliases or JVM casts.
 */
@Serializable
class PromptTournamentPlan private constructor(
    val experiment: TokenArenaExperiment,
    @SerialName("targets") private val targetSnapshot: List<PromptTournamentTarget>,
    @SerialName("profiles") private val profileSnapshot: List<PromptTournamentVariantProfile>
) {
    val targets: List<PromptTournamentTarget>
        get() = targetSnapshot.toList()

    val profiles: List<PromptTournamentVariantProfile>
        get() = profileSnapshot.toList()

    val plannedRunCount: Long
        get() = experiment.variants.size.toLong() * targetSnapshot.size.toLong()

    init {
        val variants = experiment.variants
        require(variants.size >= 2) { "Prompt Tournament needs at least two prompt variants" }
        require(targetSnapshot.isNotEmpty()) { "Prompt Tournament needs at least one provider/model target" }
        require(targetSnapshot.distinct().size == targetSnapshot.size) {
            "Prompt Tournament provider/model targets must be unique"
        }

        val currentFingerprints = variants.associate { variant ->
            variant.id to variant.promptFingerprint
        }
        require(profileSnapshot.size == variants.size) {
            "Prompt Tournament needs exactly one profile for every prompt variant"
        }
        require(profileSnapshot.map(PromptTournamentVariantProfile::variantId).toSet().size == profileSnapshot.size) {
            "Prompt Tournament variant profiles must reference unique variant ids"
        }
        require(profileSnapshot.all { profile ->
            currentFingerprints[profile.variantId] == profile.promptFingerprint
        }) {
            "Prompt Tournament profiles must reference the current prompt version of every variant"
        }
    }

    /** Lazily expand the deterministic variant x target matrix. */
    fun plannedRuns(): Sequence<PromptTournamentRun> =
        experiment.variants.asSequence().flatMap { variant ->
            targetSnapshot.asSequence().map { target ->
                PromptTournamentRun(
                    variantId = variant.id,
                    promptFingerprint = variant.promptFingerprint,
                    providerId = target.providerId,
                    modelName = target.modelName
                )
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PromptTournamentPlan) return false
        return experiment == other.experiment &&
            targetSnapshot == other.targetSnapshot &&
            profileSnapshot == other.profileSnapshot
    }

    override fun hashCode(): Int {
        var result = experiment.hashCode()
        result = 31 * result + targetSnapshot.hashCode()
        result = 31 * result + profileSnapshot.hashCode()
        return result
    }

    /** Keep nested prompt-bearing experiment state and target details out of incidental debug output. */
    override fun toString(): String =
        "PromptTournamentPlan(experimentId=${experiment.id}, targetCount=${targetSnapshot.size}, " +
            "profileCount=${profileSnapshot.size}, plannedRunCount=$plannedRunCount)"

    companion object {
        fun create(
            experiment: TokenArenaExperiment,
            targets: List<PromptTournamentTarget>,
            profiles: List<PromptTournamentVariantProfile>
        ): PromptTournamentPlan = PromptTournamentPlan(
            experiment = experiment,
            targetSnapshot = targets.toList(),
            profileSnapshot = profiles.toList()
        )
    }
}

/** Build descriptive Tournament metadata bound to the variant's current prompt version. */
fun TokenArenaVariant.tournamentProfile(
    format: PromptTournamentFormat = PromptTournamentFormat.OTHER,
    verbosity: PromptTournamentVerbosity? = null,
    languageTag: String? = null
): PromptTournamentVariantProfile = PromptTournamentVariantProfile(
    variantId = id,
    promptFingerprint = promptFingerprint,
    format = format,
    verbosity = verbosity,
    languageTag = languageTag
)
