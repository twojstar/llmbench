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
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.WebChatActivityStatus
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import com.twojstar.llmbench.data.model.markWebChatActivityRead
import com.twojstar.llmbench.data.model.nextWebChatActivityStatus
import com.twojstar.llmbench.data.model.webChatActivityStatusAfterEviction
import com.twojstar.llmbench.ui.theme.*
import com.twojstar.llmbench.ui.viewmodel.StudioUiState
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import com.twojstar.llmbench.web.applyProviderWebTweaks
import com.twojstar.llmbench.web.installProviderGenerationTracker
import com.twojstar.llmbench.web.probeProviderGenerationActivity
import com.twojstar.llmbench.web.setProviderGenerationTrackerSelected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WEBVIEW_LOG_TAG = "LlmBenchWeb"
private const val MAX_LIVE_WEBVIEWS = 2

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

/** Hosts account-backed AI services with mobile-first WebView controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebChatScreen(
    viewModel: StudioViewModel,
    uiState: StudioUiState,
    onOpenNativeCompare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedService by remember { mutableStateOf(WebAiService.CLAUDE) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(selectedService.url) }
    var pageTitle by remember { mutableStateOf(selectedService.displayName) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showPromptHelperDialog by remember { mutableStateOf(false) }
    var pendingExternalIntentUri by remember { mutableStateOf<Uri?>(null) }
    val pendingFileCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback.value ?: return@rememberLauncherForActivityResult
        pendingFileCallback.value = null
        val resultData = result.data
        val selectedUris = if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, resultData)
                ?.filter { isAllowedUploadUri(context, it) }
                ?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }
        } else null
        callback.onReceiveValue(selectedUris)
    }

    var liveServices by remember { mutableStateOf(listOf(selectedService)) }
    val webViewMap = remember { mutableStateMapOf<WebAiService, WebView>() }
    val lastKnownUrls = remember { mutableStateMapOf<WebAiService, String>() }
    val activityStatuses = remember { mutableStateMapOf<WebAiService, WebChatActivityStatus>() }
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
        selectedService = service
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
            val webViews = webViewMap.values.toList()
            webViewMap.clear()
            webViews.forEach(::releaseWebView)
        }
    }

    val activeWebView = webViewMap[selectedService]

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
                onSelectService = { service ->
                    activateService(service)
                    drawerScope.launch { drawerState.close() }
                },
                onOpenNativeCompare = {
                    drawerScope.launch {
                        drawerState.close()
                        onOpenNativeCompare()
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
                    currentUrl = currentUrl,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isDesktopMode = isDesktopMode,
                    isLoading = isLoading,
                    loadingProgress = loadingProgress,
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onShowPromptHelper = { showPromptHelperDialog = true },
                    onToggleDesktopMode = {
                        val nextDesktopMode = !isDesktopMode
                        isDesktopMode = nextDesktopMode
                        webViewMap.values.forEach { webView ->
                            applyUserAgent(webView, nextDesktopMode)
                            webView.reload()
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
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isCurrentService) Modifier else Modifier.size(0.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            createConfiguredWebView(
                                context = ctx,
                                service = service,
                                initialUrl = lastKnownUrls[service] ?: service.url,
                                isDesktop = isDesktopMode,
                                isServiceSelected = { selectedService == service },
                                onUrlChanged = { url ->
                                    lastKnownUrls[service] = url
                                    if (selectedService == service) {
                                        currentUrl = url
                                    }
                                },
                                onTitleChanged = { title ->
                                    if (selectedService == service) {
                                        pageTitle = title
                                    }
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
                                onFileChooserRequested = { callback, params ->
                                    pendingFileCallback.value?.onReceiveValue(null)
                                    pendingFileCallback.value = callback
                                    try {
                                        fileChooserLauncher.launch(params.createIntent())
                                        true
                                    } catch (error: ActivityNotFoundException) {
                                        Log.w(WEBVIEW_LOG_TAG, "No file picker available", error)
                                        pendingFileCallback.value = null
                                        callback.onReceiveValue(null)
                                        viewModel.showSnackbar("No file picker available")
                                        true
                                    } catch (error: SecurityException) {
                                        Log.w(WEBVIEW_LOG_TAG, "File picker launch blocked", error)
                                        pendingFileCallback.value = null
                                        callback.onReceiveValue(null)
                                        viewModel.showSnackbar("File picker was blocked")
                                        true
                                    }
                                }
                            ).also { wv ->
                                webViewMap[service] = wv
                            }
                        },
                        update = { wv ->
                            if (isCurrentService && lifecycleStarted) {
                                wv.onResume()
                            } else {
                                wv.onPause()
                            }
                            if (isCurrentService) {
                                canGoBack = wv.canGoBack()
                                canGoForward = wv.canGoForward()
                                wv.url?.let { currentUrl = it }
                                wv.title?.let { pageTitle = it }
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
                        text = "Copy instructions or prompts to paste directly into ${selectedService.shortName}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val renderedPrompt = uiState.renderedInstructions.ifBlank { "You are an expert AI assistant configured via LlmBench." }

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Text(
                            text = renderedPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Profile Instructions", renderedPrompt))
                            viewModel.showSnackbar("Copied Studio instructions to clipboard!")
                            showPromptHelperDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Studio Instructions to Clipboard")
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
    currentUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDesktopMode: Boolean,
    isLoading: Boolean,
    loadingProgress: Int,
    onOpenDrawer: () -> Unit,
    onShowPromptHelper: () -> Unit,
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

                Icon(
                    imageVector = getWebServiceIcon(selectedService),
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(20.dp)
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
                            text = { Text(if (isDesktopMode) "Use mobile site" else "Use desktop site") },
                            leadingIcon = {
                                Icon(
                                    if (isDesktopMode) Icons.Default.Smartphone else Icons.Default.Laptop,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onToggleDesktopMode()
                                menuExpanded = false
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
    onSelectService: (WebAiService) -> Unit,
    onOpenNativeCompare: () -> Unit
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
                    onSelect = { onSelectService(service) }
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
        }
    }
}

@Composable
private fun WebProviderDrawerItem(
    service: WebAiService,
    isSelected: Boolean,
    activityStatus: WebChatActivityStatus,
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
            Icon(
                imageVector = getWebServiceIcon(service),
                contentDescription = null,
                tint = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurfaceVariant
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
@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    service: WebAiService,
    initialUrl: String,
    isDesktop: Boolean,
    isServiceSelected: () -> Boolean,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onExternalIntentRequested: (Uri) -> Unit,
    onFileChooserRequested: (
        ValueCallback<Array<Uri>>,
        WebChromeClient.FileChooserParams
    ) -> Boolean
): WebView {
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

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

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val callback = filePathCallback ?: return false
                val params = fileChooserParams ?: return false
                return onFileChooserRequested(callback, params)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let {
                    onUrlChanged(it)
                }
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { pageUrl ->
                    onUrlChanged(pageUrl)
                    view?.let { webView ->
                        applyProviderWebTweaks(webView, service, pageUrl)
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
                return handleMainFrameNavigation(context, navigation, onExternalIntentRequested)
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
    navigation: WebResourceRequest,
    onExternalIntentRequested: (Uri) -> Unit
): Boolean {
    if (!navigation.isForMainFrame) return false

    val uri = navigation.url
    return when (uri.scheme?.lowercase()) {
        "https" -> false
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
    }
}
