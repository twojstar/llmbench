package com.twojstar.llmbench.data.tokenizer

/** Platform-neutral contract for exact local token counters. */
interface TokenCounter {
    val encodingLabel: String

    fun count(text: String): Int
}
