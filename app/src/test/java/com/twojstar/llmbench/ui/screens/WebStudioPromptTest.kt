package com.twojstar.llmbench.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebStudioPromptTest {
    @Test
    fun blankRenderedInstructionsDoNotInventAWebPrompt() {
        assertNull(studioPromptForWebChat(""))
        assertNull(studioPromptForWebChat("  \n\t"))
    }

    @Test
    fun renderedInstructionsArePassedThroughUnchanged() {
        val instructions = "Rule one.\n\nRule two."

        assertEquals(instructions, studioPromptForWebChat(instructions))
    }
}
