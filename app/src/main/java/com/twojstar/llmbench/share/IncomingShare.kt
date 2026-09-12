package com.twojstar.llmbench.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.twojstar.llmbench.data.model.WebAiService
import java.net.URI

data class IncomingSharePayload(
    val text: String? = null,
    val uriStrings: List<String> = emptyList(),
    val mimeTypeHint: String? = null,
    val isOpenDocument: Boolean = false
) {
    val attachmentCount: Int get() = uriStrings.size
    val isEmpty: Boolean get() = text == null && uriStrings.isEmpty()
}

data class PendingWebShare(
    val id: Long,
    val service: WebAiService,
    val payload: IncomingSharePayload,
    val isTextClaimed: Boolean = false
)

internal fun PendingWebShare.claimText(): PendingWebShare? =
    takeIf { payload.text != null && !isTextClaimed }
        ?.copy(isTextClaimed = true)

internal fun PendingWebShare.completeTextClaim(): PendingWebShare? {
    if (!isTextClaimed || payload.text == null) return this
    val remainingPayload = payload.copy(text = null)
    return if (remainingPayload.isEmpty) null else {
        copy(payload = remainingPayload, isTextClaimed = false)
    }
}

internal fun PendingWebShare.releaseTextClaim(): PendingWebShare =
    if (isTextClaimed) copy(isTextClaimed = false) else this

internal fun normalizeIncomingSharePayload(
    text: String?,
    uriStrings: List<String>,
    mimeTypeHint: String? = null,
    isOpenDocument: Boolean = false
): IncomingSharePayload? {
    val normalizedText = text?.takeIf(String::isNotBlank)
    val normalizedUris = uriStrings.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter(::isContentUriString)
        .distinct()
        .toList()
    val normalizedMimeTypeHint = mimeTypeHint
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { value ->
            val parts = value.split('/', limit = 2)
            parts.size == 2 && parts.all(String::isNotBlank) && '*' !in value
        }
        ?.takeIf { normalizedUris.isNotEmpty() }
    return IncomingSharePayload(
        text = normalizedText,
        uriStrings = normalizedUris,
        mimeTypeHint = normalizedMimeTypeHint,
        isOpenDocument = isOpenDocument && normalizedUris.isNotEmpty()
    ).takeUnless(IncomingSharePayload::isEmpty)
}

private fun isContentUriString(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "content" && !uri.rawAuthority.isNullOrBlank()
}.getOrDefault(false)

internal fun selectIncomingShareText(
    action: String?,
    singleText: String?,
    multipleTexts: List<String>,
    clipTexts: List<String>
): String? {
    val primaryTexts = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(singleText)
        Intent.ACTION_SEND_MULTIPLE -> multipleTexts
        else -> emptyList()
    }
    return primaryTexts
        .filter(String::isNotBlank)
        .joinToString("\n")
        .takeIf(String::isNotBlank)
        ?: clipTexts.filter(String::isNotBlank).joinToString("\n").takeIf(String::isNotBlank)
}

internal fun extractIncomingSharePayload(intent: Intent): IncomingSharePayload? {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
        return null
    }

    val clipTexts = intent.clipData?.let { clip ->
        (0 until clip.itemCount)
            .mapNotNull { index -> clip.getItemAt(index).text?.toString() }
    }.orEmpty()
    val singleText = if (intent.action == Intent.ACTION_SEND) {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    } else null
    val multipleTexts = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
            .orEmpty()
            .map(CharSequence::toString)
    } else emptyList()
    val text = selectIncomingShareText(
        action = intent.action,
        singleText = singleText,
        multipleTexts = multipleTexts,
        clipTexts = clipTexts
    )
    val uriStrings = buildList {
        addAll(intentStreamUris(intent).map(Uri::toString))
        intent.clipData?.let { clip ->
            repeat(clip.itemCount) { index ->
                clip.getItemAt(index).uri?.toString()?.let(::add)
            }
        }
    }
    return normalizeIncomingSharePayload(text, uriStrings, intent.type)
}

internal fun selectIncomingViewUri(action: String?, uriString: String?): String? =
    uriString
        ?.takeIf { action == Intent.ACTION_VIEW }
        ?.takeIf(::isContentUriString)

internal fun extractIncomingViewPayload(intent: Intent): IncomingSharePayload? {
    val uri = selectIncomingViewUri(intent.action, intent.data?.toString()) ?: return null
    return normalizeIncomingSharePayload(
        text = null,
        uriStrings = listOf(uri),
        mimeTypeHint = intent.type,
        isOpenDocument = true
    )
}

private val markdownWorkspaceDocumentMimeTypes = setOf(
    "application/json",
    "application/ld+json",
    "application/xml",
    "application/yaml",
    "application/x-yaml"
)

internal fun IncomingSharePayload.canOpenInMarkdownWorkspace(): Boolean = when {
    text != null -> attachmentCount == 0
    !isOpenDocument || attachmentCount != 1 -> false
    mimeTypeHint?.startsWith("text/") == true -> true
    else -> mimeTypeHint in markdownWorkspaceDocumentMimeTypes
}

@Suppress("DEPRECATION")
private fun intentStreamUris(intent: Intent): List<Uri> = when (intent.action) {
    Intent.ACTION_SEND -> listOfNotNull(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    )
    Intent.ACTION_SEND_MULTIPLE -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
    else -> emptyList()
}
