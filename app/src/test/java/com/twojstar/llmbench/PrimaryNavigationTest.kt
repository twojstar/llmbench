package com.twojstar.llmbench

import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryNavigationTest {
    @Test
    fun studioToolsStayUnderOnePrimaryDestination() {
        listOf(
            NavigationTab.STUDIO,
            NavigationTab.INSTRUCTIONS,
            NavigationTab.YAML,
            NavigationTab.PLAYGROUND,
            NavigationTab.SKILLS
        ).forEach { tab -> assertTrue(tab.belongsToStudioSection()) }
    }

    @Test
    fun dailyChatDestinationsRemainIndependent() {
        assertFalse(NavigationTab.WEB_CHATS.belongsToStudioSection())
        assertFalse(NavigationTab.COMPARE_HUB.belongsToStudioSection())
    }

    @Test
    fun renderedPromptTestChatOpensProfilePlayground() {
        assertEquals(NavigationTab.PLAYGROUND, profilePlaygroundDestination())
    }
}
