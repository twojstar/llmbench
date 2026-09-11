package com.twojstar.llmbench.data.codebench

import kotlin.math.roundToInt

internal object CodebenchImageSampling {
    fun sampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        maxDimension: Int = CodebenchBarcodeCodec.MAX_RENDER_DIMENSION
    ): Int {
        require(sourceWidth > 0 && sourceHeight > 0) { "Image dimensions must be positive" }
        require(maxDimension > 0) { "Maximum image dimension must be positive" }
        val required = (maxOf(sourceWidth, sourceHeight).toLong() + maxDimension - 1L) / maxDimension
        var sample = 1L
        while (sample < required) sample *= 2L
        return sample.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun boundedDimensions(width: Int, height: Int, maxDimension: Int = CodebenchBarcodeCodec.MAX_RENDER_DIMENSION): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(maxDimension > 0) { "Maximum image dimension must be positive" }
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / largest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}
