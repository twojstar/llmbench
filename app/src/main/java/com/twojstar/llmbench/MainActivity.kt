package com.twojstar.llmbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.twojstar.llmbench.data.preferences.StudioStateStore
import com.twojstar.llmbench.ui.screens.*
import com.twojstar.llmbench.ui.theme.LlmBenchTheme
import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import com.twojstar.llmbench.ui.viewmodel.restoreStudioSnapshot
import com.twojstar.llmbench.ui.viewmodel.toStudioStateSnapshot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun profilePlaygroundDestination(): NavigationTab = NavigationTab.PLAYGROUND

internal fun showPrimaryBottomNavigation(tab: NavigationTab): Boolean = tab != NavigationTab.WEB_CHATS

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val studioStateStore = StudioStateStore(applicationContext)
        studioStateStore.load()?.let(viewModel::restoreStudioSnapshot)
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.toStudioStateSnapshot() }
                .distinctUntilChanged()
                .collect { snapshot -> studioStateStore.save(snapshot) }
        }

        setContent {
            LlmBenchTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.dismissSnackbar()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showPrimaryBottomNavigation(uiState.currentTab)) NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("main_bottom_nav")
                        ) {
                            NavigationBarItem(
                                selected = uiState.currentTab == NavigationTab.WEB_CHATS,
                                onClick = { viewModel.selectTab(NavigationTab.WEB_CHATS) },
                                icon = { Icon(Icons.Default.Language, contentDescription = "Web AI Accounts") },
                                label = {
                                    Text(
                                        "Web AI",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab == NavigationTab.WEB_CHATS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_web_chats")
                            )
                            NavigationBarItem(
                                selected = uiState.currentTab == NavigationTab.COMPARE_HUB,
                                onClick = { viewModel.selectTab(NavigationTab.COMPARE_HUB) },
                                icon = { Icon(Icons.Default.Forum, contentDescription = "AI Compare Hub") },
                                label = {
                                    Text(
                                        "Compare",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab == NavigationTab.COMPARE_HUB) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_compare_hub")
                            )
                            NavigationBarItem(
                                selected = uiState.currentTab.belongsToStudioSection(),
                                onClick = { viewModel.selectTab(NavigationTab.STUDIO) },
                                icon = { Icon(Icons.Default.Tune, contentDescription = "Studio") },
                                label = {
                                    Text(
                                        "Studio",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab.belongsToStudioSection()) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_studio")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        when (uiState.currentTab) {
                            NavigationTab.WEB_CHATS -> WebChatScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onOpenNativeCompare = { viewModel.selectTab(NavigationTab.COMPARE_HUB) },
                                onOpenStudio = { viewModel.selectTab(NavigationTab.STUDIO) }
                            )
                            NavigationTab.COMPARE_HUB -> ChatScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                            NavigationTab.STUDIO -> StudioScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigateToInstructions = { viewModel.selectTab(NavigationTab.INSTRUCTIONS) },
                                onNavigateToSkills = { viewModel.selectTab(NavigationTab.SKILLS) },
                                onNavigateToYaml = { viewModel.selectTab(NavigationTab.YAML) },
                                onNavigateToPlayground = { viewModel.selectTab(profilePlaygroundDestination()) }
                            )
                            NavigationTab.INSTRUCTIONS -> InstructionsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigateToPlayground = { viewModel.selectTab(profilePlaygroundDestination()) }
                            )
                            NavigationTab.YAML -> YamlEditorScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                            NavigationTab.PLAYGROUND -> PlaygroundScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                            NavigationTab.SKILLS -> SkillsBrowserScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
