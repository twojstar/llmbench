package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.CHAT_ROLE_ASSISTANT
import com.twojstar.llmbench.data.model.ModelChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatResponseMarkdownEligibilityTest {
    private val completeResponse = ModelChatMessage(
        id = "a1",
        sender = CHAT_ROLE_ASSISTANT,
        provider = AiProvider.CHATGPT,
        text = "Useful answer"
    )

    @Test
    fun completedResponseIsEligibleWhenWorkspaceIsReady() {
        assertTrue(
            canOpenResponseAsMarkdown(
                message = completeResponse,
                isPreparingChatMarkdown = false,
                isWorkspaceBusy = false
            )
        )
    }

    @Test
    fun workspaceStateCanGateCompletedResponse() {
        assertFalse(canOpenResponseAsMarkdown(completeResponse, true, false))
        assertFalse(canOpenResponseAsMarkdown(completeResponse, false, true))
    }
}
