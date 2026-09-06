package com.twojstar.llmbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.webChatSections
import com.twojstar.llmbench.data.security.TextInspectionResult
import com.twojstar.llmbench.data.security.TextInspector
import com.twojstar.llmbench.share.IncomingSharePayload
import com.twojstar.llmbench.share.PendingWebShare
import com.twojstar.llmbench.share.extractIncomingSharePayload
import com.twojstar.llmbench.share.normalizeIncomingSharePayload
import com.twojstar.llmbench.ui.screens.*
import com.twojstar.llmbench.ui.theme.LlmBenchTheme
import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel

internal fun profilePlaygroundDestination(): NavigationTab = NavigationTab.PLAYGROUND

internal fun showPrimaryBottomNavigation(tab: NavigationTab): Boolean = tab != NavigationTab.WEB_CHATS

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()
    private var retainedShareIntentHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        retainedShareIntentHandled = savedInstanceState?.getBoolean(KEY_SHARE_INTENT_HANDLED) == true
        restoreShareState(savedInstanceState)
        if (!retainedShareIntentHandled) handleIncomingShareIntent(intent)

        setContent {
            LlmBenchTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                uiState.incomingShare?.let { payload ->
                    IncomingShareProviderDialog(
                        payload = payload,
                        favoriteServices = uiState.favoriteWebServices,
                        onSelect = viewModel::routeIncomingShareToWeb,
                        onDismiss = viewModel::dismissIncomingShare
                    )
                }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        retainedShareIntentHandled = false
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHARE_INTENT_HANDLED, retainedShareIntentHandled)
        saveShareState(outState)
    }

    private fun handleIncomingShareIntent(intent: Intent) {
        val isShareIntent = intent.action == Intent.ACTION_SEND ||
            intent.action == Intent.ACTION_SEND_MULTIPLE
        if (!isShareIntent) return
        retainedShareIntentHandled = true
        extractIncomingSharePayload(intent)?.let(viewModel::receiveIncomingShare)
    }

    private fun saveShareState(outState: Bundle) {
        val state = viewModel.uiState.value
        val pending = state.pendingWebShare
        val incoming = state.incomingShare
        when {
            pending != null -> {
                outState.putString(KEY_SHARE_STAGE, SHARE_STAGE_PENDING)
                outState.putLong(KEY_SHARE_ID, pending.id)
                outState.putString(KEY_SHARE_SERVICE_ID, pending.service.id)
                writeSharePayload(outState, pending.payload)
            }
            incoming != null -> {
                outState.putString(KEY_SHARE_STAGE, SHARE_STAGE_INCOMING)
                writeSharePayload(outState, incoming)
            }
        }
    }

    private fun restoreShareState(savedState: Bundle?) {
        val stage = savedState?.getString(KEY_SHARE_STAGE) ?: return
        val payload = normalizeIncomingSharePayload(
            text = savedState.getString(KEY_SHARE_TEXT),
            uriStrings = savedState.getStringArrayList(KEY_SHARE_URIS).orEmpty()
        ) ?: return
        val pending = if (stage == SHARE_STAGE_PENDING) {
            val serviceId = savedState.getString(KEY_SHARE_SERVICE_ID)
            val service = WebAiService.entries.firstOrNull { it.id == serviceId }
            val shareId = savedState.getLong(KEY_SHARE_ID, 0L)
            if (service != null && shareId > 0L) PendingWebShare(shareId, service, payload) else null
        } else null
        viewModel.restoreShareState(
            incomingShare = payload.takeIf { stage == SHARE_STAGE_INCOMING },
            pendingShare = pending
        )
    }

    private fun writeSharePayload(outState: Bundle, payload: IncomingSharePayload) {
        outState.putString(KEY_SHARE_TEXT, payload.text)
        outState.putStringArrayList(KEY_SHARE_URIS, ArrayList(payload.uriStrings))
    }

    private companion object {
        const val KEY_SHARE_INTENT_HANDLED = "llmbench.share.intent_handled"
        const val KEY_SHARE_STAGE = "llmbench.share.stage"
        const val KEY_SHARE_ID = "llmbench.share.id"
        const val KEY_SHARE_SERVICE_ID = "llmbench.share.service_id"
        const val KEY_SHARE_TEXT = "llmbench.share.text"
        const val KEY_SHARE_URIS = "llmbench.share.uris"
        const val SHARE_STAGE_INCOMING = "incoming"
        const val SHARE_STAGE_PENDING = "pending"
    }
}

@Composable
private fun IncomingShareProviderDialog(
    payload: IncomingSharePayload,
    favoriteServices: Set<WebAiService>,
    onSelect: (WebAiService) -> Unit,
    onDismiss: () -> Unit
) {
    val inspection = remember(payload.text) {
        payload.text?.let(TextInspector::inspect) ?: TextInspectionResult(emptyList())
    }
    var safetyReviewed by remember(payload.text) { mutableStateOf(!inspection.hasFindings) }
    if (!safetyReviewed) {
        SharedTextSafetyReviewDialog(
            inspection = inspection,
            onContinue = { safetyReviewed = true },
            onDismiss = onDismiss
        )
        return
    }

    val summary = buildList {
        if (payload.text != null) add("text")
        if (payload.attachmentCount > 0) {
            val suffix = if (payload.attachmentCount == 1) "" else "s"
            add("${payload.attachmentCount} attachment$suffix")
        }
    }.joinToString(" + ")
    val sections = webChatSections(favoriteServices)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share to LlmBench") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Choose a web provider for $summary.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                if (sections.favorites.isNotEmpty()) {
                    Text(
                        "Favorites",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    sections.favorites.forEach { service ->
                        TextButton(onClick = { onSelect(service) }, modifier = Modifier.fillMaxWidth()) {
                            Text("★ ${service.displayName}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                sections.primary.forEach { service ->
                    TextButton(
                        onClick = { onSelect(service) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(service.displayName, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (sections.additional.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "More chats",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    sections.additional.forEach { service ->
                        TextButton(
                            onClick = { onSelect(service) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(service.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SharedTextSafetyReviewDialog(
    inspection: TextInspectionResult,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WarningAmber, contentDescription = null)
                Text("Review shared text")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "LlmBench found ${inspection.findings.size} suspicious text marker${if (inspection.findings.size == 1) "" else "s"}. Review them before this text can be routed to a provider.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "High ${inspection.highCount} • Medium ${inspection.mediumCount} • Low ${inspection.lowCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                inspection.findings.take(12).forEach { finding ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${finding.severity.name.lowercase()} • ${finding.label} • line ${finding.line}:${finding.column}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            finding.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (inspection.findings.size > 12 || inspection.truncated) {
                    Text(
                        "More findings exist; the preview is intentionally bounded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Text(
                    "Inspection is read-only. LlmBench does not rewrite, remove or execute the shared text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onContinue) { Text("Continue to providers") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
