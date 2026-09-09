package com.twojstar.llmbench.data.tokenizer

import com.twojstar.llmbench.data.model.ProviderUsage
import kotlinx.serialization.Serializable

/** How a token count was obtained. Never collapse these modes into one unlabeled number. */
@Serializable
enum class TokenMeasurementMode {
    PROVIDER_EXACT,
    LOCAL_EXACT_ENCODING,
    REFERENCE_FALLBACK
}

/** Provenance of a response used by Token Arena efficiency views. */
@Serializable
enum class ArenaResponseProvenance {
    LIVE_PROVIDER,
    CACHED_REPLAY,
    SIMULATED_FALLBACK
}

/** One exact prompt representation participating in an Arena experiment. */
@Serializable
data class TokenArenaVariant(
    val id: String,
    val label: String,
    val prompt: String
) {
    init {
        require(id.isNotBlank()) { "Token Arena variant id must not be blank" }
        require(label.isNotBlank()) { "Token Arena variant label must not be blank" }
    }

    val promptFingerprint: String
        get() = stablePromptFingerprint(prompt)
}

/**
 * Token count for one prompt variant, with enough provenance to interpret the number honestly.
 *
 * `LOCAL_EXACT_ENCODING` is exact only for the named encoding, not universally for other models.
 * `PROVIDER_EXACT` must name the provider and model that supplied the count.
 */
@Serializable
data class TokenArenaTokenMeasurement(
    val variantId: String,
    val promptFingerprint: String,
    val tokens: Long,
    val mode: TokenMeasurementMode,
    val backendLabel: String,
    val encodingLabel: String? = null,
    val providerId: String? = null,
    val modelName: String? = null
) {
    init {
        require(variantId.isNotBlank()) { "Token measurement variant id must not be blank" }
        require(promptFingerprint.isNotBlank()) { "Token measurement prompt fingerprint must not be blank" }
        require(tokens >= 0) { "Token count must not be negative" }
        require(backendLabel.isNotBlank()) { "Token measurement backend label must not be blank" }
        if (mode == TokenMeasurementMode.LOCAL_EXACT_ENCODING) {
            require(!encodingLabel.isNullOrBlank()) {
                "Local exact token measurements must name their encoding"
            }
        }
        if (mode == TokenMeasurementMode.PROVIDER_EXACT) {
            require(!providerId.isNullOrBlank() && !modelName.isNullOrBlank()) {
                "Provider-exact token measurements must name provider and model"
            }
        }
    }
}

/** Response-side metrics for one prompt variant. Provider usage remains the canonical usage model. */
@Serializable
data class TokenArenaResponseObservation(
    val variantId: String,
    val promptFingerprint: String,
    val providerId: String,
    val modelName: String,
    val provenance: ArenaResponseProvenance,
    val usage: ProviderUsage? = null,
    val latencyMs: Long? = null,
    val responseLengthChars: Int? = null,
    val qualityScore: Double? = null,
    val pricingSnapshotLabel: String? = null,
    val isError: Boolean = false,
    val isPartial: Boolean = false
) {
    init {
        require(variantId.isNotBlank()) { "Arena response variant id must not be blank" }
        require(promptFingerprint.isNotBlank()) { "Arena response prompt fingerprint must not be blank" }
        require(providerId.isNotBlank()) { "Arena response provider id must not be blank" }
        require(modelName.isNotBlank()) { "Arena response model name must not be blank" }
        require(latencyMs == null || latencyMs >= 0) { "Arena response latency must not be negative" }
        require(responseLengthChars == null || responseLengthChars >= 0) {
            "Arena response length must not be negative"
        }
        require(qualityScore == null || qualityScore.isFinite()) {
            "Arena quality score must be finite"
        }
    }

    val canEnterLiveEfficiencyRanking: Boolean
        get() = provenance == ArenaResponseProvenance.LIVE_PROVIDER && !isError && !isPartial
}

/**
 * Portable, serializable record for one reproducible Token Arena experiment.
 *
 * Public construction snapshots every collection so callers cannot mutate an experiment through a
 * retained MutableList alias. Prompt fingerprints bind recorded results to the exact variant text.
 * This model does not imply persistence; durable storage/export must remain explicit.
 */
@Serializable
class TokenArenaExperiment private constructor(
    val id: String,
    val intentLabel: String,
    val variants: List<TokenArenaVariant>,
    val tokenMeasurements: List<TokenArenaTokenMeasurement>,
    val responseObservations: List<TokenArenaResponseObservation>
) {
    init {
        require(id.isNotBlank()) { "Token Arena experiment id must not be blank" }
        require(intentLabel.isNotBlank()) { "Token Arena intent label must not be blank" }
        require(variants.isNotEmpty()) { "Token Arena experiment needs at least one variant" }

        val variantIds = variants.map(TokenArenaVariant::id)
        require(variantIds.toSet().size == variantIds.size) {
            "Token Arena variant ids must be unique"
        }
        val promptFingerprints = variants.associate { variant ->
            variant.id to variant.promptFingerprint
        }
        require(tokenMeasurements.all { measurement ->
            promptFingerprints[measurement.variantId] == measurement.promptFingerprint
        }) {
            "Every token measurement must reference the current prompt version of a known variant"
        }
        require(responseObservations.all { observation ->
            promptFingerprints[observation.variantId] == observation.promptFingerprint
        }) {
            "Every response observation must reference the current prompt version of a known variant"
        }
    }

    companion object {
        fun create(
            id: String,
            intentLabel: String,
            variants: List<TokenArenaVariant>,
            tokenMeasurements: List<TokenArenaTokenMeasurement> = emptyList(),
            responseObservations: List<TokenArenaResponseObservation> = emptyList()
        ): TokenArenaExperiment = TokenArenaExperiment(
            id = id,
            intentLabel = intentLabel,
            variants = variants.toList(),
            tokenMeasurements = tokenMeasurements.toList(),
            responseObservations = responseObservations.toList()
        )
    }
}

/**
 * Convert an exact local [TokenCounter] into an explicitly encoding-scoped Arena measurement.
 * The recorded variant and counted prompt are one object so stale text cannot be attributed to it.
 */
fun TokenCounter.measureForArena(
    variant: TokenArenaVariant,
    backendLabel: String
): TokenArenaTokenMeasurement = TokenArenaTokenMeasurement(
    variantId = variant.id,
    promptFingerprint = variant.promptFingerprint,
    tokens = count(variant.prompt).toLong(),
    mode = TokenMeasurementMode.LOCAL_EXACT_ENCODING,
    backendLabel = backendLabel,
    encodingLabel = encodingLabel
)

/** Stable non-security fingerprint used only to detect accidental stale prompt/result associations. */
internal fun stablePromptFingerprint(prompt: String): String {
    val bytes = prompt.encodeToByteArray()
    var hash = -3750763034362895579L // FNV-1a 64-bit offset basis in signed Long form.
    bytes.forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 1099511628211L
    }
    return "${bytes.size}:${hash.toULong().toString(16).padStart(16, '0')}"
}
