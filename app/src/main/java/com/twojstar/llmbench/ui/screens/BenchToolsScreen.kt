package com.twojstar.llmbench.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.twojstar.llmbench.data.codebench.CodebenchBarcodeFormat
import com.twojstar.llmbench.data.codebench.CodebenchBarcodeGenerateAction
import com.twojstar.llmbench.data.codebench.CodebenchBarcodeGenerateActionResult
import com.twojstar.llmbench.data.codebench.CodebenchBarcodeMatrix
import com.twojstar.llmbench.data.codebench.CodebenchBarcodeCodec
import com.twojstar.llmbench.data.codebench.CodebenchImageSampling
import com.twojstar.llmbench.data.codebench.CodebenchImportedBarcodeDecodeAction
import com.twojstar.llmbench.data.codebench.CodebenchImportedBarcodeDecodeActionResult
import com.twojstar.llmbench.data.document.TextDocumentFileAccess
import com.twojstar.llmbench.data.model.BenchToolNetworkBehavior
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.capabilities
import com.twojstar.llmbench.data.preferences.BuiltInBenchPreferencesStore
import com.twojstar.llmbench.data.preferences.StreambenchStationPreferencesStore
import com.twojstar.llmbench.data.streambench.StreambenchImportedPlaylistActionResult
import com.twojstar.llmbench.data.streambench.StreambenchPlaybackService
import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import com.twojstar.llmbench.data.streambench.executeStreambenchPlaylistImportAction
import com.twojstar.llmbench.data.streambench.launchStreambenchPlayback
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val STREAMBENCH_PLAYLIST_MIME_TYPES = arrayOf(
    "application/vnd.apple.mpegurl",
    "audio/mpegurl",
    "audio/x-mpegurl",
    "text/plain",
    "application/octet-stream"
)

private data class StreambenchImportUiResult(
    val entries: List<StreambenchPlaylistEntry>,
    val message: String
)

private data class CodebenchImportUiResult(
    val message: String,
    val decodedText: String? = null,
    val decodedFormat: CodebenchBarcodeFormat? = null
)

private suspend fun importCodebenchBarcode(
    context: Context,
    uri: Uri,
    isEnabled: () -> Boolean
): CodebenchImportUiResult = runCatching {
    if (!isEnabled()) {
        return@runCatching CodebenchImportUiResult(CODEBENCH_POLICY_BLOCKED_MESSAGE)
    }
    val result = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected image." }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "The selected file is not a readable image."
            }

            val sampledOptions = BitmapFactory.Options().apply {
                inSampleSize = CodebenchImageSampling.sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri).use { sampledInput ->
                requireNotNull(sampledInput) { "Could not open the selected image." }
                val sampled = requireNotNull(
                    BitmapFactory.decodeStream(sampledInput, null, sampledOptions)
                ) { "The selected file is not a readable image." }
                try {
                    if (!isEnabled()) return@withContext null
                    val bounded = if (
                        sampled.width > CodebenchBarcodeCodec.MAX_RENDER_DIMENSION ||
                        sampled.height > CodebenchBarcodeCodec.MAX_RENDER_DIMENSION
                    ) {
                        val (targetWidth, targetHeight) = CodebenchImageSampling.boundedDimensions(
                            sampled.width,
                            sampled.height
                        )
                        Bitmap.createScaledBitmap(
                            sampled,
                            targetWidth,
                            targetHeight,
                            true
                        )
                    } else {
                        sampled
                    }
                    try {
                        if (!isEnabled()) return@withContext null
                        val pixels = IntArray(bounded.width * bounded.height)
                        bounded.getPixels(
                            pixels,
                            0,
                            bounded.width,
                            0,
                            0,
                            bounded.width,
                            bounded.height
                        )
                        CodebenchImportedBarcodeDecodeAction.execute(
                            width = bounded.width,
                            height = bounded.height,
                            pixels = pixels,
                            surface = BenchToolSurface.COMPANION_UI,
                            isEnabled = isEnabled(),
                            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
                        )
                    } finally {
                        if (bounded !== sampled) bounded.recycle()
                    }
                } finally {
                    sampled.recycle()
                }
            }
        }
    }
    when (result) {
        null -> CodebenchImportUiResult(CODEBENCH_POLICY_BLOCKED_MESSAGE)
        is CodebenchImportedBarcodeDecodeActionResult.Completed -> CodebenchImportUiResult(
            message = "Decoded ${result.barcode.format.displayLabel()}.",
            decodedText = result.barcode.text,
            decodedFormat = result.barcode.format
        )
        CodebenchImportedBarcodeDecodeActionResult.NotFound -> CodebenchImportUiResult(
            "No supported QR code or barcode was found."
        )
        is CodebenchImportedBarcodeDecodeActionResult.Blocked -> CodebenchImportUiResult(
            CODEBENCH_POLICY_BLOCKED_MESSAGE
        )
        is CodebenchImportedBarcodeDecodeActionResult.Rejected -> CodebenchImportUiResult(
            "Could not decode this image."
        )
    }
}.getOrElse { error ->
    if (error is CancellationException) throw error
    if (error !is Exception) throw error
    CodebenchImportUiResult(
        when (error) {
            is SecurityException -> "LlmBench could not access the selected image."
            is IOException -> error.message ?: "Could not read the selected image."
            else -> "Could not decode this image."
        }
    )
}

internal fun updatedBenchSelection(
    current: Set<BuiltInBenchTool>,
    tool: BuiltInBenchTool,
    enabled: Boolean
): Set<BuiltInBenchTool> = current.toMutableSet().apply {
    if (enabled) add(tool) else remove(tool)
}.toSet()

private suspend fun importStreambenchPlaylist(
    context: Context,
    uri: Uri,
    isEnabled: Boolean
): StreambenchImportUiResult = runCatching {
    val opened = TextDocumentFileAccess.import(
        context = context,
        uri = uri,
        fallbackName = "playlist.m3u"
    )
    when (
        val result = opened.executeStreambenchPlaylistImportAction(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = isEnabled,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )
    ) {
        is StreambenchImportedPlaylistActionResult.Completed -> {
            val suffix = if (result.entries.size == 1) "station" else "stations"
            StreambenchImportUiResult(result.entries, "Loaded ${result.entries.size} $suffix locally.")
        }
        is StreambenchImportedPlaylistActionResult.Blocked -> StreambenchImportUiResult(
            emptyList(),
            "Playlist import is blocked by the current Bench policy."
        )
        is StreambenchImportedPlaylistActionResult.Rejected -> StreambenchImportUiResult(
            emptyList(),
            "Could not import this playlist. Check its format and size."
        )
    }
}.getOrElse { error ->
    if (error is CancellationException) throw error
    StreambenchImportUiResult(
        entries = emptyList(),
        message = when (error) {
            is SecurityException -> "LlmBench could not access the selected playlist."
            is IOException -> error.message ?: "Could not read the selected playlist."
            else -> "Could not import the selected playlist."
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { BuiltInBenchPreferencesStore(context.applicationContext) }
    val stationStore = remember(context) {
        StreambenchStationPreferencesStore(context.applicationContext)
    }
    var enabledTools by remember(store) { mutableStateOf(store.loadEnabledTools()) }
    var favoriteStationKeys by remember(stationStore) { mutableStateOf(stationStore.loadFavoriteKeys()) }
    var recentStationKeys by remember(stationStore) { mutableStateOf(stationStore.loadRecentKeys()) }
    var streambenchEntries by remember { mutableStateOf<List<StreambenchPlaylistEntry>>(emptyList()) }
    var streambenchImportMessage by remember { mutableStateOf<String?>(null) }
    var streambenchPlaybackMessage by remember { mutableStateOf<String?>(null) }
    var streambenchImporting by remember { mutableStateOf(false) }
    var codebenchText by remember { mutableStateOf("") }
    var codebenchFormat by remember { mutableStateOf(CodebenchBarcodeFormat.QR_CODE) }
    var codebenchFormatMenuExpanded by remember { mutableStateOf(false) }
    var codebenchMatrix by remember { mutableStateOf<CodebenchBarcodeMatrix?>(null) }
    var codebenchGenerationMessage by remember { mutableStateOf<String?>(null) }
    var codebenchImportMessage by remember { mutableStateOf<CodebenchImportUiResult?>(null) }
    var codebenchImporting by remember { mutableStateOf(false) }

    val streambenchImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                streambenchImporting = true
                val imported = importStreambenchPlaylist(
                    context = context,
                    uri = selectedUri,
                    isEnabled = BuiltInBenchTool.STREAMBENCH_PLAYER in enabledTools
                )
                if (BuiltInBenchTool.STREAMBENCH_PLAYER in store.loadEnabledTools()) {
                    streambenchEntries = imported.entries
                    streambenchImportMessage = imported.message
                    streambenchPlaybackMessage = null
                } else {
                    streambenchEntries = emptyList()
                    streambenchImportMessage = null
                    streambenchPlaybackMessage = null
                }
                streambenchImporting = false
            }
        }
    }

    val codebenchImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                codebenchImporting = true
                val imported = importCodebenchBarcode(
                    context = context,
                    uri = selectedUri,
                    isEnabled = {
                        BuiltInBenchTool.CODEBENCH_QR_BARCODE in store.loadEnabledTools()
                    }
                )
                codebenchImportMessage = if (
                    BuiltInBenchTool.CODEBENCH_QR_BARCODE in store.loadEnabledTools()
                ) imported else null
                codebenchImporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Built-in Benches", fontWeight = FontWeight.Bold)
                        Text(
                            "Enable only the first-party tools you want available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    "Enabling a Bench does not grant file, camera, export or network access. Each action still checks its own policy before running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(BuiltInBenchTool.entries, key = { it.id }) { tool ->
                val capabilities = tool.capabilities()
                val enabled = tool in enabledTools
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bench_tool_${tool.id}")
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    capabilities.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    capabilities.networkBehavior.displayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { shouldEnable ->
                                    val updated = updatedBenchSelection(enabledTools, tool, shouldEnable)
                                    enabledTools = updated
                                    store.saveEnabledTools(updated)
                                    if (tool == BuiltInBenchTool.STREAMBENCH_PLAYER && !shouldEnable) {
                                        StreambenchPlaybackService.stop(context)
                                        streambenchEntries = emptyList()
                                        streambenchImportMessage = null
                                        streambenchPlaybackMessage = null
                                    }
                                    if (tool == BuiltInBenchTool.CODEBENCH_QR_BARCODE && !shouldEnable) {
                                        codebenchText = ""
                                        codebenchFormat = CodebenchBarcodeFormat.QR_CODE
                                        codebenchFormatMenuExpanded = false
                                        codebenchMatrix = null
                                        codebenchGenerationMessage = null
                                        codebenchImportMessage = null
                                    }
                                },
                                modifier = Modifier.testTag("bench_toggle_${tool.id}")
                            )
                        }

                        if (tool == BuiltInBenchTool.STREAMBENCH_PLAYER && enabled) {
                            HorizontalDivider()
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "Import an M3U/M3U8 playlist locally, then search, favorite or explicitly start an allowed HTTPS stream. Playlist contents stay in memory; only local station hashes are kept for favorites and recents.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        try {
                                            streambenchImportLauncher.launch(STREAMBENCH_PLAYLIST_MIME_TYPES)
                                        } catch (_: ActivityNotFoundException) {
                                            streambenchImportMessage = "No document picker is available."
                                        }
                                    },
                                    enabled = !streambenchImporting,
                                    modifier = Modifier.testTag("streambench_import_playlist")
                                ) {
                                    Text(if (streambenchImporting) "Importing…" else "Import playlist")
                                }
                                streambenchImportMessage?.let { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall)
                                }
                                streambenchPlaybackMessage?.let { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall)
                                }
                                if (streambenchEntries.isNotEmpty()) {
                                    StreambenchStationBrowser(
                                        entries = streambenchEntries,
                                        favoriteKeys = favoriteStationKeys,
                                        recentKeys = recentStationKeys,
                                        onToggleFavorite = { key ->
                                            favoriteStationKeys = stationStore.toggleFavorite(key)
                                        },
                                        onPlay = { entry, key ->
                                            recentStationKeys = stationStore.recordRecent(key)
                                            streambenchPlaybackMessage = launchStreambenchPlayback(
                                                context = context,
                                                entry = entry,
                                                isEnabled = BuiltInBenchTool.STREAMBENCH_PLAYER in store.loadEnabledTools()
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (tool == BuiltInBenchTool.CODEBENCH_QR_BARCODE && enabled) {
                                HorizontalDivider()
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        "Generate or decode QR codes and barcodes locally. Images are downsampled before decoding and never uploaded.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = codebenchText,
                                        onValueChange = {
                                            codebenchText = it
                                            codebenchMatrix = null
                                            codebenchGenerationMessage = null
                                        },
                                        label = { Text("Text to encode") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("codebench_text")
                                    )
                                    Box {
                                        OutlinedButton(
                                            onClick = { codebenchFormatMenuExpanded = true },
                                            modifier = Modifier.testTag("codebench_format")
                                        ) {
                                            Text("Format: ${codebenchFormat.displayLabel()}")
                                        }
                                        DropdownMenu(
                                            expanded = codebenchFormatMenuExpanded,
                                            onDismissRequest = { codebenchFormatMenuExpanded = false }
                                        ) {
                                            CodebenchBarcodeFormat.entries.forEach { format ->
                                                DropdownMenuItem(
                                                    text = { Text(format.displayLabel()) },
                                                    onClick = {
                                                        codebenchFormat = format
                                                        codebenchFormatMenuExpanded = false
                                                        codebenchMatrix = null
                                                        codebenchGenerationMessage = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val (previewWidth, previewHeight) =
                                                codebenchPreviewDimensions(codebenchFormat)
                                            when (
                                                val result = CodebenchBarcodeGenerateAction.execute(
                                                    text = codebenchText,
                                                    format = codebenchFormat,
                                                    width = previewWidth,
                                                    height = previewHeight,
                                                    surface = BenchToolSurface.COMPANION_UI,
                                                    isEnabled = BuiltInBenchTool.CODEBENCH_QR_BARCODE in
                                                        store.loadEnabledTools()
                                                )
                                            ) {
                                                is CodebenchBarcodeGenerateActionResult.Completed -> {
                                                    codebenchMatrix = result.matrix
                                                    codebenchGenerationMessage = "Generated locally."
                                                }
                                                is CodebenchBarcodeGenerateActionResult.Blocked -> {
                                                    codebenchGenerationMessage =
                                                        "Generation is blocked by the current Bench policy."
                                                }
                                                is CodebenchBarcodeGenerateActionResult.Rejected -> {
                                                    codebenchMatrix = null
                                                    codebenchGenerationMessage =
                                                        "Enter valid, non-empty text for this format."
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("codebench_generate")
                                    ) {
                                        Text("Generate")
                                    }
                                    codebenchGenerationMessage?.let { message ->
                                        Text(message, style = MaterialTheme.typography.bodySmall)
                                    }
                                    codebenchMatrix?.let { matrix ->
                                        CodebenchBarcodePreview(matrix)
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                codebenchImportLauncher.launch(arrayOf("image/*"))
                                            } catch (_: ActivityNotFoundException) {
                                                codebenchImportMessage =
                                                    CodebenchImportUiResult("No document picker is available.")
                                            }
                                        },
                                        enabled = !codebenchImporting,
                                        modifier = Modifier.testTag("codebench_import_image")
                                    ) {
                                        Text(if (codebenchImporting) "Decoding…" else "Decode image")
                                    }
                                    codebenchImportMessage?.let { result ->
                                        Text(result.message, style = MaterialTheme.typography.bodySmall)
                                        result.decodedFormat?.let { format ->
                                            Text(
                                                "Format: ${format.displayLabel()}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        result.decodedText?.let { text ->
                                            Text(
                                                text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.testTag("codebench_decoded_text")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun CodebenchBarcodePreview(matrix: CodebenchBarcodeMatrix) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(matrix.width.toFloat() / matrix.height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .testTag("codebench_preview")
    ) {
        val cellWidth = size.width / matrix.width
        val cellHeight = size.height / matrix.height
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x * cellWidth,
                            y * cellHeight
                        ),
                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                    )
                }
            }
        }
    }
}

internal fun codebenchPreviewDimensions(format: CodebenchBarcodeFormat): Pair<Int, Int> = when (format) {
    CodebenchBarcodeFormat.QR_CODE,
    CodebenchBarcodeFormat.DATA_MATRIX,
    CodebenchBarcodeFormat.AZTEC -> CODEBENCH_SQUARE_PREVIEW to CODEBENCH_SQUARE_PREVIEW
    CodebenchBarcodeFormat.PDF_417 -> CODEBENCH_WIDE_PREVIEW_WIDTH to CODEBENCH_PDF417_PREVIEW_HEIGHT
    else -> CODEBENCH_WIDE_PREVIEW_WIDTH to CODEBENCH_LINEAR_PREVIEW_HEIGHT
}

private fun CodebenchBarcodeFormat.displayLabel(): String = name.replace('_', ' ')

private fun BenchToolNetworkBehavior.displayLabel(): String = when (this) {
    BenchToolNetworkBehavior.LOCAL_ONLY -> "Local only"
    BenchToolNetworkBehavior.NETWORK_OPTIONAL -> "Network only when a concrete action requires it"
    BenchToolNetworkBehavior.NETWORK_REQUIRED -> "Network required"
}

private const val CODEBENCH_SQUARE_PREVIEW = 256
private const val CODEBENCH_WIDE_PREVIEW_WIDTH = 512
private const val CODEBENCH_PDF417_PREVIEW_HEIGHT = 256
private const val CODEBENCH_LINEAR_PREVIEW_HEIGHT = 192
private const val CODEBENCH_POLICY_BLOCKED_MESSAGE =
    "Image decoding is blocked by the current Bench policy."
