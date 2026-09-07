package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.ModelChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatResponseMarkdownEligibilityTest {
    @Test
    fun completedResponseIsEligible() {
        val message = ModelChatMessage(id = "a1", sender = "assistant", text = "Useful answer")

        assertTrue(
            canOpenResponseAsMarkdown(
                message = message,
                isPreparingChatMarkdown = false,
                isWorkspaceBusy = false
            )
        )
    }

    @Test
    fun partialResponseRemainsIneligibleAfterCancellation() {
        val stoppedPartial = ModelChatMessage(
            id = "a2",
            sender = "assistant",
            text = "Half of an answer",
            isPartial = true
        )

        assertFalse(
            canOpenResponseAsMarkdown(
                message = stoppedPartial,
                isPreparingChatMarkdown = false,
                isWorkspaceBusy = false
            )
        )
    }

    @Test
    fun errorAndBusyStatesAreIneligible() {
        val error = ModelChatMessage(
            id = "a3",
            sender = "assistant",
            text = "Provider failed",
            isError = true
        )
        val complete = ModelChatMessage(id = "a4", sender = "assistant", text = "Complete")

        assertFalse(canOpenResponseAsMarkdown(error, false, false))
        assertFalse(canOpenResponseAsMarkdown(complete, true, false))
        assertFalse(canOpenResponseAsMarkdown(complete, false, true))
    }
}
