package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.tokenizer.LocalTokenCounter
import com.twojstar.llmbench.data.tokenizer.TokenCounter

/**
 * Builds the portable pre-flight report from the metadata captured by the Android SAF boundary.
 *
 * Keeping this wiring in one place prevents UI callers from dropping the provider MIME hint or using
 * a different tokenizer path. The report remains read-only and does not retain the complete source.
 */
internal fun OpenedTextDocument.buildPreflightReport(
    tokenCounter: TokenCounter = LocalTokenCounter
): DocumentPreflightReport = DocumentPreflight.inspect(
    document = document,
    displayName = displayName,
    mimeType = mimeType,
    tokenCounter = tokenCounter
)
