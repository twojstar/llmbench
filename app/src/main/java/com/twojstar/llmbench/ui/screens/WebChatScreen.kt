package com.twojstar.llmbench.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.twojstar.llmbench.data.model.BrowserAiPlatform
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.WebChatActivityStatus
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import com.twojstar.llmbench.data.model.markWebChatActivityRead
import com.twojstar.llmbench.data.model.nextWebChatActivityStatus
import com.twojstar.llmbench.data.model.webChatActivityStatusAfterEviction
import com.twojstar.llmbench.ui.theme.*
import com.twojstar.llmbench.ui.viewmodel.StudioUiState
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import com.twojstar.llmbench.web.ProviderDiagnosticsProbeResult
import com.twojstar.llmbench.web.ProviderDiagnosticsSnapshot
import com.twojstar.llmbench.web.StudioPromptApplyResult
import com.twojstar.llmbench.web.probeProviderDiagnostics
import com.twojstar.llmbench.web.providerDiagnosticsHost
import com.twojstar.llmbench.web.providerDiagnosticsPageHost
import com.twojstar.llmbench.web.providerDiagnosticsDocumentMatches
import com.twojstar.llmbench.web.providerDiagnosticsProbeSummary
import com.twojstar.llmbench.web.applyProviderWebTweaks
import com.twojstar.llmbench.web.applyStudioPromptToFocusedEditor
import com.twojstar.llmbench.web.installProviderGenerationTracker
import com.twojstar.llmbench.web.installStudioPromptTargetTracker
import com.twojstar.llmbench.web.probeProviderGenerationActivity
import com.twojstar.llmbench.web.providerUrlMatches
import com.twojstar.llmbench.web.providerGenerationTrackingSupported
import com.twojstar.llmbench.web.sanitizeProviderAcceptTypes
import com.twojstar.llmbench.web.shouldLoadHttpsInProviderWebView
import com.twojstar.llmbench.web.setProviderGenerationTrackerSelected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WEBVIEW_LOG_TAG = "LlmBenchWeb"
private const val MAX_LIVE_WEBVIEWS = 2
private const val DIAGNOSTIC_NONE_YET = "None yet"

internal fun shouldApplyWebChatObservation(
    observation: WebChatGenerationObservation,
    isLiveService: Boolean,
    isSameWebView: Boolean,
    hasCurrentWebView: Boolean
): Boolean {
    if (isLiveService && isSameWebView) return true
    val canSurviveEviction = observation == WebChatGenerationObservation.GENERATING ||
        observation == WebChatGenerationObservation.COMPLETED ||
        observation == WebChatGenerationObservation.COMPLETED_WHILE_SELECTED
    return canSurviveEviction && (isSameWebView || !hasCurrentWebView)
}

internal fun nextObservedWebChatActivityStatus(
    previous: WebChatActivityStatus,
    observation: WebChatGenerationObservation,
    isSelected: Boolean,
    isLiveService: Boolean
): WebChatActivityStatus {
    val nextStatus = nextWebChatActivityStatus(previous, observation, isSelected)
    return if (isLiveService) nextStatus else webChatActivityStatusAfterEviction(nextStatus)
}

internal fun shouldApplyPendingDesktopMode(
    observation: WebChatGenerationObservation,
    trackingSupported: Boolean,
    isStableOffProviderPage: Boolean
): Boolean = when {
    observation == WebChatGenerationObservation.GENERATING -> false
    isStableOffProviderPage -> true
    !trackingSupported -> false
    observation != WebChatGenerationObservation.UNKNOWN -> true
    else -> false
}

/** Hosts account-backed AI services with mobile-first WebView controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebChatScreen(
    viewModel: StudioViewModel,
    uiState: StudioUiState,
    onOpenNativeCompare: () -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedService by rememberUpdatedState(uiState.selectedWebService)
    var currentUrl by remember { mutableStateOf(selectedService.url) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showPromptHelperDialog by remember { mutableStateOf(false) }
    var showProviderDiagnosticsDialog by remember { mutableStateOf(false) }
    var diagnosticsProbeResult by remember { mutableStateOf<ProviderDiagnosticsProbeResult?>(null) }
    var diagnosticsProbeRequestId by remember { mutableIntStateOf(0) }
    var pendingExternalIntentUri by remember { mutableStateOf<Uri?>(null) }
    val pendingFileCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pendingFileService = remember { mutableStateOf<WebAiService?>(null) }
    val pendingFileRequestId = remember { mutableStateOf<Int?>(null) }
    var nextFileChooserRequestId by remember { mutableIntStateOf(0) }
    val fileChooserLatestRequestIds = remember { mutableStateMapOf<WebAiService, Int>() }
    val fileChooserRequestCounts = remember { mutableStateMapOf<WebAiService, Int>() }
    val fileChooserAcceptTypes = remember { mutableStateMapOf<WebAiService, String>() }
    val fileChooserModes = remember { mutableStateMapOf<WebAiService, String>() }
    val fileChooserHosts = remember { mutableStateMapOf<WebAiService, String>() }
    val fileChooserOutcomes = remember { mutableStateMapOf<WebAiService, String>() }
    val documentRevisions = remember { mutableStateMapOf<WebAiService, Int>() }

    fun recordFileChooserRequest(
        service: WebAiService,
        params: WebChromeClient.FileChooserParams,
        pageUrl: String?,
        outcome: String
    ): Int {
        val requestId = nextFileChooserRequestId + 1
        nextFileChooserRequestId = requestId
        fileChooserLatestRequestIds[service] = requestId
        fileChooserRequestCounts[service] = (fileChooserRequestCounts[service] ?: 0) + 1
        fileChooserHosts[service] = providerDiagnosticsPageHost(service, pageUrl)
        fileChooserModes[service] = if (
            params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        ) "multiple" else "single"
        fileChooserAcceptTypes[service] = sanitizeProviderAcceptTypes(params.acceptTypes)
        fileChooserOutcomes[service] = outcome
        return requestId
    }

    fun updateFileChooserOutcome(service: WebAiService, requestId: Int, outcome: String) {
        if (fileChooserLatestRequestIds[service] == requestId) {
            fileChooserOutcomes[service] = outcome
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback.value ?: return@rememberLauncherForActivityResult
        val service = pendingFileService.value
        val requestId = pendingFileRequestId.value
        pendingFileCallback.value = null
        pendingFileService.value = null
        pendingFileRequestId.value = null
        val resultData = result.data
        val selectedUris = if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, resultData)
                ?.filter { isAllowedUploadUri(context, it) }
                ?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }
        } else null
        if (service != null && requestId != null) {
            updateFileChooserOutcome(
                service,
                requestId,
                selectedUris?.let { uris -> "selected (${uris.size})" }
                    ?: "cancelled / no accepted file"
            )
        }
        callback.onReceiveValue(selectedUris)
    }

    var liveServices by remember { mutableStateOf(listOf(selectedService)) }
    val webViewMap = remember { mutableStateMapOf<WebAiService, WebView>() }
    val lastKnownUrls = remember { mutableStateMapOf<WebAiService, String>() }
    val activityStatuses = remember { mutableStateMapOf<WebAiService, WebChatActivityStatus>() }
    val desktopModes = remember { mutableStateMapOf<WebAiService, Boolean>() }
    val pendingDesktopModes = remember { mutableStateMapOf<WebAiService, Boolean>() }
    val providerFavicons = remember { mutableStateMapOf<WebAiService, Bitmap>() }
    val currentSelectedService by rememberUpdatedState(selectedService)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    fun updateLiveServices(nextServices: List<WebAiService>) {
        val evictedServices = liveServices.filterNot(nextServices.toSet()::contains)
        evictedServices.forEach { service ->
            activityStatuses[service]?.let { status ->
                activityStatuses[service] = webChatActivityStatusAfterEviction(status)
            }
        }
        liveServices = nextServices
    }

    fun applyPendingDesktopModeIfSafe(
        service: WebAiService,
        observation: WebChatGenerationObservation
    ) {
        if (service != selectedService) return
        val nextDesktopMode = pendingDesktopModes[service] ?: return
        val webView = webViewMap[service] ?: return
        val pageUrl = webView.url
        val isStableOffProviderPage = pageUrl != null &&
            !providerUrlMatches(service, pageUrl) &&
            webView.progress >= 100
        if (!shouldApplyPendingDesktopMode(
                observation = observation,
                trackingSupported = providerGenerationTrackingSupported(service),
                isStableOffProviderPage = isStableOffProviderPage
            )
        ) return
        pendingDesktopModes.remove(service)
        desktopModes[service] = nextDesktopMode
        applyUserAgent(webView, nextDesktopMode)
        webView.reload()
    }

    fun probeServiceActivity(service: WebAiService) {
        val webView = webViewMap[service] ?: return
        probeProviderGenerationActivity(webView, service) { observation ->
            val mappedWebView = webViewMap[service]
            val shouldApply = shouldApplyWebChatObservation(
                observation = observation,
                isLiveService = service in liveServices,
                isSameWebView = mappedWebView === webView,
                hasCurrentWebView = mappedWebView != null
            )
            if (!shouldApply) return@probeProviderGenerationActivity
            val previous = activityStatuses[service] ?: WebChatActivityStatus.IDLE
            activityStatuses[service] = nextObservedWebChatActivityStatus(
                previous = previous,
                observation = observation,
                isSelected = selectedService == service,
                isLiveService = service in liveServices
            )
            applyPendingDesktopModeIfSafe(service, observation)
        }
    }

    fun activateService(service: WebAiService) {
        val previousService = selectedService
        if (previousService != service) {
            webViewMap[previousService]?.let { webView ->
                setProviderGenerationTrackerSelected(webView, previousService, isSelected = false)
            }
            webViewMap[service]?.let { webView ->
                setProviderGenerationTrackerSelected(webView, service, isSelected = true)
            }
        }
        val nextWebView = webViewMap[service]
        currentUrl = nextWebView?.url ?: lastKnownUrls[service] ?: service.url
        canGoBack = nextWebView?.canGoBack() ?: false
        canGoForward = nextWebView?.canGoForward() ?: false
        loadingProgress = nextWebView?.progress ?: 0
        isLoading = nextWebView?.let { it.progress < 100 } ?: false
        viewModel.selectWebService(service)
        activityStatuses[service]?.let { status ->
            activityStatuses[service] = markWebChatActivityRead(status)
        }
        updateLiveServices(nextWebViewLru(liveServices, service))
    }

    fun evictInactiveWebViews() {
        updateLiveServices(listOf(currentSelectedService))
    }

    val appContext = context.applicationContext
    DisposableEffect(appContext) {
        val callbacks = object : ComponentCallbacks2 {
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    evictInactiveWebViews()
                }
            }

            @Suppress("DEPRECATION")
            override fun onLowMemory() = evictInactiveWebViews()
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        appContext.registerComponentCallbacks(callbacks)
        onDispose { appContext.unregisterComponentCallbacks(callbacks) }
    }

    val lifecycleStarted = rememberWebViewLifecycleStarted(
        webViewMap = webViewMap,
        selectedService = currentSelectedService
    )

    LaunchedEffect(lifecycleStarted, liveServices) {
        if (!lifecycleStarted) return@LaunchedEffect
        while (true) {
            liveServices.forEach { service ->
                probeServiceActivity(service)
            }
            delay(1_200)
        }
    }

    DisposableEffect(webViewMap) {
        onDispose {
            pendingFileCallback.value?.onReceiveValue(null)
            pendingFileCallback.value = null
            pendingFileService.value = null
            pendingFileRequestId.value = null
            val webViews = webViewMap.values.toList()
            webViewMap.clear()
            webViews.forEach(::releaseWebView)
        }
    }

    val activeWebView = webViewMap[selectedService]
    val isDesktopMode = desktopModes[selectedService] == true
    val studioPrompt = uiState.renderedInstructions.ifBlank {
        "You are an expert AI assistant configured via LlmBench."
    }

    fun copyStudioPrompt(message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Profile Instructions", studioPrompt))
        viewModel.showSnackbar(message)
    }

    fun refreshProviderDiagnostics() {
        val service = selectedService
        val webView = webViewMap[service]
        val requestedUrl = webView?.url
        val requestedDocumentRevision = documentRevisions[service] ?: 0
        val requestId = diagnosticsProbeRequestId + 1
        diagnosticsProbeRequestId = requestId
        diagnosticsProbeResult = null
        if (webView == null || requestedUrl == null) {
            diagnosticsProbeResult = ProviderDiagnosticsProbeResult.Failed
            return
        }
        probeProviderDiagnostics(webView, service) { result ->
            val stillCurrent = diagnosticsProbeRequestId == requestId &&
                selectedService == service &&
                webViewMap[service] === webView &&
                (documentRevisions[service] ?: 0) == requestedDocumentRevision &&
                providerDiagnosticsDocumentMatches(requestedUrl, webView.url)
            if (stillCurrent) diagnosticsProbeResult = result
        }
    }

    fun openProviderDiagnostics() {
        showProviderDiagnosticsDialog = true
        refreshProviderDiagnostics()
    }

    fun applyStudioPrompt() {
        val webView = activeWebView
        if (webView == null) {
            copyStudioPrompt("Provider is not ready yet; Studio instructions copied instead.")
            return
        }
        applyStudioPromptToFocusedEditor(webView, selectedService, studioPrompt) { result ->
            when (result) {
                StudioPromptApplyResult.INSERTED ->
                    viewModel.showSnackbar("Studio instructions inserted into ${selectedService.shortName}.")
                StudioPromptApplyResult.NOT_EMPTY ->
                    copyStudioPrompt("Composer already has text; Studio instructions copied instead.")
                StudioPromptApplyResult.NO_EDITOR ->
                    copyStudioPrompt("Tap the provider composer first; Studio instructions copied too.")
                StudioPromptApplyResult.REJECTED ->
                    copyStudioPrompt("The provider rejected insertion; Studio instructions copied instead.")
                StudioPromptApplyResult.OFF_PROVIDER ->
                    copyStudioPrompt("Return to the provider chat; Studio instructions copied instead.")
                StudioPromptApplyResult.FAILED ->
                    copyStudioPrompt("Could not insert Studio instructions; copied them instead.")
            }
        }
    }

    BackHandler(enabled = canGoBack && drawerState.currentValue == DrawerValue.Closed) {
        activeWebView?.let {
            if (it.canGoBack()) {
                it.goBack()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            WebProviderDrawer(
                selectedService = selectedService,
                activityStatuses = activityStatuses,
                providerFavicons = providerFavicons,
                onSelectService = { service ->
                    activateService(service)
                    drawerScope.launch { drawerState.close() }
                },
                onOpenNativeCompare = {
                    drawerScope.launch {
                        drawerState.close()
                        onOpenNativeCompare()
                    }
                },
                onOpenStudio = {
                    drawerScope.launch {
                        drawerState.close()
                        onOpenStudio()
                    }
                },
                onOpenBrowserPlatform = { platform ->
                    drawerScope.launch {
                        drawerState.close()
                        if (!openExternalUri(context, Uri.parse(platform.url))) {
                            viewModel.showSnackbar("Could not launch ${platform.shortName}")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                WebChatToolbar(
                    activeWebView = activeWebView,
                    selectedService = selectedService,
                    activityStatus = activityStatuses[selectedService] ?: WebChatActivityStatus.IDLE,
                    providerFavicon = providerFavicons[selectedService],
                    currentUrl = currentUrl,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isDesktopMode = isDesktopMode,
                    isLoading = isLoading,
                    loadingProgress = loadingProgress,
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onApplyStudio = ::applyStudioPrompt,
                    onShowPromptHelper = { showPromptHelperDialog = true },
                    onShowDiagnostics = ::openProviderDiagnostics,
                    onToggleDesktopMode = {
                        val service = selectedService
                        val currentMode = pendingDesktopModes[service] ?: isDesktopMode
                        val nextDesktopMode = !currentMode
                        if (webViewMap[service] == null) {
                            pendingDesktopModes.remove(service)
                            desktopModes[service] = nextDesktopMode
                        } else {
                            pendingDesktopModes[service] = nextDesktopMode
                            probeServiceActivity(service)
                        }
                    },
                    onShowSnackbar = viewModel::showSnackbar
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            // Keep only a tiny MRU set of provider WebViews alive. Cookies and storage remain provider-owned.
            liveServices.forEach { service ->
                key(service) {
                    val isCurrentService = selectedService == service

                    Box(
                        modifier = if (isCurrentService) {
                            Modifier.fillMaxSize()
                        } else {
                            // Do not chain size(0.dp) after fillMaxSize(): the exact full-size
                            // constraints win, leaving the inactive WebView visible on top.
                            Modifier.size(0.dp)
                        }
                    ) {
                        AndroidView(
                        factory = { ctx ->
                            val pendingDesktopMode = pendingDesktopModes[service]
                            val initialDesktopMode = pendingDesktopMode ?: (desktopModes[service] == true)
                            createConfiguredWebView(
                                context = ctx,
                                service = service,
                                initialUrl = lastKnownUrls[service] ?: service.url,
                                isDesktop = initialDesktopMode,
                                isServiceSelected = { selectedService == service },
                                onDocumentStarted = {
                                    documentRevisions[service] = (documentRevisions[service] ?: 0) + 1
                                    if (selectedService == service && showProviderDiagnosticsDialog) {
                                        diagnosticsProbeResult = ProviderDiagnosticsProbeResult.Failed
                                    }
                                },
                                onUrlChanged = { url ->
                                    lastKnownUrls[service] = url
                                    if (selectedService == service) {
                                        currentUrl = url
                                    }
                                },
                                onTitleChanged = {},
                                onFaviconChanged = { favicon ->
                                    providerFavicons[service] = favicon
                                },
                                onProgressChanged = { progress ->
                                    if (selectedService == service) {
                                        loadingProgress = progress
                                        isLoading = progress < 100
                                    }
                                },
                                onNavStateChanged = { back, fwd ->
                                    if (selectedService == service) {
                                        canGoBack = back
                                        canGoForward = fwd
                                    }
                                },
                                onExternalIntentRequested = { uri ->
                                    pendingExternalIntentUri = uri
                                },
                                onFileChooserRequested = { callback, params, pageUrl ->
                                    if (pendingFileCallback.value != null) {
                                        recordFileChooserRequest(
                                            service,
                                            params,
                                            pageUrl,
                                            "rejected: picker already active"
                                        )
                                        callback.onReceiveValue(null)
                                        true
                                    } else {
                                        val requestId = recordFileChooserRequest(
                                            service,
                                            params,
                                            pageUrl,
                                            "picker launched"
                                        )
                                        pendingFileCallback.value = callback
                                        pendingFileService.value = service
                                        pendingFileRequestId.value = requestId
                                        val launchError = runCatching {
                                            fileChooserLauncher.launch(params.createIntent())
                                        }.exceptionOrNull()
                                        if (launchError == null) {
                                            true
                                        } else {
                                            val outcome = when (launchError) {
                                                is ActivityNotFoundException -> {
                                                    Log.w(WEBVIEW_LOG_TAG, "No file picker available", launchError)
                                                    viewModel.showSnackbar("No file picker available")
                                                    "no picker available"
                                                }
                                                is SecurityException -> {
                                                    Log.w(WEBVIEW_LOG_TAG, "File picker launch blocked", launchError)
                                                    viewModel.showSnackbar("File picker was blocked")
                                                    "picker blocked"
                                                }
                                                is IllegalStateException -> {
                                                    Log.w(WEBVIEW_LOG_TAG, "File picker already active", launchError)
                                                    "picker already active"
                                                }
                                                else -> {
                                                    pendingFileCallback.value = null
                                                    pendingFileService.value = null
                                                    pendingFileRequestId.value = null
                                                    callback.onReceiveValue(null)
                                                    throw launchError
                                                }
                                            }
                                            pendingFileCallback.value = null
                                            pendingFileService.value = null
                                            pendingFileRequestId.value = null
                                            updateFileChooserOutcome(service, requestId, outcome)
                                            callback.onReceiveValue(null)
                                            true
                                        }
                                    }
                                }
                            ).also { wv ->
                                wv.visibility = providerWebViewVisibility(isCurrentService)
                                webViewMap[service] = wv
                                if (pendingDesktopMode != null &&
                                    pendingDesktopModes[service] == pendingDesktopMode
                                ) {
                                    desktopModes[service] = pendingDesktopMode
                                    pendingDesktopModes.remove(service)
                                }
                            }
                        },
                        update = { wv ->
                            wv.visibility = providerWebViewVisibility(isCurrentService)
                            if (isCurrentService && lifecycleStarted) {
                                wv.onResume()
                            } else {
                                wv.onPause()
                            }
                            if (isCurrentService) {
                                canGoBack = wv.canGoBack()
                                canGoForward = wv.canGoForward()
                                wv.url?.let { currentUrl = it }
                            }
                        },
                        onRelease = { wv ->
                            if (webViewMap.remove(service) === wv) {
                                releaseWebView(wv)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    }
                }
            }
        }
        }
    }

    // Quick Prompt / Profile Copier Dialog
    ExternalIntentConfirmationDialog(
        uri = pendingExternalIntentUri,
        onDismiss = { pendingExternalIntentUri = null },
        onConfirm = { pendingUri ->
            pendingExternalIntentUri = null
            openExternalIntentUri(context, pendingUri)
        }
    )

    if (showProviderDiagnosticsDialog) {
        val webViewPackage = WebView.getCurrentWebViewPackage()?.let { packageInfo ->
            listOfNotNull(packageInfo.packageName, packageInfo.versionName).joinToString(" ")
        } ?: "Unavailable"
        ProviderDiagnosticsDialog(
            service = selectedService,
            host = providerDiagnosticsHost(currentUrl) ?: "Unavailable",
            providerOwned = providerUrlMatches(selectedService, currentUrl),
            webViewPackage = webViewPackage,
            isDesktopMode = isDesktopMode,
            activityTrackingSupported = providerGenerationTrackingSupported(selectedService),
            activityStatus = activityStatuses[selectedService] ?: WebChatActivityStatus.IDLE,
            fileChooserRequests = fileChooserRequestCounts[selectedService] ?: 0,
            fileChooserMode = fileChooserModes[selectedService],
            fileChooserHost = fileChooserHosts[selectedService],
            fileChooserAcceptTypes = fileChooserAcceptTypes[selectedService],
            fileChooserOutcome = fileChooserOutcomes[selectedService],
            probeResult = diagnosticsProbeResult,
            onRefresh = ::refreshProviderDiagnostics,
            onDismiss = { showProviderDiagnosticsDialog = false },
            onCopyReport = { report ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LlmBench provider diagnostics", report))
                viewModel.showSnackbar("Copied privacy-safe provider diagnostics.")
            }
        )
    }

    if (showPromptHelperDialog) {
        AlertDialog(
            onDismissRequest = { showPromptHelperDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AccentCyan)
                    Text("Quick Prompt & Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tap ${selectedService.shortName}'s composer, then apply the current Studio profile. LlmBench only inserts into an empty focused editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Text(
                            text = studioPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            applyStudioPrompt()
                            showPromptHelperDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply Studio to focused composer")
                    }

                    OutlinedButton(
                        onClick = {
                            copyStudioPrompt("Copied Studio instructions to clipboard.")
                            showPromptHelperDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Studio Instructions")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromptHelperDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ProviderDiagnosticsDialog(
    service: WebAiService,
    host: String,
    providerOwned: Boolean,
    webViewPackage: String,
    isDesktopMode: Boolean,
    activityTrackingSupported: Boolean,
    activityStatus: WebChatActivityStatus,
    fileChooserRequests: Int,
    fileChooserMode: String?,
    fileChooserHost: String?,
    fileChooserAcceptTypes: String?,
    fileChooserOutcome: String?,
    probeResult: ProviderDiagnosticsProbeResult?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onCopyReport: (String) -> Unit
) {
    val probeSummary = providerDiagnosticsProbeSummary(probeResult)
    val snapshot = ProviderDiagnosticsSnapshot(
        providerName = service.shortName,
        host = host,
        providerOwned = providerOwned,
        webViewPackage = webViewPackage,
        siteMode = if (isDesktopMode) "desktop" else "mobile",
        activityTracking = if (activityTrackingSupported) "verified" else "not verified",
        activityState = activityStatus.name.lowercase(),
        fileChooserRequests = fileChooserRequests,
        fileChooserMode = fileChooserMode ?: "none",
        fileChooserHost = fileChooserHost ?: "none",
        fileChooserAcceptTypes = fileChooserAcceptTypes ?: "none",
        fileChooserOutcome = fileChooserOutcome ?: "none",
        domProbeSummary = probeSummary
    )
    val safeReport = snapshot.safeReport()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = AccentCyan)
                Text("Provider diagnostics", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Privacy-safe diagnostics only: no page text, full URLs, cookies, tokens, form values or file names are collected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                DiagnosticsLine("Provider", service.shortName)
                DiagnosticsLine("Host", host)
                DiagnosticsLine("Provider-owned page", if (providerOwned) "Yes" else "No")
                DiagnosticsLine("WebView", webViewPackage)
                DiagnosticsLine("Site mode", if (isDesktopMode) "Desktop" else "Mobile")
                DiagnosticsLine(
                    "Activity tracking",
                    if (activityTrackingSupported) "Verified" else "Not verified"
                )
                DiagnosticsLine("Current activity", activityStatus.name.lowercase())
                HorizontalDivider()
                DiagnosticsLine("File chooser requests", fileChooserRequests.toString())
                DiagnosticsLine("Picker mode", fileChooserMode ?: DIAGNOSTIC_NONE_YET)
                DiagnosticsLine("Last picker host", fileChooserHost ?: DIAGNOSTIC_NONE_YET)
                DiagnosticsLine("Accept types", fileChooserAcceptTypes ?: DIAGNOSTIC_NONE_YET)
                DiagnosticsLine("Last picker outcome", fileChooserOutcome ?: DIAGNOSTIC_NONE_YET)
                HorizontalDivider()
                DiagnosticsLine("DOM capability probe", probeSummary)
                Text(
                    "DOM counts are capability hints for verification, not proof that sign-in, upload or generation tracking works end to end.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onCopyReport(safeReport) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun DiagnosticsLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun rememberWebViewLifecycleStarted(
    webViewMap: Map<WebAiService, WebView>,
    selectedService: WebAiService
): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSelectedService by rememberUpdatedState(selectedService)
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleStarted = true
                    webViewMap[currentSelectedService]?.onResume()
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleStarted = false
                    webViewMap.values.forEach(WebView::onPause)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return lifecycleStarted
}

@Composable
private fun WebChatToolbar(
    activeWebView: WebView?,
    selectedService: WebAiService,
    activityStatus: WebChatActivityStatus,
    providerFavicon: Bitmap?,
    currentUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDesktopMode: Boolean,
    isLoading: Boolean,
    loadingProgress: Int,
    onOpenDrawer: () -> Unit,
    onApplyStudio: () -> Unit,
    onShowPromptHelper: () -> Unit,
    onShowDiagnostics: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val brandColor = Color(selectedService.brandHexColor)
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_web_provider_drawer")
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Switch AI service")
                }

                WebProviderIdentityIcon(
                    service = selectedService,
                    favicon = providerFavicon,
                    fallbackTint = brandColor,
                    size = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedService.shortName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                WebProviderActivityIndicator(selectedService, activityStatus, brandColor)
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onApplyStudio,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_web_apply_studio")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Apply Studio profile")
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_web_more")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Web chat options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Back") },
                            leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                            enabled = canGoBack,
                            onClick = {
                                activeWebView?.goBack()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                            enabled = canGoForward,
                            onClick = {
                                activeWebView?.goForward()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reload") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                activeWebView?.reload()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Provider home") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            onClick = {
                                activeWebView?.loadUrl(selectedService.url)
                                menuExpanded = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Copy address") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                onShowSnackbar("Copied address")
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quick prompt / profile") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShowPromptHelper()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Provider diagnostics") },
                            leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShowDiagnostics()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isDesktopMode) "Use mobile site" else "Use desktop site") },
                            leadingIcon = {
                                Icon(
                                    if (isDesktopMode) Icons.Default.Smartphone else Icons.Default.Laptop,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (providerGenerationTrackingSupported(selectedService)) {
                                    onToggleDesktopMode()
                                } else {
                                    onShowSnackbar(
                                        "Display mode switching is disabled until ${selectedService.shortName} activity tracking is verified."
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                            onClick = {
                                if (!openExternalUri(context, Uri.parse(currentUrl))) {
                                    onShowSnackbar("Could not launch external browser")
                                }
                                menuExpanded = false
                            }
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = brandColor,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun WebProviderDrawer(
    selectedService: WebAiService,
    activityStatuses: Map<WebAiService, WebChatActivityStatus>,
    providerFavicons: Map<WebAiService, Bitmap>,
    onSelectService: (WebAiService) -> Unit,
    onOpenNativeCompare: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenBrowserPlatform: (BrowserAiPlatform) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(292.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
            )

            WebAiService.entries.forEach { service ->
                WebProviderDrawerItem(
                    service = service,
                    isSelected = selectedService == service,
                    activityStatus = activityStatuses[service] ?: WebChatActivityStatus.IDLE,
                    favicon = providerFavicons[service],
                    onSelect = { onSelectService(service) }
                )
            }

            Text(
                text = "Platforms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
            )
            BrowserAiPlatform.entries.forEach { platform ->
                NavigationDrawerItem(
                    label = { Text(platform.shortName) },
                    selected = false,
                    onClick = { onOpenBrowserPlatform(platform) },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    badge = { Text("Browser") },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("btn_open_${platform.id}")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Compare Hub") },
                selected = false,
                onClick = onOpenNativeCompare,
                icon = {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = AccentCyan
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("btn_switch_to_native_hub")
            )
            NavigationDrawerItem(
                label = { Text("Studio") },
                selected = false,
                onClick = onOpenStudio,
                icon = {
                    Icon(Icons.Default.Tune, contentDescription = null)
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("btn_switch_to_studio")
            )
        }
    }
}

@Composable
private fun WebProviderDrawerItem(
    service: WebAiService,
    isSelected: Boolean,
    activityStatus: WebChatActivityStatus,
    favicon: Bitmap?,
    onSelect: () -> Unit
) {
    val brandColor = Color(service.brandHexColor)
    NavigationDrawerItem(
        label = {
            Text(
                text = service.shortName,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        selected = isSelected,
        onClick = onSelect,
        icon = {
            WebProviderIdentityIcon(
                service = service,
                favicon = favicon,
                fallbackTint = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 24.dp
            )
        },
        badge = {
            WebProviderActivityIndicator(service, activityStatus, brandColor)
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = brandColor.copy(alpha = 0.12f),
            selectedIconColor = brandColor
        ),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .semantics {
                when (activityStatus) {
                    WebChatActivityStatus.GENERATING -> stateDescription = "Generating response"
                    WebChatActivityStatus.UNREAD -> stateDescription = "Unread response"
                    WebChatActivityStatus.PENDING ->
                        stateDescription = "Response status pending after tab eviction"
                    WebChatActivityStatus.IDLE -> Unit
                }
            }
            .testTag("tab_web_service_${service.id}")
    )
}

@Composable
private fun WebProviderIdentityIcon(
    service: WebAiService,
    favicon: Bitmap?,
    fallbackTint: Color,
    size: androidx.compose.ui.unit.Dp
) {
    if (favicon != null) {
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(5.dp))
        )
    } else {
        Icon(
            imageVector = getWebServiceIcon(service),
            contentDescription = null,
            tint = fallbackTint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun WebProviderActivityIndicator(
    service: WebAiService,
    activityStatus: WebChatActivityStatus,
    brandColor: Color
) {
    when (activityStatus) {
        WebChatActivityStatus.GENERATING -> CircularProgressIndicator(
            modifier = Modifier
                .size(10.dp)
                .testTag("status_web_service_${service.id}_generating"),
            strokeWidth = 1.5.dp,
            color = brandColor
        )
        WebChatActivityStatus.UNREAD -> Box(
            modifier = Modifier
                .size(8.dp)
                .background(brandColor, CircleShape)
                .testTag("status_web_service_${service.id}_unread")
        )
        WebChatActivityStatus.PENDING -> Box(
            modifier = Modifier
                .size(8.dp)
                .border(1.dp, brandColor, CircleShape)
                .testTag("status_web_service_${service.id}_pending")
        )
        WebChatActivityStatus.IDLE -> Unit
    }
}

/** Requires explicit user consent before an intent URI leaves LlmBench. */
@Composable
private fun ExternalIntentConfirmationDialog(
    uri: Uri?,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    val pendingUri = uri ?: return
    val targetPackage = runCatching {
        Intent.parseUri(pendingUri.toString(), Intent.URI_INTENT_SCHEME).`package`
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open another app?") },
        text = {
            Text(
                targetPackage?.let { "This login wants to open $it." }
                    ?: "This login wants to leave LlmBench and open another app."
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pendingUri) }) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay here") }
        }
    )
}

internal fun providerWebViewVisibility(isCurrentService: Boolean): Int =
    if (isCurrentService) android.view.View.VISIBLE else android.view.View.GONE

internal fun nextWebViewLru(
    current: List<WebAiService>,
    selected: WebAiService
): List<WebAiService> = buildList {
    add(selected)
    current.filterTo(this) { it != selected }
}.take(MAX_LIVE_WEBVIEWS)

private fun releaseWebView(webView: WebView) {
    webView.onPause()
    webView.stopLoading()
    webView.webChromeClient = null
    webView.webViewClient = WebViewClient()
    webView.destroy()
}

/** Builds the least-privileged WebView needed by account-backed providers. */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createConfiguredWebView(
    context: Context,
    service: WebAiService,
    initialUrl: String,
    isDesktop: Boolean,
    isServiceSelected: () -> Boolean,
    onDocumentStarted: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onFaviconChanged: (Bitmap) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onExternalIntentRequested: (Uri) -> Unit,
    onFileChooserRequested: (
        ValueCallback<Array<Uri>>,
        WebChromeClient.FileChooserParams,
        pageUrl: String?
    ) -> Boolean
): WebView {
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.requestFocusFromTouch()
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // Web Settings configured for modern SPA web applications (ChatGPT, Claude, Gemini, DeepSeek, Kimi, Vibe)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess = false
            allowFileAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
        }

        // Enable Cookies and 3rd-party cookies for OAuth logins (Google, Apple, Microsoft, Auth0)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        applyUserAgent(this, isDesktop)

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { onTitleChanged(it) }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                val pageUrl = view?.url ?: return
                if (icon != null && providerUrlMatches(service, pageUrl)) {
                    onFaviconChanged(icon)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val callback = filePathCallback ?: return false
                val params = fileChooserParams ?: return false
                return onFileChooserRequested(callback, params, webView?.url)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onDocumentStarted()
                url?.let { pageUrl ->
                    onUrlChanged(pageUrl)
                    if (favicon != null && providerUrlMatches(service, pageUrl)) {
                        onFaviconChanged(favicon)
                    }
                }
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { pageUrl ->
                    onUrlChanged(pageUrl)
                    view?.let { webView ->
                        applyProviderWebTweaks(webView, service, pageUrl)
                        installStudioPromptTargetTracker(webView, service, pageUrl)
                        installProviderGenerationTracker(
                            webView,
                            service,
                            isSelected = isServiceSelected()
                        )
                    }
                }
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val navigation = request ?: return true
                return handleMainFrameNavigation(context, service, navigation, onExternalIntentRequested)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
            }
        }

        loadUrl(initialUrl)
    }

    return webView
}

/** Accepts only externally granted content URIs for provider uploads. */
private fun isAllowedUploadUri(context: Context, uri: Uri): Boolean {
    if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return false
    val authority = uri.authority ?: return false
    val appAuthorityPrefix = context.packageName.lowercase()
    val normalizedAuthority = authority.lowercase()
    if (normalizedAuthority == appAuthorityPrefix || normalizedAuthority.startsWith("$appAuthorityPrefix.")) return false
    return true
}

/** Routes a top-level WebView navigation without granting implicit app-launch authority. */
private fun handleMainFrameNavigation(
    context: Context,
    service: WebAiService,
    navigation: WebResourceRequest,
    onExternalIntentRequested: (Uri) -> Unit
): Boolean {
    if (!navigation.isForMainFrame) return false

    val uri = navigation.url
    return when (uri.scheme?.lowercase()) {
        "https" -> {
            if (shouldLoadHttpsInProviderWebView(service, uri.toString())) return false
            if (navigation.hasGesture()) {
                openExternalUri(context, uri)
            }
            true
        }
        "http", "mailto", "tel", "sms" -> {
            if (navigation.hasGesture()) {
                openExternalUri(context, uri)
            }
            true
        }
        "intent" -> {
            if (navigation.hasGesture()) {
                onExternalIntentRequested(uri)
            }
            true
        }
        else -> true
    }
}

/** Switches between the installed WebView mobile UA and a desktop-shaped variant. */
private fun applyUserAgent(webView: WebView, isDesktop: Boolean) {
    val defaultUserAgent = WebSettings.getDefaultUserAgent(webView.context)
    webView.settings.userAgentString = if (isDesktop) {
        defaultUserAgent
            .replace(Regex("\\([^)]*Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Version/4.0", "")
            .replace(Regex("\\s+Mobile(?=\\s|$)"), "")
    } else {
        defaultUserAgent
    }
}

/** Delegates an explicitly allowed external URI to a browsable system handler. */
private fun openExternalUri(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val error = runCatching { context.startActivity(intent) }.exceptionOrNull() ?: return true
    when (error) {
        is ActivityNotFoundException -> Log.w(WEBVIEW_LOG_TAG, "External URI handler unavailable", error)
        is SecurityException -> Log.w(WEBVIEW_LOG_TAG, "External URI launch rejected", error)
        else -> throw error
    }
    return false
}

/** Launches a user-confirmed intent URI or falls back to validated HTTPS. */
private fun openExternalIntentUri(context: Context, uri: Uri) {
    val parsedIntent = runCatching {
        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
    }.getOrNull() ?: return
    val fallbackUri = validatedHttpsFallback(parsedIntent)
    val targetPackage = parsedIntent.`package` ?: parsedIntent.component?.packageName

    if (!targetPackage.isNullOrBlank()) {
        val launchIntent = sanitizeExternalIntent(parsedIntent, targetPackage)
        if (launchIntent != null) try {
            context.startActivity(launchIntent)
            return
        } catch (error: ActivityNotFoundException) {
            Log.w(WEBVIEW_LOG_TAG, "Intent target unavailable; trying HTTPS fallback", error)
        } catch (error: SecurityException) {
            Log.w(WEBVIEW_LOG_TAG, "Intent target rejected; trying HTTPS fallback", error)
        }
    }

    fallbackUri?.let { openExternalUri(context, it) }
}

/** Reduces an intent URI to a safe browsable ACTION_VIEW handoff. */
private fun sanitizeExternalIntent(intent: Intent, targetPackage: String): Intent? {
    val data = intent.data ?: return null
    val scheme = data.scheme?.lowercase() ?: return null
    if (scheme in setOf("file", "content", "android.resource", "javascript", "data")) return null

    return Intent(Intent.ACTION_VIEW, data).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        setPackage(targetPackage)
    }
}

/** Returns an intent browser fallback only when it is HTTPS. */
private fun validatedHttpsFallback(intent: Intent): Uri? {
    val fallbackUri = intent.getStringExtra("browser_fallback_url")?.let(Uri::parse) ?: return null
    return fallbackUri.takeIf { it.scheme.equals("https", ignoreCase = true) }
}

/** Maps a provider to its lightweight toolbar icon. */
fun getWebServiceIcon(service: WebAiService): ImageVector {
    return when (service) {
        WebAiService.CLAUDE -> Icons.Default.Flare
        WebAiService.CHATGPT -> Icons.Default.SmartToy
        WebAiService.GEMINI -> Icons.Default.AutoAwesome
        WebAiService.DEEPSEEK -> Icons.Default.Psychology
        WebAiService.KIMI -> Icons.Default.ElectricBolt
        WebAiService.VIBE -> Icons.Default.Air
        WebAiService.QWEN -> Icons.Default.Hub
        WebAiService.COPILOT -> Icons.Default.AutoAwesome
        WebAiService.ZAI -> Icons.Default.Memory
        WebAiService.GROK -> Icons.Default.Public
        WebAiService.CHARACTER_AI -> Icons.Default.Groups
        WebAiService.VENICE -> Icons.Default.Lock
        WebAiService.META_AI -> Icons.Default.AllInclusive
    }
}
