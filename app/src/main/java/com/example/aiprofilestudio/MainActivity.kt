package com.example.aiprofilestudio

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
import com.example.aiprofilestudio.ui.screens.*
import com.example.aiprofilestudio.ui.theme.AiProfileStudioTheme
import com.example.aiprofilestudio.ui.viewmodel.NavigationTab
import com.example.aiprofilestudio.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AiProfileStudioTheme {
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
                        NavigationBar(
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
                                selected = uiState.currentTab == NavigationTab.STUDIO,
                                onClick = { viewModel.selectTab(NavigationTab.STUDIO) },
                                icon = { Icon(Icons.Default.Tune, contentDescription = "Studio") },
                                label = {
                                    Text(
                                        "Studio",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab == NavigationTab.STUDIO) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_studio")
                            )
                            NavigationBarItem(
                                selected = uiState.currentTab == NavigationTab.INSTRUCTIONS,
                                onClick = { viewModel.selectTab(NavigationTab.INSTRUCTIONS) },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = "Instructions") },
                                label = {
                                    Text(
                                        "Prompt",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab == NavigationTab.INSTRUCTIONS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_instructions")
                            )
                            NavigationBarItem(
                                selected = uiState.currentTab == NavigationTab.SKILLS,
                                onClick = { viewModel.selectTab(NavigationTab.SKILLS) },
                                icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Skills") },
                                label = {
                                    Text(
                                        "Skills",
                                        fontSize = 11.sp,
                                        fontWeight = if (uiState.currentTab == NavigationTab.SKILLS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_skills")
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
                                onOpenNativeCompare = { viewModel.selectTab(NavigationTab.COMPARE_HUB) }
                            )
                            NavigationTab.COMPARE_HUB -> ChatScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                            NavigationTab.STUDIO -> StudioScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigateToInstructions = { viewModel.selectTab(NavigationTab.INSTRUCTIONS) }
                            )
                            NavigationTab.INSTRUCTIONS -> InstructionsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigateToPlayground = { viewModel.selectTab(NavigationTab.COMPARE_HUB) }
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
