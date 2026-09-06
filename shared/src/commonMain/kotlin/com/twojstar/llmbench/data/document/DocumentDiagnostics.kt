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
    private sealed interface MarkdownContainer

    private data object BlockQuoteContainer : MarkdownContainer

    private data class ListContainer(
        val continuationColumns: Int
    ) : MarkdownContainer

    private data class Cursor(
        val index: Int,
        val column: Int
    )

    private data class OpenFence(
        val marker: Char,
        val length: Int,
        val line: Int,
        val column: Int,
        val containers: List<MarkdownContainer>
    )

    private data class FenceLineContext(
        val contentStart: Int,
        val containers: List<MarkdownContainer>
    )

    private data class ListMarker(
        val contentStart: Int,
        val contentColumn: Int
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
                text += repairPrefix(fence.containers) + fence.marker.toString().repeat(fence.length)
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
        val context = openingFenceContext(line) ?: return null
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
            containers = context.containers
        )
    }

    private fun isClosingFence(line: String, open: OpenFence): Boolean {
        var cursor = Cursor(index = 0, column = 0)
        for (container in open.containers) {
            cursor = when (container) {
                BlockQuoteContainer -> consumeBlockQuote(line, cursor) ?: return false
                is ListContainer -> consumeRequiredIndent(line, cursor, container.continuationColumns) ?: return false
            }
        }

        cursor = consumeIndentAtMost(line, cursor, MAX_FENCE_INDENT_COLUMNS) ?: return false
        if (cursor.index >= line.length || line[cursor.index] != open.marker) return false

        val markerLength = line.drop(cursor.index).takeWhile { it == open.marker }.length
        if (markerLength < open.length) return false
        return line.substring(cursor.index + markerLength).all { it == ' ' || it == '\t' }
    }

    private fun openingFenceContext(line: String): FenceLineContext? {
        var cursor = Cursor(index = 0, column = 0)
        val containers = mutableListOf<MarkdownContainer>()

        while (cursor.index < line.length) {
            val levelStartColumn = cursor.column
            cursor = consumeIndentAtMost(line, cursor, MAX_FENCE_INDENT_COLUMNS) ?: return null

            if (line.getOrNull(cursor.index) == '>') {
                cursor = consumeBlockQuoteMarker(line, cursor)
                containers += BlockQuoteContainer
                continue
            }

            val list = parseListMarker(line, cursor)
            if (list != null) {
                containers += ListContainer(continuationColumns = list.contentColumn - levelStartColumn)
                cursor = Cursor(index = list.contentStart, column = list.contentColumn)
                continue
            }

            return FenceLineContext(contentStart = cursor.index, containers = containers.toList())
        }

        return FenceLineContext(contentStart = cursor.index, containers = containers.toList())
    }

    private fun consumeBlockQuote(line: String, start: Cursor): Cursor? {
        val indented = consumeIndentAtMost(line, start, MAX_FENCE_INDENT_COLUMNS) ?: return null
        if (line.getOrNull(indented.index) != '>') return null
        return consumeBlockQuoteMarker(line, indented)
    }

    private fun consumeBlockQuoteMarker(line: String, start: Cursor): Cursor {
        var cursor = Cursor(index = start.index + 1, column = start.column + 1)
        val next = line.getOrNull(cursor.index)
        if (next == ' ' || next == '\t') cursor = advanceWhitespace(cursor, next)
        return cursor
    }

    private fun parseListMarker(line: String, start: Cursor): ListMarker? = when (line.getOrNull(start.index)) {
        '-', '+', '*' -> finishListMarker(line, start, markerLength = 1)
        in '0'..'9' -> parseOrderedListMarker(line, start)
        else -> null
    }

    private fun parseOrderedListMarker(line: String, start: Cursor): ListMarker? {
        var index = start.index
        var digits = 0
        while (index < line.length && line[index].isDigit() && digits < MAX_ORDERED_LIST_DIGITS) {
            index++
            digits++
        }
        if (digits == 0 || line.getOrNull(index) !in listOf('.', ')')) return null
        return finishListMarker(line, start, markerLength = digits + 1)
    }

    private fun finishListMarker(line: String, start: Cursor, markerLength: Int): ListMarker? {
        val afterMarker = Cursor(index = start.index + markerLength, column = start.column + markerLength)
        val firstPadding = line.getOrNull(afterMarker.index)
        if (firstPadding != ' ' && firstPadding != '\t') return null

        var cursor = afterMarker
        val paddingStartColumn = cursor.column
        while (cursor.index < line.length) {
            val char = line[cursor.index]
            if (char != ' ' && char != '\t') break
            val next = advanceWhitespace(cursor, char)
            if (next.column - paddingStartColumn > MAX_LIST_PADDING_COLUMNS) break
            cursor = next
        }
        if (cursor.column == paddingStartColumn) return null

        return ListMarker(contentStart = cursor.index, contentColumn = cursor.column)
    }

    private fun consumeIndentAtMost(line: String, start: Cursor, maxColumns: Int): Cursor? {
        var cursor = start
        while (cursor.index < line.length) {
            val char = line[cursor.index]
            if (char != ' ' && char != '\t') break
            val next = advanceWhitespace(cursor, char)
            if (next.column - start.column > maxColumns) return null
            cursor = next
        }
        return cursor
    }

    private fun consumeRequiredIndent(line: String, start: Cursor, requiredColumns: Int): Cursor? {
        var cursor = start
        while (cursor.index < line.length && cursor.column - start.column < requiredColumns) {
            val char = line[cursor.index]
            if (char != ' ' && char != '\t') return null
            cursor = advanceWhitespace(cursor, char)
        }
        return cursor.takeIf { it.column - start.column >= requiredColumns }
    }

    private fun advanceWhitespace(cursor: Cursor, char: Char): Cursor {
        val nextColumn = if (char == '\t') {
            cursor.column + (TAB_STOP_COLUMNS - (cursor.column % TAB_STOP_COLUMNS))
        } else {
            cursor.column + 1
        }
        return Cursor(index = cursor.index + 1, column = nextColumn)
    }

    private fun repairPrefix(containers: List<MarkdownContainer>): String = buildString {
        containers.forEach { container ->
            when (container) {
                BlockQuoteContainer -> append("> ")
                is ListContainer -> append(" ".repeat(container.continuationColumns))
            }
        }
    }

    private fun preferredLineEnding(counts: LineEndingCounts): LineEnding = when {
        counts.crlf >= counts.lf && counts.crlf >= counts.cr && counts.crlf > 0 -> LineEnding.CRLF
        counts.lf >= counts.cr && counts.lf > 0 -> LineEnding.LF
        counts.cr > 0 -> LineEnding.CR
        else -> LineEnding.LF
    }

    private fun endsWithLineEnding(text: String): Boolean = text.endsWith('\n') || text.endsWith('\r')

    private const val MAX_FENCE_INDENT_COLUMNS = 3
    private const val MAX_LIST_PADDING_COLUMNS = 4
    private const val MAX_ORDERED_LIST_DIGITS = 9
    private const val TAB_STOP_COLUMNS = 4
}
