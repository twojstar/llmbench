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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twojstar.llmbench.data.document.MarkdownWorkspaceRecoveryStore
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
import com.twojstar.llmbench.ui.viewmodel.ExternalMarkdownOpenResult
import com.twojstar.llmbench.ui.viewmodel.MarkdownWorkspaceViewModel
import com.twojstar.llmbench.ui.viewmodel.NavigationTab
import com.twojstar.llmbench.ui.viewmodel.StudioUiState
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun profilePlaygroundDestination(): NavigationTab = NavigationTab.PLAYGROUND

internal fun showPrimaryNavigation(tab: NavigationTab): Boolean = tab != NavigationTab.WEB_CHATS

internal fun navigationSuiteUsesBottomBar(type: NavigationSuiteType): Boolean =
    type == NavigationSuiteType.NavigationBar ||
        type == NavigationSuiteType.ShortNavigationBarCompact ||
        type == NavigationSuiteType.ShortNavigationBarMedium

@Composable
private fun PrimaryNavigationShell(
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
    content: @Composable (applyNavigationBarInset: Boolean) -> Unit
) {
    val showNavigation = showPrimaryNavigation(currentTab)
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(
        currentWindowAdaptiveInfoV2()
    )
    val navigationState = rememberNavigationSuiteScaffoldState(
        initialValue = if (showNavigation) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        }
    )

    LaunchedEffect(showNavigation) {
        navigationState.snapTo(
            if (showNavigation) NavigationSuiteScaffoldValue.Visible
            else NavigationSuiteScaffoldValue.Hidden
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentTab == NavigationTab.WEB_CHATS,
                onClick = { onSelectTab(NavigationTab.WEB_CHATS) },
                icon = { Icon(Icons.Default.Language, contentDescription = "Web AI Accounts") },
                label = { Text("Web AI") },
                modifier = Modifier.testTag("nav_tab_web_chats")
            )
            item(
                selected = currentTab == NavigationTab.COMPARE_HUB,
                onClick = { onSelectTab(NavigationTab.COMPARE_HUB) },
                icon = { Icon(Icons.Default.Forum, contentDescription = "AI Compare Hub") },
                label = { Text("Compare") },
                modifier = Modifier.testTag("nav_tab_compare_hub")
            )
            item(
                selected = currentTab.belongsToStudioSection(),
                onClick = { onSelectTab(NavigationTab.STUDIO) },
                icon = { Icon(Icons.Default.Tune, contentDescription = "Studio") },
                label = { Text("Studio") },
                modifier = Modifier.testTag("nav_tab_studio")
            )
        },
        layoutType = navigationSuiteType,
        state = navigationState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_adaptive_nav")
    ) {
        content(!showNavigation || !navigationSuiteUsesBottomBar(navigationSuiteType))
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()
    private val markdownWorkspaceViewModel: MarkdownWorkspaceViewModel by viewModels()
    private var retainedShareIntentHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        markdownWorkspaceViewModel.attachRecoveryStore(MarkdownWorkspaceRecoveryStore(noBackupFilesDir))
        markdownWorkspaceViewModel.attachLifecycle(this)
        retainedShareIntentHandled = savedInstanceState?.getBoolean(KEY_SHARE_INTENT_HANDLED) == true
        restoreShareState(savedInstanceState)
        if (!retainedShareIntentHandled) handleIncomingShareIntent(intent)

        setContent {
            LlmBenchTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                IncomingShareRoutingDialogs(
                    uiState = uiState,
                    viewModel = viewModel,
                    markdownWorkspaceViewModel = markdownWorkspaceViewModel
                )

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.dismissSnackbar()
                    }
                }

                PrimaryNavigationShell(
                    currentTab = uiState.currentTab,
                    onSelectTab = viewModel::selectTab
                ) { applyNavigationBarInset ->
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            StreambenchMiniPlayer(
                                applyNavigationBarInset = applyNavigationBarInset
                            )
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
        val payload = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> extractIncomingSharePayload(intent)
            Intent.ACTION_PROCESS_TEXT -> normalizeIncomingSharePayload(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
                uriStrings = emptyList()
            )
            else -> null
        } ?: return
        retainedShareIntentHandled = true
        viewModel.receiveIncomingShare(payload)
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
private fun IncomingShareRoutingDialogs(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    markdownWorkspaceViewModel: MarkdownWorkspaceViewModel
) {
    val markdownUiState by markdownWorkspaceViewModel.uiState.collectAsStateWithLifecycle()
    var confirmMarkdownReplace by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.incomingShare) {
        confirmMarkdownReplace = false
    }

    fun openIncomingTextInMarkdown(payload: IncomingSharePayload, allowDiscardDirty: Boolean) {
        val text = payload.text ?: return
        when (
            markdownWorkspaceViewModel.openExternalText(
                text = text,
                allowDiscardDirty = allowDiscardDirty
            )
        ) {
            ExternalMarkdownOpenResult.OPENED -> {
                confirmMarkdownReplace = false
                viewModel.dismissIncomingShare()
                viewModel.selectTab(NavigationTab.YAML)
            }
            ExternalMarkdownOpenResult.NEEDS_DISCARD -> confirmMarkdownReplace = true
            ExternalMarkdownOpenResult.BUSY -> viewModel.showSnackbar(
                "Markdown workspace is still restoring or busy. Try again when it is ready."
            )
            ExternalMarkdownOpenResult.TOO_LARGE -> viewModel.showSnackbar(
                "Shared text is larger than the 8 MiB Markdown workspace limit."
            )
        }
    }

    uiState.incomingShare?.let { payload ->
        if (confirmMarkdownReplace && payload.text != null) {
            ReplaceMarkdownDraftDialog(
                currentName = markdownUiState.displayName,
                onDiscard = { openIncomingTextInMarkdown(payload, allowDiscardDirty = true) },
                onDismiss = { confirmMarkdownReplace = false }
            )
        } else {
            IncomingShareProviderDialog(
                payload = payload,
                favoriteServices = uiState.favoriteWebServices,
                onOpenMarkdown = if (payload.text != null && payload.attachmentCount == 0) {
                    { openIncomingTextInMarkdown(payload, allowDiscardDirty = false) }
                } else {
                    null
                },
                markdownEnabled = !markdownUiState.isBusy,
                onSelect = viewModel::routeIncomingShareToWeb,
                onDismiss = viewModel::dismissIncomingShare
            )
        }
    }
}

@Composable
private fun IncomingShareProviderDialog(
    payload: IncomingSharePayload,
    favoriteServices: Set<WebAiService>,
    onOpenMarkdown: (() -> Unit)?,
    markdownEnabled: Boolean,
    onSelect: (WebAiService) -> Unit,
    onDismiss: () -> Unit
) {
    var safetyReviewed by rememberSaveable(payload.text, payload.uriStrings) { mutableStateOf(false) }
    var inspection by remember(payload.text, payload.uriStrings) {
        mutableStateOf<TextInspectionResult?>(
            if (payload.text == null) TextInspectionResult(emptyList()) else null
        )
    }

    LaunchedEffect(payload.text, payload.uriStrings) {
        val text = payload.text
        inspection = if (text == null) {
            TextInspectionResult(emptyList())
        } else {
            withContext(Dispatchers.Default) { TextInspector.inspect(text) }
        }
    }

    val currentInspection = inspection
    if (currentInspection == null) {
        SharedTextInspectionProgressDialog(onDismiss = onDismiss)
        return
    }
    if (!safetyReviewed && currentInspection.hasFindings) {
        SharedTextSafetyReviewDialog(
            inspection = currentInspection,
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
                    "Choose a destination for $summary.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                onOpenMarkdown?.let {
                    Text(
                        "Local",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    TextButton(
                        onClick = it,
                        enabled = markdownEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Markdown workspace", modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
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
private fun ReplaceMarkdownDraftDialog(
    currentName: String,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace unsaved Markdown draft?") },
        text = {
            Text(
                "$currentName has edits that have not been exported. Discard them and open the shared text as a new local Markdown draft?"
            )
        },
        confirmButton = {
            Button(onClick = onDiscard) { Text("Discard and open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SharedTextInspectionProgressDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inspecting shared text") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    "Checking hidden Unicode, encoded carriers and prompt-like instructions before routing this text.",
                    style = MaterialTheme.typography.bodyMedium
                )
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
                    "LlmBench found ${inspection.detectedCount} suspicious text detection${if (inspection.detectedCount == 1) "" else "s"}. Review the retained details before this text is used locally or routed to a provider.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${if (inspection.truncated) "Retained" else "Severity"}: High ${inspection.highCount} • Medium ${inspection.mediumCount} • Low ${inspection.lowCount}",
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
            Button(onClick = onContinue) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
