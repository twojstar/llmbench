package com.twojstar.llmbench.data.codebench

import org.junit.Assert.assertEquals
import org.junit.Test

class CodebenchImageSamplingTest {
    @Test
    fun imagesWithinCodecLimitAreNotDownsampled() {
        assertEquals(1, CodebenchImageSampling.sampleSize(2_048, 2_048))
    }

    @Test
    fun sampleSizeUsesPowerOfTwoThatBoundsLargestDimension() {
        assertEquals(4, CodebenchImageSampling.sampleSize(4_097, 6_145))
    }

    @Test
    fun sampleSizeUsesTheConfiguredLimit() {
        assertEquals(4, CodebenchImageSampling.sampleSize(1_000, 1_001, maxDimension = 256))
    }

    @Test
    fun boundedDimensionsPreserveAspectRatio() {
        assertEquals(2_048 to 1_024, CodebenchImageSampling.boundedDimensions(4_000, 2_000))
    }
}
