package com.twojstar.llmbench.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
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

    // Map of persistent WebViews to prevent reloading when switching tabs.
    val webViewMap = remember { mutableMapOf<WebAiService, WebView>() }

    DisposableEffect(webViewMap) {
        onDispose {
            webViewMap.values.forEach { webView ->
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.destroy()
            }
            webViewMap.clear()
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
                                    .clickable {
                                        selectedService = service
                                    }
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
                                } catch (_: Exception) {
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
            // Container holding all WebViews (one per provider, stacked in a Box)
            // This maintains sessions, cookies, logins, and prompt state across tab switches without reloading!
            WebAiService.entries.forEach { service ->
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
                                initialUrl = service.url,
                                isDesktop = isDesktopMode,
                                onUrlChanged = { url ->
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
                                }
                            ).also { wv ->
                                webViewMap[service] = wv
                            }
                        },
                        update = { wv ->
                            if (isCurrentService) {
                                canGoBack = wv.canGoBack()
                                canGoForward = wv.canGoForward()
                                wv.url?.let { currentUrl = it }
                                wv.title?.let { pageTitle = it }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Quick Prompt / Profile Copier Dialog
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

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    initialUrl: String,
    isDesktop: Boolean,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit
): WebView {
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Web Settings configured for modern SPA web applications (ChatGPT, Claude, Gemini, DeepSeek, Kimi)
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
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let {
                    onUrlChanged(it)
                }
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let {
                    onUrlChanged(it)
                }
                onNavStateChanged(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val navigation = request ?: return true
                if (!navigation.isForMainFrame) return false

                val uri = navigation.url
                return when (uri.scheme?.lowercase()) {
                    "https" -> false
                    "http", "mailto", "tel", "sms" -> {
                        openExternalUri(context, uri)
                        true
                    }
                    "intent" -> {
                        openExternalIntentUri(context, uri)
                        true
                    }
                    else -> true
                }
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

private fun openExternalUri(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Unsupported or unavailable external schemes stay blocked from the WebView.
    }
}

private fun openExternalIntentUri(context: Context, uri: Uri) {
    try {
        val parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        val targetPackage = parsedIntent.`package` ?: parsedIntent.component?.packageName
        val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")

        if (!targetPackage.isNullOrBlank()) {
            parsedIntent.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                setPackage(targetPackage)
                removeExtra("browser_fallback_url")
            }
            context.startActivity(parsedIntent)
            return
        }

        val fallbackUri = fallbackUrl?.let(Uri::parse)
        if (fallbackUri != null && fallbackUri.scheme.equals("https", ignoreCase = true)) {
            openExternalUri(context, fallbackUri)
        }
    } catch (_: Exception) {
        // Invalid or unavailable intent links remain blocked.
    }
}

fun getWebServiceIcon(service: WebAiService): ImageVector {
    return when (service) {
        WebAiService.CLAUDE -> Icons.Default.Flare
        WebAiService.CHATGPT -> Icons.Default.SmartToy
        WebAiService.GEMINI -> Icons.Default.AutoAwesome
        WebAiService.DEEPSEEK -> Icons.Default.Psychology
        WebAiService.KIMI -> Icons.Default.ElectricBolt
    }
}
