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
        val opened = OpenedTextDocument(
            document = textDocument(ROOT_XML),
            displayName = "payload.xml",
            hasProviderDisplayName = true,
            mimeType = APPLICATION_XML
        )

        val report = opened.buildPreflightReport(FakeCounter)

        assertEquals(StructuredTextFormat.XML, report.structuredValidation?.format)
        assertTrue(report.structuredValidation?.isValid == true)
        assertEquals(ROOT_XML.length, report.tokenSummary?.count)
        assertEquals(FakeCounter.encodingLabel, report.tokenSummary?.encodingLabel)
    }

    @Test
    fun syntheticFallbackNameCannotConflictWithProviderMime() {
        val opened = OpenedTextDocument(
            document = textDocument(ROOT_XML),
            displayName = "payload.json",
            hasProviderDisplayName = false,
            mimeType = APPLICATION_XML
        )

        val report = opened.buildPreflightReport(FakeCounter)

        assertEquals(StructuredTextFormat.XML, report.structuredValidation?.format)
        assertTrue(report.structuredValidation?.isValid == true)
    }

    @Test
    fun defaultBridgeUsesTheRealLocalTokenizerBackend() {
        val opened = OpenedTextDocument(
            document = textDocument(HELLO_WORLD),
            displayName = NOTES_TXT,
            hasProviderDisplayName = true,
            mimeType = TEXT_PLAIN
        )

        val report = opened.buildPreflightReport()

        assertNull(report.structuredValidation)
        assertEquals(LocalTokenCounter.ENCODING_LABEL, report.tokenSummary?.encodingLabel)
        assertEquals(2, report.tokenSummary?.count)
    }

    @Test
    fun callerCanRequestTokenlessPreflight() {
        val opened = OpenedTextDocument(
            document = textDocument(HELLO_WORLD),
            displayName = NOTES_TXT,
            hasProviderDisplayName = true,
            mimeType = TEXT_PLAIN
        )

        val report = opened.buildPreflightReport(tokenCounter = null)

        assertNull(report.tokenSummary)
    }

    @Test
    fun interactiveBridgeSkipsTokenizationAboveSharedResponsivenessLimit() {
        val source = "x".repeat(MAX_INTERACTIVE_TOKENIZED_CHARS + 1)
        val opened = OpenedTextDocument(
            document = textDocument(source),
            displayName = "large.txt",
            hasProviderDisplayName = true,
            mimeType = TEXT_PLAIN
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

    private companion object {
        const val ROOT_XML = "<root />"
        const val APPLICATION_XML = "application/xml"
        const val HELLO_WORLD = "hello world"
        const val NOTES_TXT = "notes.txt"
        const val TEXT_PLAIN = "text/plain"
    }
}
