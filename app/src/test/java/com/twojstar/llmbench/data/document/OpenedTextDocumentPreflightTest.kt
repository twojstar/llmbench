package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
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

    @Test
    fun policyGatedActionBridgeBlocksBeforeTokenCounting() {
        val opened = OpenedTextDocument(
            document = textDocument(HELLO_WORLD),
            displayName = NOTES_TXT,
            hasProviderDisplayName = true,
            mimeType = TEXT_PLAIN
        )
        var tokenCountCalls = 0
        val mustNotRun = object : TokenCounter {
            override val encodingLabel: String = "blocked"
            override fun count(text: String): Int {
                tokenCountCalls += 1
                return text.length
            }
        }

        val result = opened.executeDocbenchPreflightAction(
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = emptySet(),
            tokenCounter = mustNotRun
        )

        assertTrue(result is DocbenchDocumentPreflightActionResult.Blocked)
        val blocked = result as DocbenchDocumentPreflightActionResult.Blocked
        assertEquals(0, tokenCountCalls)
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION),
            blocked.availability.blockers
        )
    }

    @Test
    fun policyGatedActionBridgePreservesSafMetadataAndInteractiveTokenBudget() {
        val opened = OpenedTextDocument(
            document = textDocument(ROOT_XML),
            displayName = "synthetic.json",
            hasProviderDisplayName = false,
            mimeType = APPLICATION_XML
        )

        val result = opened.executeDocbenchPreflightAction(
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT,
            tokenCounter = FakeCounter
        )

        assertTrue(result is DocbenchDocumentPreflightActionResult.Completed)
        val completed = result as DocbenchDocumentPreflightActionResult.Completed
        assertEquals(StructuredTextFormat.XML, completed.report.structuredValidation?.format)
        assertTrue(completed.report.structuredValidation?.isValid == true)
        assertEquals(ROOT_XML.length, completed.report.tokenSummary?.count)

        val largeText = "x".repeat(MAX_INTERACTIVE_TOKENIZED_CHARS + 1)
        val largeOpened = OpenedTextDocument(
            document = textDocument(largeText),
            displayName = "large.txt",
            hasProviderDisplayName = true,
            mimeType = TEXT_PLAIN
        )
        val mustNotRun = object : TokenCounter {
            override val encodingLabel: String = "must-not-run-action"
            override fun count(text: String): Int = error("Oversized action input was tokenized")
        }
        val largeResult = largeOpened.executeDocbenchPreflightAction(
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT,
            tokenCounter = mustNotRun
        )

        assertTrue(largeResult is DocbenchDocumentPreflightActionResult.Completed)
        assertNull((largeResult as DocbenchDocumentPreflightActionResult.Completed).report.tokenSummary)
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
        val DOCUMENT_READ_GRANT = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        const val ROOT_XML = "<root />"
        const val APPLICATION_XML = "application/xml"
        const val HELLO_WORLD = "hello world"
        const val NOTES_TXT = "notes.txt"
        const val TEXT_PLAIN = "text/plain"
    }
}
