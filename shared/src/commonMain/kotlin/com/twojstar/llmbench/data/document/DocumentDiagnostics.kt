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
        val column: Int,
        val closingPrefix: String
    )

    private data class FenceLineContext(
        val contentStart: Int,
        val closingPrefix: String
    )

    private data class ListMarker(
        val contentStart: Int,
        val consumedWidth: Int
    )

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
                text += fence.closingPrefix + fence.marker.toString().repeat(fence.length)
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
        val context = fenceLineContext(line) ?: return null
        val start = context.contentStart
        if (start >= line.length) return null

        val marker = line[start]
        if (marker != '`' && marker != '~') return null
        val length = line.drop(start).takeWhile { it == marker }.length
        if (length < 3) return null

        val info = line.substring(start + length)
        if (marker == '`' && '`' in info) return null

        return OpenFence(
            marker = marker,
            length = length,
            line = lineNumber,
            column = start + 1,
            closingPrefix = context.closingPrefix
        )
    }

    private fun isClosingFence(line: String, open: OpenFence): Boolean {
        if (!line.startsWith(open.closingPrefix)) return false
        var index = open.closingPrefix.length
        var extraIndent = 0
        while (index < line.length && line[index] == ' ' && extraIndent < 3) {
            index++
            extraIndent++
        }
        if (index >= line.length || line[index] != open.marker) return false

        val markerLength = line.drop(index).takeWhile { it == open.marker }.length
        if (markerLength < open.length) return false
        return line.substring(index + markerLength).all { it == ' ' || it == '\t' }
    }

    /**
     * Extract a fence position while preserving enough Markdown container context to
     * close a fence inside block quotes and list items. List markers become equivalent
     * continuation indentation in the repair prefix.
     */
    private fun fenceLineContext(line: String): FenceLineContext? {
        var index = 0
        val closingPrefix = StringBuilder()
        var sawContainer = false

        while (index < line.length) {
            val spacesStart = index
            while (index < line.length && line[index] == ' ') index++
            val spaces = index - spacesStart

            if (!sawContainer && spaces > 3) return null

            if (index < line.length && line[index] == '>') {
                closingPrefix.append(" ".repeat(spaces)).append('>')
                index++
                if (index < line.length && (line[index] == ' ' || line[index] == '\t')) {
                    closingPrefix.append(line[index])
                    index++
                }
                sawContainer = true
                continue
            }

            parseListMarker(line, index)?.let { list ->
                closingPrefix.append(" ".repeat(spaces + list.consumedWidth))
                index = list.contentStart
                sawContainer = true
                continue
            }

            if (spaces > 3) return null
            closingPrefix.append(" ".repeat(spaces))
            return FenceLineContext(contentStart = index, closingPrefix = closingPrefix.toString())
        }

        return FenceLineContext(contentStart = index, closingPrefix = closingPrefix.toString())
    }

    private fun parseListMarker(line: String, start: Int): ListMarker? {
        if (start >= line.length) return null
        var markerEnd = start

        when (line[start]) {
            '-', '+', '*' -> markerEnd++
            in '0'..'9' -> {
                while (markerEnd < line.length && line[markerEnd].isDigit() && markerEnd - start < 9) {
                    markerEnd++
                }
                if (markerEnd == start || markerEnd >= line.length || (line[markerEnd] != '.' && line[markerEnd] != ')')) {
                    return null
                }
                markerEnd++
            }
            else -> return null
        }

        if (markerEnd >= line.length || (line[markerEnd] != ' ' && line[markerEnd] != '\t')) return null
        var contentStart = markerEnd
        var padding = 0
        while (contentStart < line.length && (line[contentStart] == ' ' || line[contentStart] == '\t') && padding < 4) {
            contentStart++
            padding++
        }
        if (padding == 0) return null

        return ListMarker(
            contentStart = contentStart,
            consumedWidth = contentStart - start
        )
    }

    private fun preferredLineEnding(counts: LineEndingCounts): LineEnding = when {
        counts.crlf >= counts.lf && counts.crlf >= counts.cr && counts.crlf > 0 -> LineEnding.CRLF
        counts.lf >= counts.cr && counts.lf > 0 -> LineEnding.LF
        counts.cr > 0 -> LineEnding.CR
        else -> LineEnding.LF
    }

    private fun endsWithLineEnding(text: String): Boolean = text.endsWith('\n') || text.endsWith('\r')
}
