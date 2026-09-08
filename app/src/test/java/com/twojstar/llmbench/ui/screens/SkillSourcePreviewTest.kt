package com.twojstar.llmbench.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillSourcePreviewTest {
    @Test
    fun shortSourceIsReturnedUnchanged() {
        val source = "# Instructions\nKeep this short."

        assertEquals(source, boundedSkillSourceForDisplay(source))
    }

    @Test
    fun largeSourceIsBoundedWithoutClaimingUnavailableActions() {
        val source = "x".repeat(MAX_SOURCE_PREVIEW_CHARS + 100)

        val displayed = boundedSkillSourceForDisplay(source)

        assertTrue(displayed.startsWith("x".repeat(MAX_SOURCE_PREVIEW_CHARS)))
        assertTrue(displayed.endsWith("… source preview truncated …"))
        assertFalse(displayed.contains("copy", ignoreCase = true))
        assertTrue(displayed.length < source.length)
    }
}
