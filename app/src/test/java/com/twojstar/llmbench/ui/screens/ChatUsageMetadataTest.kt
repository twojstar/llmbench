package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.ModelChatMessage
import com.twojstar.llmbench.data.model.ProviderUsage
import org.junit.Assert.assertEquals
import org.junit.Test

private const val ASSISTANT_SENDER = "assistant"
private const val ANSWER_TEXT = "answer"

class ChatUsageMetadataTest {
    @Test
    fun formatsLatencyProviderTokensAndReportedCost() {
        val message = ModelChatMessage(
            id = "m1",
            sender = ASSISTANT_SENDER,
            text = ANSWER_TEXT,
            latencyMs = 842,
            usage = ProviderUsage(
                inputTokens = 1_234,
                outputTokens = 456,
                totalTokens = 1_690,
                cachedInputTokens = 120,
                reasoningTokens = 80,
                costUsd = 0.00125
            )
        )
        assertEquals(
            "842ms • 1,234 in • 456 out • 1,690 total • 120 cached • 80 reasoning • $0.00125",
            formatChatResponseDiagnostics(message)
        )
    }

    @Test
    fun preservesTinyPositiveReportedCosts() {
        val message = ModelChatMessage(
            id = "m2",
            sender = ASSISTANT_SENDER,
            text = ANSWER_TEXT,
            usage = ProviderUsage(costUsd = 0.0000004)
        )
        assertEquals("<$0.000001", formatChatResponseDiagnostics(message))
    }

    @Test
    fun keepsLatencyOnlyResponsesCompact() {
        val message = ModelChatMessage(
            id = "m3",
            sender = ASSISTANT_SENDER,
            text = ANSWER_TEXT,
            latencyMs = 120
        )
        assertEquals("120ms", formatChatResponseDiagnostics(message))
    }
}
