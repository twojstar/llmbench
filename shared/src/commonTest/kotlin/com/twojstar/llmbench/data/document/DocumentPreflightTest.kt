package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.tokenizer.TokenCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentPreflightTest {
    @Test
    fun composesDocumentSafetySyntaxAndTokenChecksWithoutChangingSource() {
        val source = "{\r\n\"text\":\"a\u200Bb\",\n\"enabled\":true\r\n}"
        val document = TextDocument(
            text = source,
            hadUtf8Bom = true,
            lineEndings = TextDocumentCodec.detectLineEndings(source)
        )

        val report = DocumentPreflight.inspect(
            document = document,
            displayName = "settings.json",
            mimeType = "application/json",
            tokenCounter = FakeCounter
        )

        assertTrue(report.hadUtf8Bom)
        assertEquals(LineEndingStyle.MIXED, report.lineEndings.style)
        assertTrue(
            report.diagnostics.any { it.kind == DocumentDiagnosticKind.MIXED_LINE_ENDINGS }
        )
        assertTrue(report.textInspection.hasFindings)
        assertTrue(
            report.textInspection.findings.any { it.label == "Zero-width space" }
        )
        val validation = assertNotNull(report.structuredValidation)
        assertEquals(StructuredTextFormat.JSON, validation.format)
        assertTrue(validation.isValid)
        assertEquals(
            DocumentTokenSummary(count = source.length, encodingLabel = FakeCounter.encodingLabel),
            report.tokenSummary
        )
        assertEquals(source, document.text)
    }

    @Test
    fun derivesLineEndingsFromCurrentTextInsteadOfStaleDocumentMetadata() {
        val source = "one\ntwo\n"
        val document = TextDocument(
            text = source,
            hadUtf8Bom = false,
            lineEndings = LineEndingCounts(lf = 0, crlf = 2, cr = 0)
        )

        val report = DocumentPreflight.inspect(
            document = document,
            displayName = "notes.md"
        )

        assertEquals(LineEndingStyle.LF, report.lineEndings.style)
        assertTrue(report.diagnostics.isEmpty())
    }

    @Test
    fun invalidRecognizedStructuredTextStillReturnsTheOtherPreflightSignals() {
        val source = "<root>\u0000</root>"
        val document = TextDocument(
            text = source,
            hadUtf8Bom = false,
            lineEndings = TextDocumentCodec.detectLineEndings(source)
        )

        val report = DocumentPreflight.inspect(
            document = document,
            displayName = "payload.xml",
            mimeType = "application/xml"
        )

        assertFalse(assertNotNull(report.structuredValidation).isValid)
        assertTrue(report.diagnostics.any { it.kind == DocumentDiagnosticKind.NUL_CHARACTER })
        assertNull(report.tokenSummary)
    }

    @Test
    fun unsupportedMetadataSkipsStructuredValidationButKeepsPortableChecks() {
        val source = "# note\n"
        val document = TextDocument(
            text = source,
            hadUtf8Bom = false,
            lineEndings = TextDocumentCodec.detectLineEndings(source)
        )

        val report = DocumentPreflight.inspect(
            document = document,
            displayName = "notes.md",
            mimeType = "text/markdown",
            tokenCounter = FakeCounter
        )

        assertNull(report.structuredValidation)
        assertFalse(report.textInspection.hasFindings)
        assertTrue(report.diagnostics.isEmpty())
        assertEquals(source.length, assertNotNull(report.tokenSummary).count)
    }

    private object FakeCounter : TokenCounter {
        override val encodingLabel: String = "fake-exact"
        override fun count(text: String): Int = text.length
    }
}
