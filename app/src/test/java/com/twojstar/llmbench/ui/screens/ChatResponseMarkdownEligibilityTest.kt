package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.CHAT_ROLE_ASSISTANT
import com.twojstar.llmbench.data.model.CHAT_ROLE_USER
import com.twojstar.llmbench.data.model.ModelChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatResponseMarkdownEligibilityTest {
    @Test
    fun completedResponseIsEligible() {
        val message = ModelChatMessage(
            id = "a1",
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.CHATGPT,
            text = "Useful answer"
        )

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
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.CHATGPT,
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
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.CHATGPT,
            text = "Provider failed",
            isError = true
        )
        val complete = ModelChatMessage(
            id = "a4",
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.CHATGPT,
            text = "Complete"
        )

        assertFalse(canOpenResponseAsMarkdown(error, false, false))
        assertFalse(canOpenResponseAsMarkdown(complete, true, false))
        assertFalse(canOpenResponseAsMarkdown(complete, false, true))
    }

    @Test
    fun onboardingCardIsNotAResponseAsset() {
        val welcome = ModelChatMessage(
            id = "welcome",
            sender = CHAT_ROLE_ASSISTANT,
            provider = AiProvider.ALL,
            text = "Welcome"
        )

        assertFalse(canOpenResponseAsMarkdown(welcome, false, false))
    }

    @Test
    fun userMessageWithProviderIsNotAResponseAsset() {
        val userMessage = ModelChatMessage(
            id = "u1",
            sender = CHAT_ROLE_USER,
            provider = AiProvider.CHATGPT,
            text = "User prompt"
        )

        assertFalse(canOpenResponseAsMarkdown(userMessage, false, false))
    }
}
