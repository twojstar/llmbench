package com.twojstar.llmbench.data.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTokenCounterTest {
    @Test
    fun countsEmptyTextAsZero() {
        assertEquals(0, LocalTokenCounter.count(""))
    }

    @Test
    fun countsKnownO200kSample() {
        assertEquals(2, LocalTokenCounter.count("hello world"))
    }

    @Test
    fun treatsSpecialTokenTextAsOrdinaryInput() {
        val count = LocalTokenCounter.count("hello <|endoftext|> world")

        // If the marker were recognized as one special token, this phrase would be
        // only three tokens: prefix + marker + suffix. Ordinary encoding must split it.
        assertTrue(count > 3)
    }
}
