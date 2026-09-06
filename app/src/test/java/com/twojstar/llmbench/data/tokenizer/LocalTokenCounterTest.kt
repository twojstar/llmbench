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
    fun probeSpecialTokenOrdinaryCount() {
        when (LocalTokenCounter.count("hello <|endoftext|> world")) {
            4 -> throw Count4()
            5 -> throw Count5()
            6 -> throw Count6()
            7 -> throw Count7()
            8 -> throw Count8()
            9 -> throw Count9()
            10 -> throw Count10()
            11 -> throw Count11()
            12 -> throw Count12()
            else -> throw UnexpectedCount()
        }
    }

    private class Count4 : RuntimeException()
    private class Count5 : RuntimeException()
    private class Count6 : RuntimeException()
    private class Count7 : RuntimeException()
    private class Count8 : RuntimeException()
    private class Count9 : RuntimeException()
    private class Count10 : RuntimeException()
    private class Count11 : RuntimeException()
    private class Count12 : RuntimeException()
    private class UnexpectedCount : RuntimeException()
}
