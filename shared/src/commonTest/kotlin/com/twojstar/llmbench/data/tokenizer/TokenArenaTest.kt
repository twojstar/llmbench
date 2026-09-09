package com.twojstar.llmbench.data.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenArenaTest {
    private object FakeCounter : TokenCounter {
        override val encodingLabel: String = "fake-encoding"
        override fun count(text: String): Int = text.length
    }

    @Test
    fun localMeasurementStaysExplicitlyEncodingScopedAndBoundToVariantPrompt() {
        val variant = TokenArenaVariant(
            id = "short",
            label = "Short",
            prompt = "hello"
        )
        val measurement = FakeCounter.measureForArena(
            variant = variant,
            backendLabel = "fake-local"
        )

        assertEquals("short", measurement.variantId)
        assertEquals(variant.promptFingerprint, measurement.promptFingerprint)
        assertEquals(5L, measurement.tokens)
        assertEquals(TokenMeasurementMode.LOCAL_EXACT_ENCODING, measurement.mode)
        assertEquals("fake-local", measurement.backendLabel)
        assertEquals("fake-encoding", measurement.encodingLabel)
        assertEquals(null, measurement.providerId)
        assertEquals(null, measurement.modelName)
    }

    @Test
    fun providerExactMeasurementRequiresProviderAndModel() {
        assertFailsWith<IllegalArgumentException> {
            TokenArenaTokenMeasurement(
                variantId = "short",
                promptFingerprint = stablePromptFingerprint("hello"),
                tokens = 10,
                mode = TokenMeasurementMode.PROVIDER_EXACT,
                backendLabel = "provider-api"
            )
        }
    }

    @Test
    fun experimentRejectsDuplicateUnknownAndStaleVariantReferences() {
        assertFailsWith<IllegalArgumentException> {
            TokenArenaExperiment.create(
                id = "duplicate",
                intentLabel = INTENT_LABEL,
                variants = listOf(
                    TokenArenaVariant(VARIANT_ID, "A", "first"),
                    TokenArenaVariant(VARIANT_ID, "Again", "second")
                )
            )
        }

        val original = TokenArenaVariant(VARIANT_ID, "A", "first")
        val measurement = FakeCounter.measureForArena(original, "fake-local")
        assertFailsWith<IllegalArgumentException> {
            TokenArenaExperiment.create(
                id = "unknown-ref",
                intentLabel = INTENT_LABEL,
                variants = listOf(original),
                tokenMeasurements = listOf(
                    TokenArenaTokenMeasurement(
                        variantId = "missing",
                        promptFingerprint = original.promptFingerprint,
                        tokens = 3,
                        mode = TokenMeasurementMode.REFERENCE_FALLBACK,
                        backendLabel = "estimate"
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TokenArenaExperiment.create(
                id = "stale-prompt",
                intentLabel = INTENT_LABEL,
                variants = listOf(original.copy(prompt = "edited")),
                tokenMeasurements = listOf(measurement)
            )
        }
    }

    @Test
    fun experimentSnapshotsCallerCollections() {
        val variant = TokenArenaVariant(VARIANT_ID, "A", "first")
        val variants = mutableListOf(variant)
        val measurements = mutableListOf(FakeCounter.measureForArena(variant, "fake-local"))
        val experiment = TokenArenaExperiment.create(
            id = "snapshot",
            intentLabel = INTENT_LABEL,
            variants = variants,
            tokenMeasurements = measurements
        )

        variants.clear()
        measurements.clear()

        assertEquals(listOf(variant), experiment.variants)
        assertEquals(1, experiment.tokenMeasurements.size)
    }

    @Test
    fun onlyCompleteSuccessfulLiveResponsesEnterLiveEfficiencyRanking() {
        val variant = TokenArenaVariant(VARIANT_ID, "A", "prompt")
        fun observation(
            provenance: ArenaResponseProvenance,
            isError: Boolean = false,
            isPartial: Boolean = false
        ) = TokenArenaResponseObservation(
            variantId = variant.id,
            promptFingerprint = variant.promptFingerprint,
            providerId = "provider",
            modelName = "model",
            provenance = provenance,
            isError = isError,
            isPartial = isPartial
        )

        assertTrue(observation(ArenaResponseProvenance.LIVE_PROVIDER).canEnterLiveEfficiencyRanking)
        assertFalse(
            observation(
                ArenaResponseProvenance.LIVE_PROVIDER,
                isError = true
            ).canEnterLiveEfficiencyRanking
        )
        assertFalse(
            observation(
                ArenaResponseProvenance.LIVE_PROVIDER,
                isPartial = true
            ).canEnterLiveEfficiencyRanking
        )
        assertFalse(observation(ArenaResponseProvenance.CACHED_REPLAY).canEnterLiveEfficiencyRanking)
        assertFalse(observation(ArenaResponseProvenance.SIMULATED_FALLBACK).canEnterLiveEfficiencyRanking)
    }

    private companion object {
        const val VARIANT_ID = "a"
        const val INTENT_LABEL = "same intent"
    }
}
