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
    val tokens: Long,
    val mode: TokenMeasurementMode,
    val backendLabel: String,
    val encodingLabel: String? = null,
    val providerId: String? = null,
    val modelName: String? = null
) {
    init {
        require(variantId.isNotBlank()) { "Token measurement variant id must not be blank" }
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
    val providerId: String,
    val modelName: String,
    val provenance: ArenaResponseProvenance,
    val usage: ProviderUsage? = null,
    val latencyMs: Long? = null,
    val responseLengthChars: Int? = null,
    val qualityScore: Double? = null,
    val pricingSnapshotLabel: String? = null,
    val isError: Boolean = false
) {
    init {
        require(variantId.isNotBlank()) { "Arena response variant id must not be blank" }
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
        get() = provenance == ArenaResponseProvenance.LIVE_PROVIDER && !isError
}

/**
 * Portable, serializable record for one reproducible Token Arena experiment.
 *
 * This model does not imply persistence. Callers must make any durable storage/export explicit,
 * because prompt text and provider responses may contain sensitive user data.
 */
@Serializable
data class TokenArenaExperiment(
    val id: String,
    val intentLabel: String,
    val variants: List<TokenArenaVariant>,
    val tokenMeasurements: List<TokenArenaTokenMeasurement> = emptyList(),
    val responseObservations: List<TokenArenaResponseObservation> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Token Arena experiment id must not be blank" }
        require(intentLabel.isNotBlank()) { "Token Arena intent label must not be blank" }
        require(variants.isNotEmpty()) { "Token Arena experiment needs at least one variant" }

        val variantIds = variants.map(TokenArenaVariant::id)
        require(variantIds.toSet().size == variantIds.size) {
            "Token Arena variant ids must be unique"
        }
        val knownVariantIds = variantIds.toSet()
        require(tokenMeasurements.all { it.variantId in knownVariantIds }) {
            "Every token measurement must reference a known variant"
        }
        require(responseObservations.all { it.variantId in knownVariantIds }) {
            "Every response observation must reference a known variant"
        }
    }
}

/** Convert any exact local [TokenCounter] into an explicitly encoding-scoped Arena measurement. */
fun TokenCounter.measureForArena(
    variantId: String,
    text: String,
    backendLabel: String
): TokenArenaTokenMeasurement = TokenArenaTokenMeasurement(
    variantId = variantId,
    tokens = count(text).toLong(),
    mode = TokenMeasurementMode.LOCAL_EXACT_ENCODING,
    backendLabel = backendLabel,
    encodingLabel = encodingLabel
)
