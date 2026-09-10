package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.tokenizer.LocalTokenCounter
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import com.twojstar.llmbench.data.tokenizer.TokenCounter

/**
 * Builds the portable pre-flight report from metadata captured by the Android SAF boundary.
 *
 * Only a provider-supplied display name is forwarded as format evidence; synthetic fallback names
 * must not conflict with a real provider MIME hint. Interactive token counting follows the shared
 * responsiveness budget so an imported document cannot make this convenience path tokenize the full
 * 8 MiB document limit. Callers may pass null to skip token counting explicitly. The report remains
 * read-only and does not retain the complete source.
 */
internal fun OpenedTextDocument.buildPreflightReport(
    tokenCounter: TokenCounter? = LocalTokenCounter
): DocumentPreflightReport = DocumentPreflight.inspect(
    document = document,
    displayName = displayName.takeIf { hasProviderDisplayName },
    mimeType = mimeType,
    tokenCounter = interactiveTokenCounter(tokenCounter)
)

/**
 * Runs the first-party Docbench document action for an already opened Android SAF document.
 *
 * File access stays outside this function. The caller supplies the current app-level Bench grant and
 * route, while the shared action performs the canonical policy check before document inspection.
 */
internal fun OpenedTextDocument.executeDocbenchPreflightAction(
    surface: BenchToolSurface,
    isEnabled: Boolean,
    grantedPermissions: Set<BenchToolPermission>,
    tokenCounter: TokenCounter? = LocalTokenCounter
): DocbenchDocumentPreflightActionResult = DocbenchDocumentPreflightAction.execute(
    document = document,
    displayName = displayName.takeIf { hasProviderDisplayName },
    mimeType = mimeType,
    surface = surface,
    isEnabled = isEnabled,
    grantedPermissions = grantedPermissions,
    tokenCounter = interactiveTokenCounter(tokenCounter)
)

private fun OpenedTextDocument.interactiveTokenCounter(tokenCounter: TokenCounter?): TokenCounter? =
    tokenCounter?.takeIf { document.text.length <= MAX_INTERACTIVE_TOKENIZED_CHARS }
