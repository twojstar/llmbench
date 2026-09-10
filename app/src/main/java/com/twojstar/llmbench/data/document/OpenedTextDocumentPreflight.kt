package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.tokenizer.LocalTokenCounter
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import com.twojstar.llmbench.data.tokenizer.TokenCounter

/**
 * Builds the portable pre-flight report from metadata captured by the Android SAF boundary.
 *
 * Only a provider-supplied display name is forwarded as format evidence; synthetic fallback names
 * must not conflict with a real provider MIME hint. Interactive token counting follows the shared
 * responsiveness budget so an imported document cannot make this convenience path tokenize the full
 * 8 MiB document limit. The report remains read-only and does not retain the complete source.
 */
internal fun OpenedTextDocument.buildPreflightReport(
    tokenCounter: TokenCounter = LocalTokenCounter
): DocumentPreflightReport = DocumentPreflight.inspect(
    document = document,
    displayName = displayName.takeIf { hasProviderDisplayName },
    mimeType = mimeType,
    tokenCounter = tokenCounter.takeIf {
        document.text.length <= MAX_INTERACTIVE_TOKENIZED_CHARS
    }
)
