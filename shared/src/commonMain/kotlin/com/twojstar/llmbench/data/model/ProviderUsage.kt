package com.twojstar.llmbench.data.model

import kotlinx.serialization.Serializable

/** Provider-reported usage for one native/API response. Null fields mean the provider omitted them. */
@Serializable
data class ProviderUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val costUsd: Double? = null
)
