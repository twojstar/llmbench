package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.tokenizer.LocalTokenCounter
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import com.twojstar.llmbench.data.tokenizer.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenedTextDocumentPreflightTest {
    @Test
    fun forwardsProviderSafMetadataAndInjectedCounterToPortablePreflight() {
        val source = "<root />"
        val opened = OpenedTextDocument(
            document = textDocument(source),
            displayName = "payload.xml",
            hasProviderDisplayName = true,
            mimeType = "application/xml"
        )

        val report = opened.buildPreflightReport(FakeCounter)

        assertEquals(StructuredTextFormat.XML, report.structuredValidation?.format)
        assertTrue(report.structuredValidation?.isValid == true)
        assertEquals(source.length, report.tokenSummary?.count)
        assertEquals(FakeCounter.encodingLabel, report.tokenSummary?.encodingLabel)
    }

    @Test
    fun syntheticFallbackNameCannotConflictWithProviderMime() {
        val opened = OpenedTextDocument(
            document = textDocument("<root />"),
            displayName = "payload.json",
            hasProviderDisplayName = false,
            mimeType = "application/xml"
        )

        val report = opened.buildPreflightReport(FakeCounter)

        assertEquals(StructuredTextFormat.XML, report.structuredValidation?.format)
        assertTrue(report.structuredValidation?.isValid == true)
    }

    @Test
    fun defaultBridgeUsesTheRealLocalTokenizerBackend() {
        val opened = OpenedTextDocument(
            document = textDocument("hello world"),
            displayName = "notes.txt",
            hasProviderDisplayName = true,
            mimeType = "text/plain"
        )

        val report = opened.buildPreflightReport()

        assertNull(report.structuredValidation)
        assertEquals(LocalTokenCounter.ENCODING_LABEL, report.tokenSummary?.encodingLabel)
        assertEquals(2, report.tokenSummary?.count)
    }

    @Test
    fun interactiveBridgeSkipsTokenizationAboveSharedResponsivenessLimit() {
        val source = "x".repeat(MAX_INTERACTIVE_TOKENIZED_CHARS + 1)
        val opened = OpenedTextDocument(
            document = textDocument(source),
            displayName = "large.txt",
            hasProviderDisplayName = true,
            mimeType = "text/plain"
        )
        val mustNotRun = object : TokenCounter {
            override val encodingLabel: String = "must-not-run"
            override fun count(text: String): Int = error("Oversized interactive input was tokenized")
        }

        val report = opened.buildPreflightReport(mustNotRun)

        assertNull(report.tokenSummary)
    }

    private fun textDocument(text: String): TextDocument = TextDocument(
        text = text,
        hadUtf8Bom = false,
        lineEndings = TextDocumentCodec.detectLineEndings(text)
    )

    private object FakeCounter : TokenCounter {
        override val encodingLabel: String = "fake-exact"
        override fun count(text: String): Int = text.length
    }
}
