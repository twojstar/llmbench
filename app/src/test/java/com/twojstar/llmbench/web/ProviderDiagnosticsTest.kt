package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiagnosticsTest {
    @Test
    fun hostSummaryDropsPathQueryAndFragment() {
        assertEquals(
            "chatgpt.com",
            providerDiagnosticsHost("https://ChatGPT.com/c/secret-conversation?token=secret#message")
        )
        assertNull(providerDiagnosticsHost("not a url"))
    }

    @Test
    fun documentMatchIgnoresFragmentsButRejectsNavigation() {
        assertTrue(
            providerDiagnosticsDocumentMatches(
                "https://chatgpt.com/c/123#first",
                "https://chatgpt.com/c/123#second"
            )
        )
        assertTrue(
            !providerDiagnosticsDocumentMatches(
                "https://chatgpt.com/c/123",
                "https://chatgpt.com/c/456"
            )
        )
        assertTrue(
            !providerDiagnosticsDocumentMatches(
                "https://chatgpt.com/c/123?mode=a",
                "https://chatgpt.com/c/123?mode=b"
            )
        )
    }

    @Test
    fun acceptTypesKeepOnlyBoundedMimeTokens() {
        assertEquals(
            "image/*, application/pdf",
            sanitizeProviderAcceptTypes(arrayOf(" image/*, application/pdf ", "image/*"))
        )
        assertEquals(
            "*/*",
            sanitizeProviderAcceptTypes(arrayOf("https://example.test/?token=secret", "not-a-mime"))
        )
        assertEquals("*/*", sanitizeProviderAcceptTypes(null))
    }

    @Test
    fun probeScriptIsRestrictedToProviderOwnedHttpsPages() {
        assertTrue(
            providerDiagnosticsProbeScript(WebAiService.CHATGPT, "https://chatgpt.com/") != null
        )
        assertNull(
            providerDiagnosticsProbeScript(WebAiService.CHATGPT, "https://chatgpt.com.evil.example/")
        )
        assertNull(
            providerDiagnosticsProbeScript(WebAiService.CHATGPT, "http://chatgpt.com/")
        )
    }

    @Test
    fun parserAcceptsNonNegativeCapabilityCountsAndKnownEditorKinds() {
        assertEquals(
            ProviderDiagnosticsProbeResult.Ready(
                ProviderDomCapabilities(
                    textareas = 2,
                    contentEditables = 1,
                    fileInputs = 3,
                    activeEditorKind = "textarea"
                )
            ),
            parseProviderDiagnosticsProbeResult("\"2|1|3|textarea\"")
        )
        assertEquals(
            ProviderDiagnosticsProbeResult.Ready(
                ProviderDomCapabilities(
                    textareas = 0,
                    contentEditables = 0,
                    fileInputs = 0,
                    activeEditorKind = null
                )
            ),
            parseProviderDiagnosticsProbeResult("\"0|0|0|none\"")
        )
        assertEquals(
            ProviderDiagnosticsProbeResult.Ready(
                ProviderDomCapabilities(1, 0, 0, null)
            ),
            parseProviderDiagnosticsProbeResult("\"1|0|0|unexpected\"")
        )
    }

    @Test
    fun safeReportContainsOnlyExplicitDiagnosticsFields() {
        val report = ProviderDiagnosticsSnapshot(
            providerName = "Example AI",
            host = "chat.example.test",
            providerOwned = true,
            webViewPackage = "com.android.webview 1.2.3",
            siteMode = "mobile",
            activityTracking = "not verified",
            activityState = "idle",
            fileChooserRequests = 1,
            fileChooserMode = "single",
            fileChooserHost = "chat.example.test",
            fileChooserAcceptTypes = "image/*",
            fileChooserOutcome = "selected (1)",
            domProbeSummary = "textarea=1, contenteditable=0, file=1, active=textarea"
        ).safeReport()

        assertTrue(report.contains("Host: chat.example.test"))
        assertTrue(report.contains("File chooser requests: 1"))
        assertTrue(report.contains("Last file chooser host: chat.example.test"))
        assertTrue(!report.contains("https://"))
        assertTrue(!report.contains("cookie", ignoreCase = true))
        assertTrue(!report.contains("token", ignoreCase = true))
    }

    @Test
    fun parserRejectsOffProviderAndMalformedResultsConservatively() {
        assertEquals(
            ProviderDiagnosticsProbeResult.OffProvider,
            parseProviderDiagnosticsProbeResult("\"off-provider\"")
        )
        assertEquals(
            ProviderDiagnosticsProbeResult.Failed,
            parseProviderDiagnosticsProbeResult("\"-1|0|0|none\"")
        )
        assertEquals(
            ProviderDiagnosticsProbeResult.Failed,
            parseProviderDiagnosticsProbeResult("null")
        )
        assertEquals(
            ProviderDiagnosticsProbeResult.Failed,
            parseProviderDiagnosticsProbeResult(null)
        )
    }
}
