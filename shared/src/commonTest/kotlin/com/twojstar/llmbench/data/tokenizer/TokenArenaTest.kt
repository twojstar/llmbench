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
    fun localMeasurementStaysExplicitlyEncodingScoped() {
        val measurement = FakeCounter.measureForArena(
            variantId = "short",
            text = "hello",
            backendLabel = "fake-local"
        )

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
                tokens = 10,
                mode = TokenMeasurementMode.PROVIDER_EXACT,
                backendLabel = "provider-api"
            )
        }
    }

    @Test
    fun experimentRejectsDuplicateAndUnknownVariantReferences() {
        assertFailsWith<IllegalArgumentException> {
            TokenArenaExperiment(
                id = "duplicate",
                intentLabel = "same intent",
                variants = listOf(
                    TokenArenaVariant("a", "A", "first"),
                    TokenArenaVariant("a", "Again", "second")
                )
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TokenArenaExperiment(
                id = "unknown-ref",
                intentLabel = "same intent",
                variants = listOf(TokenArenaVariant("a", "A", "first")),
                tokenMeasurements = listOf(
                    TokenArenaTokenMeasurement(
                        variantId = "missing",
                        tokens = 3,
                        mode = TokenMeasurementMode.REFERENCE_FALLBACK,
                        backendLabel = "estimate"
                    )
                )
            )
        }
    }

    @Test
    fun onlySuccessfulLiveResponsesEnterLiveEfficiencyRanking() {
        fun observation(
            provenance: ArenaResponseProvenance,
            isError: Boolean = false
        ) = TokenArenaResponseObservation(
            variantId = "a",
            providerId = "provider",
            modelName = "model",
            provenance = provenance,
            isError = isError
        )

        assertTrue(observation(ArenaResponseProvenance.LIVE_PROVIDER).canEnterLiveEfficiencyRanking)
        assertFalse(observation(ArenaResponseProvenance.LIVE_PROVIDER, isError = true).canEnterLiveEfficiencyRanking)
        assertFalse(observation(ArenaResponseProvenance.CACHED_REPLAY).canEnterLiveEfficiencyRanking)
        assertFalse(observation(ArenaResponseProvenance.SIMULATED_FALLBACK).canEnterLiveEfficiencyRanking)
    }
}
