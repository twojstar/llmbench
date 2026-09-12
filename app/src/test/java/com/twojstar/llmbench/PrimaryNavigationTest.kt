package com.twojstar.llmbench

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import com.twojstar.llmbench.widget.LlmBenchWidgetNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun webChatUsesImmersiveNavigationShell() {
        assertFalse(showPrimaryNavigation(NavigationTab.WEB_CHATS))
        assertTrue(showPrimaryNavigation(NavigationTab.COMPARE_HUB))
        assertTrue(showPrimaryNavigation(NavigationTab.STUDIO))
    }

    @Test
    fun bottomNavigationTypesDoNotNeedExtraSystemInset() {
        assertTrue(navigationSuiteUsesBottomBar(NavigationSuiteType.NavigationBar))
        assertTrue(navigationSuiteUsesBottomBar(NavigationSuiteType.ShortNavigationBarCompact))
        assertTrue(navigationSuiteUsesBottomBar(NavigationSuiteType.ShortNavigationBarMedium))
        assertFalse(navigationSuiteUsesBottomBar(NavigationSuiteType.NavigationRail))
        assertFalse(navigationSuiteUsesBottomBar(NavigationSuiteType.WideNavigationRailCollapsed))
        assertFalse(navigationSuiteUsesBottomBar(NavigationSuiteType.WideNavigationRailExpanded))
    }

    @Test
    fun renderedPromptTestChatOpensProfilePlayground() {
        assertEquals(NavigationTab.PLAYGROUND, profilePlaygroundDestination())
    }

    @Test
    fun widgetDestinationsMapToPrimaryTabs() {
        assertEquals(
            NavigationTab.WEB_CHATS,
            LlmBenchWidgetNavigation.destination(LlmBenchWidgetNavigation.DESTINATION_WEB_AI)
        )
        assertEquals(
            NavigationTab.COMPARE_HUB,
            LlmBenchWidgetNavigation.destination(LlmBenchWidgetNavigation.DESTINATION_COMPARE)
        )
        assertEquals(
            NavigationTab.STUDIO,
            LlmBenchWidgetNavigation.destination(LlmBenchWidgetNavigation.DESTINATION_STUDIO)
        )
        assertNull(LlmBenchWidgetNavigation.destination("unknown"))
    }
}
