package com.twojstar.llmbench.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twojstar.llmbench.data.model.AiProvider
import com.twojstar.llmbench.data.model.ModelChatMessage
import com.twojstar.llmbench.ui.theme.*
import com.twojstar.llmbench.ui.viewmodel.StudioUiState
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: StudioViewModel,
    uiState: StudioUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var promptInput by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val samplePrompts = listOf(
        "Compare how you analyze edge cases in code",
        "Explain async coroutines in Kotlin vs threads",
        "Critique my tech architecture proposal",
        "Summarize the key design principles of .ai profiles"
    )

    // Auto-scroll when new messages are appended
    LaunchedEffect(uiState.chatMessages.size, uiState.isChatGenerating) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Top header row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                PrimaryDark,
                                                AccentCyan,
                                                AccentEmerald
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Hub",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI Multi-Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ChatGPT • Gemini • Claude",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // System profile attachment badge
                            IconButton(
                                onClick = {
                                    viewModel.toggleIncludeSystemProfile(!uiState.includeSystemProfileInChat)
                                },
                                modifier = Modifier.testTag("btn_toggle_profile_attachment")
                            ) {
                                Icon(
                                    imageVector = if (uiState.includeSystemProfileInChat) Icons.Default.Psychology else Icons.Outlined.Psychology,
                                    contentDescription = "Toggle System Profile Attachment",
                                    tint = if (uiState.includeSystemProfileInChat) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // API Key configuration dialog trigger
                            IconButton(
                                onClick = { viewModel.setShowApiKeyDialog(true) },
                                modifier = Modifier.testTag("btn_api_keys_settings")
                            ) {
                                BadgedBox(
                                    badge = {
                                        val hasAnyKey = uiState.apiKeyConfig.geminiKey.isNotBlank() ||
                                                uiState.apiKeyConfig.openAiKey.isNotBlank() ||
                                                uiState.apiKeyConfig.claudeKey.isNotBlank() ||
                                                uiState.apiKeyConfig.deepseekKey.isNotBlank() ||
                                                uiState.apiKeyConfig.kimiKey.isNotBlank() ||
                                                uiState.apiKeyConfig.openRouterKey.isNotBlank() ||
                                                uiState.apiKeyConfig.aiHubMixKey.isNotBlank()
                                        if (hasAnyKey) {
                                            Badge(
                                                containerColor = AccentEmerald,
                                                modifier = Modifier.size(6.dp)
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Configure API Keys",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Clear history button
                            IconButton(
                                onClick = { viewModel.clearChatHistory() },
                                modifier = Modifier.testTag("btn_clear_chat_history")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = "Clear History",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Provider selector pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_selector_row")
                    ) {
                        items(AiProvider.entries) { provider ->
                            val isSelected = uiState.selectedChatProvider == provider
                            val providerColor = getProviderColor(provider)

                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setChatProvider(provider) },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = getProviderIcon(provider),
                                            contentDescription = null,
                                            tint = if (isSelected) providerColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = provider.shortName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = providerColor.copy(alpha = 0.15f),
                                    selectedLabelColor = providerColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderColor = providerColor,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("chip_provider_${provider.id}")
                            )
                        }
                    }

                    // Model selection dropdown for single provider
                    if (uiState.selectedChatProvider != AiProvider.ALL) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showModelMenu = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Model: ${uiState.selectedChatModel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )

                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                uiState.selectedChatProvider.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = model,
                                                fontWeight = if (uiState.selectedChatModel == model) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setChatModel(model)
                                            showModelMenu = false
                                        },
                                        leadingIcon = {
                                            if (uiState.selectedChatModel == model) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = getProviderColor(uiState.selectedChatProvider),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Quick sample prompt chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        items(samplePrompts) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.sendChatMessage(prompt)
                                    }
                                    .testTag("sample_chat_prompt_${prompt.take(12)}")
                            ) {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Input row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            placeholder = {
                                val destination = when (uiState.selectedChatProvider) {
                                    AiProvider.ALL -> "Ask Gemini, ChatGPT, Claude, DeepSeek & Kimi..."
                                    AiProvider.GEMINI -> "Ask Google Gemini..."
                                    AiProvider.CHATGPT -> "Ask OpenAI ChatGPT..."
                                    AiProvider.CLAUDE -> "Ask Anthropic Claude..."
                                    AiProvider.DEEPSEEK -> "Ask DeepSeek..."
                                    AiProvider.KIMI -> "Ask Moonshot Kimi..."
                                    AiProvider.OPENROUTER -> "Ask a free OpenRouter model..."
                                    AiProvider.AIHUBMIX -> "Ask a free AIHubMix model..."
                                }
                                Text(
                                    text = destination,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field")
                        )

                        FloatingActionButton(
                            onClick = {
                                if (promptInput.isNotBlank() && !uiState.isChatGenerating) {
                                    val textToSend = promptInput
                                    if (viewModel.sendChatMessage(textToSend)) {
                                        promptInput = ""
                                    }
                                }
                            },
                            shape = CircleShape,
                            containerColor = getProviderColor(uiState.selectedChatProvider),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_prompt_button")
                        ) {
                            if (uiState.isChatGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send Prompt",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_message_list")
        ) {
            items(
                items = uiState.chatMessages,
                key = { it.id }
            ) { message ->
                ChatMessageItem(
                    message = message,
                    onCopyText = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI Message", text)
                        clipboard.setPrimaryClip(clip)
                        viewModel.showSnackbar("Copied to clipboard")
                    },
                    onRetryPrompt = { prompt ->
                        viewModel.sendChatMessage(prompt)
                    }
                )
            }

            // Typing / generating indicators
            if (uiState.isChatGenerating) {
                item(key = "generating_indicator") {
                    GeneratingIndicator(activeProviders = uiState.activeGeneratingProviders)
                }
            }
        }
    }

    // API Keys Dialog
    if (uiState.showApiKeyDialog) {
        ApiKeySettingsDialog(
            currentKeys = uiState.apiKeyConfig,
            onDismiss = { viewModel.setShowApiKeyDialog(false) },
            onSave = { gemini, openAi, claude, deepseek, kimi, openRouter, aiHubMix ->
                viewModel.saveApiKeys(gemini, openAi, claude, deepseek, kimi, openRouter, aiHubMix)
            }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ModelChatMessage,
    onCopyText: (String) -> Unit,
    onRetryPrompt: (String) -> Unit
) {
    val isUser = message.sender == "user"
    val provider = message.provider ?: AiProvider.GEMINI
    val providerColor = getProviderColor(provider)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble_${provider.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Bubble Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(providerColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getProviderIcon(provider),
                        contentDescription = null,
                        tint = providerColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = providerColor
                )
                message.modelName?.let { model ->
                    Text(
                        text = "• $model",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (message.isSimulated) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.testTag("badge_simulated_${message.id}")
                    ) {
                        Text(
                            text = "SIMULATED",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (message.latencyMs != null) {
                    Text(
                        text = "(${message.latencyMs}ms)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Message Content Box
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            ),
            border = if (!isUser) {
                BorderStroke(1.dp, providerColor.copy(alpha = 0.25f))
            } else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else if (message.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    lineHeight = 21.sp
                )

                // Active style profile or system prompt notes badge
                if (!isUser && message.activeProfileNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        message.activeProfileNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Action row for assistant responses
                if (!isUser) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { onCopyText(message.text) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeneratingIndicator(activeProviders: Set<AiProvider>) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
                val providerNames = if (activeProviders.isEmpty()) {
                    "AI Assistant"
                } else {
                    activeProviders.joinToString(", ") { it.shortName }
                }
                Text(
                    text = "Generating response from $providerNames...",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
    }
}

@Composable
fun ApiKeySettingsDialog(
    currentKeys: com.twojstar.llmbench.data.model.ApiKeyConfig,
    onDismiss: () -> Unit,
    onSave: (
        gemini: String,
        openAi: String,
        claude: String,
        deepseek: String,
        kimi: String,
        openRouter: String,
        aiHubMix: String
    ) -> Unit
) {
    var geminiKey by remember { mutableStateOf(currentKeys.geminiKey) }
    var openAiKey by remember { mutableStateOf(currentKeys.openAiKey) }
    var claudeKey by remember { mutableStateOf(currentKeys.claudeKey) }
    var deepseekKey by remember { mutableStateOf(currentKeys.deepseekKey) }
    var kimiKey by remember { mutableStateOf(currentKeys.kimiKey) }
    var openRouterKey by remember { mutableStateOf(currentKeys.openRouterKey) }
    var aiHubMixKey by remember { mutableStateOf(currentKeys.aiHubMixKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, tint = AccentEmerald)
                Text("AI Provider API Keys", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter API keys for direct providers and optional gateways. Keys are stored locally on device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Google Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_gemini_api_key")
                )

                OutlinedTextField(
                    value = openAiKey,
                    onValueChange = { openAiKey = it },
                    label = { Text("OpenAI API Key (ChatGPT)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = AccentEmerald)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_openai_api_key")
                )

                OutlinedTextField(
                    value = claudeKey,
                    onValueChange = { claudeKey = it },
                    label = { Text("Anthropic Claude API Key") },
                    placeholder = { Text("sk-ant-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Flare, contentDescription = null, tint = AccentAmber)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_claude_api_key")
                )

                OutlinedTextField(
                    value = deepseekKey,
                    onValueChange = { deepseekKey = it },
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF2563EB))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_deepseek_api_key")
                )

                OutlinedTextField(
                    value = kimiKey,
                    onValueChange = { kimiKey = it },
                    label = { Text("Moonshot Kimi API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = Color(0xFF8B5CF6))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_kimi_api_key")
                )

                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = { openRouterKey = it },
                    label = { Text("OpenRouter API Key") },
                    placeholder = { Text("sk-or-v1-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Route, contentDescription = null, tint = Color(0xFF6366F1))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_openrouter_api_key")
                )

                OutlinedTextField(
                    value = aiHubMixKey,
                    onValueChange = { aiHubMixKey = it },
                    label = { Text("AIHubMix API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF14B8A6))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_aihubmix_api_key")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(geminiKey, openAiKey, claudeKey, deepseekKey, kimiKey, openRouterKey, aiHubMixKey) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                modifier = Modifier.testTag("btn_save_api_keys")
            ) {
                Text("Save Keys")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_api_keys")
            ) {
                Text("Cancel")
            }
        }
    )
}

fun getProviderColor(provider: AiProvider): Color {
    return when (provider) {
        AiProvider.GEMINI -> Color(0xFF0EA5E9) // Vibrant Cyan/Sky
        AiProvider.CHATGPT -> Color(0xFF10B981) // Emerald Green
        AiProvider.CLAUDE -> Color(0xFFF59E0B) // Amber/Terracotta
        AiProvider.DEEPSEEK -> Color(0xFF2563EB) // Deep Blue
        AiProvider.KIMI -> Color(0xFF8B5CF6) // Violet / Electric Blue
        AiProvider.OPENROUTER -> Color(0xFF6366F1) // Indigo gateway
        AiProvider.AIHUBMIX -> Color(0xFF14B8A6) // Teal gateway
        AiProvider.ALL -> Color(0xFF8B5CF6) // Purple Multi
    }
}

fun getProviderIcon(provider: AiProvider): ImageVector {
    return when (provider) {
        AiProvider.GEMINI -> Icons.Default.AutoAwesome
        AiProvider.CHATGPT -> Icons.Default.SmartToy
        AiProvider.CLAUDE -> Icons.Default.Flare
        AiProvider.DEEPSEEK -> Icons.Default.Psychology
        AiProvider.KIMI -> Icons.Default.ElectricBolt
        AiProvider.OPENROUTER -> Icons.Default.Route
        AiProvider.AIHUBMIX -> Icons.Default.Cloud
        AiProvider.ALL -> Icons.Default.Hub
    }
}
