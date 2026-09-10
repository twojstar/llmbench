package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.security.TextInspectionResult
import com.twojstar.llmbench.data.security.TextInspector
import com.twojstar.llmbench.data.tokenizer.TokenCounter

data class DocumentTokenSummary(
    val count: Int,
    val encodingLabel: String
)

data class DocumentPreflightReport(
    val hadUtf8Bom: Boolean,
    val lineEndings: LineEndingCounts,
    val diagnostics: List<DocumentDiagnostic>,
    val textInspection: TextInspectionResult,
    val structuredValidation: StructuredTextValidationResult?,
    val tokenSummary: DocumentTokenSummary?
)

/**
 * Composes the existing portable document checks into one read-only pre-flight report.
 *
 * The report does not retain the complete source text, though Text Inspector findings can contain
 * bounded previews needed to explain suspicious carriers. Structured syntax is validated only when
 * filename/MIME metadata identifies a supported format, and token counting is optional so platform
 * clients can inject an exact local backend without making the portable core depend on one tokenizer.
 * This work is CPU-bound; UI callers should run it away from the main thread for non-trivial inputs.
 */
object DocumentPreflight {
    fun inspect(
        document: TextDocument,
        displayName: String?,
        mimeType: String? = null,
        tokenCounter: TokenCounter? = null
    ): DocumentPreflightReport {
        val text = document.text
        val tokenSummary = tokenCounter?.let { counter ->
            DocumentTokenSummary(
                count = counter.count(text),
                encodingLabel = counter.encodingLabel
            )
        }

        return DocumentPreflightReport(
            hadUtf8Bom = document.hadUtf8Bom,
            lineEndings = TextDocumentCodec.detectLineEndings(text),
            diagnostics = DocumentDiagnostics.inspect(document).toList(),
            textInspection = TextInspector.inspect(text),
            structuredValidation = validateStructuredTextDocument(
                text = text,
                displayName = displayName,
                mimeType = mimeType
            ),
            tokenSummary = tokenSummary
        )
    }
}
