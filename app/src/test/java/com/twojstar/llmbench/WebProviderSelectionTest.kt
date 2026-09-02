package com.twojstar.llmbench

import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class WebProviderSelectionTest {
    @Test
    fun selectedWebProviderSurvivesPrimaryNavigation() {
        val viewModel = StudioViewModel()

        viewModel.selectWebService(WebAiService.KIMI)
        viewModel.selectTab(NavigationTab.STUDIO)
        viewModel.selectTab(NavigationTab.WEB_CHATS)

        assertEquals(WebAiService.KIMI, viewModel.uiState.value.selectedWebService)
    }
}