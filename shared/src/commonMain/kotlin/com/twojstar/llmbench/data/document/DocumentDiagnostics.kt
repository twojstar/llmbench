package com.twojstar.llmbench.data.document

enum class DocumentDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class DocumentDiagnosticKind {
    MIXED_LINE_ENDINGS,
    NUL_CHARACTER
}

data class DocumentDiagnostic(
    val kind: DocumentDiagnosticKind,
    val severity: DocumentDiagnosticSeverity,
    val message: String,
    val line: Int,
    val column: Int
)

enum class DocumentRepairAction {
    NORMALIZE_LINE_ENDINGS
}

data class DocumentRepairResult(
    val document: TextDocument,
    val applied: List<DocumentRepairAction>
)

/** Objective, low-risk diagnostics for editable text documents. */
object DocumentDiagnostics {
    fun inspect(document: TextDocument): List<DocumentDiagnostic> {
        val diagnostics = mutableListOf<DocumentDiagnostic>()
        val currentLineEndings = TextDocumentCodec.detectLineEndings(document.text)

        if (currentLineEndings.style == LineEndingStyle.MIXED) {
            diagnostics += DocumentDiagnostic(
                kind = DocumentDiagnosticKind.MIXED_LINE_ENDINGS,
                severity = DocumentDiagnosticSeverity.WARNING,
                message = "Document mixes LF, CRLF or CR line endings.",
                line = 1,
                column = 1
            )
        }

        firstNulPosition(document.text)?.let { (line, column) ->
            diagnostics += DocumentDiagnostic(
                kind = DocumentDiagnosticKind.NUL_CHARACTER,
                severity = DocumentDiagnosticSeverity.WARNING,
                message = "NUL character found in text; verify that this is not binary or mis-decoded input.",
                line = line,
                column = column
            )
        }

        return diagnostics
    }

    /**
     * Applies only explicitly requested, syntax-independent repairs.
     * Markdown-structure repairs belong behind a real Markdown parser rather than heuristics.
     */
    fun repair(
        document: TextDocument,
        normalizeTo: LineEnding? = null
    ): DocumentRepairResult {
        if (normalizeTo == null) {
            return DocumentRepairResult(document = document, applied = emptyList())
        }

        val normalized = TextDocumentCodec.normalizeLineEndings(document.text, normalizeTo)
        val currentLineEndings = TextDocumentCodec.detectLineEndings(normalized)
        if (normalized == document.text) {
            val refreshed = if (currentLineEndings == document.lineEndings) {
                document
            } else {
                document.copy(lineEndings = currentLineEndings)
            }
            return DocumentRepairResult(document = refreshed, applied = emptyList())
        }

        return DocumentRepairResult(
            document = TextDocument(
                text = normalized,
                hadUtf8Bom = document.hadUtf8Bom,
                lineEndings = currentLineEndings
            ),
            applied = listOf(DocumentRepairAction.NORMALIZE_LINE_ENDINGS)
        )
    }

    private fun firstNulPosition(text: String): Pair<Int, Int>? {
        var line = 1
        var column = 1
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\u0000' -> return line to column
                '\r' -> {
                    line++
                    column = 1
                    index += if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
                }
                '\n' -> {
                    line++
                    column = 1
                    index++
                }
                else -> {
                    val isSurrogatePair = text[index].isHighSurrogate() &&
                        index + 1 < text.length && text[index + 1].isLowSurrogate()
                    column++
                    index += if (isSurrogatePair) 2 else 1
                }
            }
        }
        return null
    }
}
