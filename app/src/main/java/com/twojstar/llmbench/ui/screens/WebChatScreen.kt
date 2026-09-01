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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.ui.theme.*
import com.twojstar.llmbench.ui.viewmodel.StudioUiState
import com.twojstar.llmbench.ui.viewmodel.StudioViewModel
import com.twojstar.llmbench.web.applyProviderWebTweaks

private const val WEBVIEW_LOG_TAG = "LlmBenchWeb"
private const val MAX_LIVE_WEBVIEWS = 2

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
    val currentSelectedService by rememberUpdatedState(selectedService)

    fun activateService(service: WebAiService) {
        selectedService = service
        liveServices = nextWebViewLru(liveServices, service)
    }

    fun evictInactiveWebViews() {
        liveServices = listOf(currentSelectedService)
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> webViewMap[currentSelectedService]?.onResume()
                Lifecycle.Event.ON_STOP -> webViewMap.values.forEach(WebView::onPause)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    BackHandler(enabled = canGoBack) {
        activeWebView?.let {
            if (it.canGoBack()) {
                it.goBack()
            }
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
                ) {
                    // Top Bar: AI Provider Web Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WebAiService.entries.forEach { service ->
                            val isSelected = selectedService == service
                            val brandColor = Color(service.brandHexColor)

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) brandColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.5.dp, brandColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .clickable { activateService(service) }
                                    .testTag("tab_web_service_${service.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = getWebServiceIcon(service),
                                        contentDescription = null,
                                        tint = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = service.shortName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Native Hub Switch button
                        OutlinedButton(
                            onClick = onOpenNativeCompare,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_switch_to_native_hub")
                        ) {
                            Icon(
                                Icons.Default.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = AccentCyan
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Compare Hub",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan
                            )
                        }
                    }

                    // Navigation Bar (Back, Forward, Reload, URL & Helpers)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { activeWebView?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(32.dp).testTag("btn_web_back")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp),
                                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = { activeWebView?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(32.dp).testTag("btn_web_forward")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                modifier = Modifier.size(18.dp),
                                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = { activeWebView?.reload() },
                            modifier = Modifier.size(32.dp).testTag("btn_web_reload")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reload",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                activeWebView?.loadUrl(selectedService.url)
                            },
                            modifier = Modifier.size(32.dp).testTag("btn_web_home")
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // URL Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                    viewModel.showSnackbar("Copied address: $currentUrl")
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secure Connection",
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = currentUrl.removePrefix("https://").removePrefix("http://"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Studio Prompt Injection / Copy helper
                        IconButton(
                            onClick = { showPromptHelperDialog = true },
                            modifier = Modifier.size(32.dp).testTag("btn_prompt_quick_copy")
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = "Copy Prompt / Profile",
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Desktop mode toggle
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                activeWebView?.let { wv ->
                                    applyUserAgent(wv, isDesktopMode)
                                    wv.reload()
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("btn_toggle_desktop_mode")
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.Laptop else Icons.Default.Smartphone,
                                contentDescription = "Toggle Desktop/Mobile",
                                tint = if (isDesktopMode) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Open in external browser
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                    context.startActivity(intent)
                                } catch (error: ActivityNotFoundException) {
                                    Log.w(WEBVIEW_LOG_TAG, "External browser handler unavailable", error)
                                    viewModel.showSnackbar("Could not launch external browser")
                                } catch (error: SecurityException) {
                                    Log.w(WEBVIEW_LOG_TAG, "External browser launch rejected", error)
                                    viewModel.showSnackbar("Could not launch external browser")
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("btn_open_external")
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Open in Chrome/Browser",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Progress Bar
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { loadingProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = Color(selectedService.brandHexColor),
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
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
                            if (isCurrentService) {
                                wv.onResume()
                                canGoBack = wv.canGoBack()
                                canGoForward = wv.canGoForward()
                                wv.url?.let { currentUrl = it }
                                wv.title?.let { pageTitle = it }
                            } else {
                                wv.onPause()
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
                    view?.let { applyProviderWebTweaks(it, service, pageUrl) }
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
private fun openExternalUri(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Log.w(WEBVIEW_LOG_TAG, "External URI handler unavailable", error)
    } catch (error: SecurityException) {
        Log.w(WEBVIEW_LOG_TAG, "External URI launch rejected", error)
    }
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
