package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.ModelChatMessage
import java.util.Locale

internal fun formatChatResponseDiagnostics(message: ModelChatMessage): String? = buildList {
    message.latencyMs?.let { add("${it}ms") }
    message.usage?.let { usage ->
        usage.inputTokens?.let { add("${formatTokenCount(it)} in") }
        usage.outputTokens?.let { add("${formatTokenCount(it)} out") }
        usage.totalTokens?.let { add("${formatTokenCount(it)} total") }
        usage.cachedInputTokens?.let { add("${formatTokenCount(it)} cached") }
        usage.reasoningTokens?.let { add("${formatTokenCount(it)} reasoning") }
        usage.costUsd?.let { add(formatReportedCost(it)) }
    }
}.joinToString(" • ").takeIf { it.isNotBlank() }

private fun formatTokenCount(tokens: Long): String = String.format(Locale.US, "%,d", tokens)

private fun formatReportedCost(costUsd: Double): String {
    val fixed = String.format(Locale.US, "%.6f", costUsd).trimEnd('0').trimEnd('.')
    return "$$fixed"
}
