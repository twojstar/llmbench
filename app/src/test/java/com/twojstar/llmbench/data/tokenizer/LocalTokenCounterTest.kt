package com.twojstar.llmbench.data.tokenizer

import org.junit.Assert.assertEquals
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
        assertEquals(9, LocalTokenCounter.count("hello <|endoftext|> world"))
    }
}
