package com.twojstar.llmbench.data.tokenizer

/**
 * Shared responsiveness guard for interactive token-count surfaces.
 *
 * This is a UI/workflow budget rather than a tokenizer capability limit. Larger inputs may still be
 * tokenized explicitly by batch/offline callers that can tolerate the work.
 */
const val MAX_INTERACTIVE_TOKENIZED_CHARS: Int = 1_000_000

/** Platform-neutral contract for exact local token counters. */
interface TokenCounter {
    val encodingLabel: String

    fun count(text: String): Int
}
