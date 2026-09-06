package com.twojstar.llmbench.data.document

enum class DocumentDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class DocumentDiagnosticKind {
    MIXED_LINE_ENDINGS,
    NUL_CHARACTER,
    UNTERMINATED_CODE_FENCE
}

data class DocumentDiagnostic(
    val kind: DocumentDiagnosticKind,
    val severity: DocumentDiagnosticSeverity,
    val message: String,
    val line: Int,
    val column: Int
)

enum class DocumentRepairAction {
    NORMALIZE_LINE_ENDINGS,
    CLOSE_UNTERMINATED_CODE_FENCE
}

data class DocumentRepairResult(
    val document: TextDocument,
    val applied: List<DocumentRepairAction>
)

/** Objective, low-risk diagnostics for editable text documents. */
object DocumentDiagnostics {
    private data class OpenFence(
        val marker: Char,
        val length: Int,
        val line: Int,
        val column: Int
    )

    fun inspect(document: TextDocument): List<DocumentDiagnostic> {
        val diagnostics = mutableListOf<DocumentDiagnostic>()

        if (document.lineEndings.style == LineEndingStyle.MIXED) {
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

        findUnterminatedFence(document.text)?.let { fence ->
            diagnostics += DocumentDiagnostic(
                kind = DocumentDiagnosticKind.UNTERMINATED_CODE_FENCE,
                severity = DocumentDiagnosticSeverity.WARNING,
                message = "Code fence opened with ${fence.marker.toString().repeat(fence.length)} is not closed.",
                line = fence.line,
                column = fence.column
            )
        }

        return diagnostics
    }

    fun repair(
        document: TextDocument,
        normalizeTo: LineEnding? = null,
        closeUnterminatedCodeFence: Boolean = false
    ): DocumentRepairResult {
        var text = document.text
        val applied = mutableListOf<DocumentRepairAction>()

        if (normalizeTo != null) {
            val normalized = TextDocumentCodec.normalizeLineEndings(text, normalizeTo)
            if (normalized != text) {
                text = normalized
                applied += DocumentRepairAction.NORMALIZE_LINE_ENDINGS
            }
        }

        if (closeUnterminatedCodeFence) {
            val fence = findUnterminatedFence(text)
            if (fence != null) {
                val eol = normalizeTo ?: preferredLineEnding(TextDocumentCodec.detectLineEndings(text))
                if (!endsWithLineEnding(text)) text += eol.value
                text += fence.marker.toString().repeat(fence.length)
                applied += DocumentRepairAction.CLOSE_UNTERMINATED_CODE_FENCE
            }
        }

        return DocumentRepairResult(
            document = TextDocument(
                text = text,
                hadUtf8Bom = document.hadUtf8Bom,
                lineEndings = TextDocumentCodec.detectLineEndings(text)
            ),
            applied = applied
        )
    }

    private fun firstNulPosition(text: String): Pair<Int, Int>? {
        var line = 1
        var column = 1
        var index = 0
        while (index < text.length) {
            when (val char = text[index]) {
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
                    column++
                    index++
                }
            }
        }
        return null
    }

    private fun findUnterminatedFence(text: String): OpenFence? {
        val normalized = TextDocumentCodec.normalizeLineEndings(text, LineEnding.LF)
        var open: OpenFence? = null

        normalized.split('\n').forEachIndexed { index, line ->
            val current = open
            if (current == null) {
                parseOpeningFence(line, index + 1)?.let { open = it }
            } else if (isClosingFence(line, current)) {
                open = null
            }
        }
        return open
    }

    private fun parseOpeningFence(line: String, lineNumber: Int): OpenFence? {
        val indentation = line.takeWhile { it == ' ' }.length
        if (indentation > 3 || indentation == line.length) return null

        val marker = line[indentation]
        if (marker != '`' && marker != '~') return null
        val length = line.drop(indentation).takeWhile { it == marker }.length
        if (length < 3) return null

        val info = line.substring(indentation + length)
        if (marker == '`' && '`' in info) return null

        return OpenFence(marker = marker, length = length, line = lineNumber, column = indentation + 1)
    }

    private fun isClosingFence(line: String, open: OpenFence): Boolean {
        val indentation = line.takeWhile { it == ' ' }.length
        if (indentation > 3 || indentation == line.length || line[indentation] != open.marker) return false

        val markerLength = line.drop(indentation).takeWhile { it == open.marker }.length
        if (markerLength < open.length) return false
        return line.substring(indentation + markerLength).all { it == ' ' || it == '\t' }
    }

    private fun preferredLineEnding(counts: LineEndingCounts): LineEnding = when {
        counts.crlf >= counts.lf && counts.crlf >= counts.cr && counts.crlf > 0 -> LineEnding.CRLF
        counts.lf >= counts.cr && counts.lf > 0 -> LineEnding.LF
        counts.cr > 0 -> LineEnding.CR
        else -> LineEnding.LF
    }

    private fun endsWithLineEnding(text: String): Boolean = text.endsWith('\n') || text.endsWith('\r')
}
