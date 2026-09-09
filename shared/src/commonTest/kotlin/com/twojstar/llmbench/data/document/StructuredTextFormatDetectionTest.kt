package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredTextFormatDetectionTest {
    @Test
    fun detectsCanonicalFileExtensionsCaseInsensitively() {
        assertEquals(StructuredTextFormat.JSON, detectStructuredTextFormat("config.JSON"))
        assertEquals(StructuredTextFormat.YAML, detectStructuredTextFormat("profile.yml"))
        assertEquals(StructuredTextFormat.YAML, detectStructuredTextFormat("profile.YAML"))
        assertEquals(StructuredTextFormat.XML, detectStructuredTextFormat("document.xml"))
    }

    @Test
    fun detectsStandardAndStructuredSuffixMimeTypes() {
        assertEquals(
            StructuredTextFormat.JSON,
            detectStructuredTextFormat(null, "application/ld+json; charset=utf-8")
        )
        assertEquals(
            StructuredTextFormat.YAML,
            detectStructuredTextFormat(null, "application/yaml")
        )
        assertEquals(
            StructuredTextFormat.YAML,
            detectStructuredTextFormat(null, "text/x-yaml")
        )
        assertEquals(
            StructuredTextFormat.XML,
            detectStructuredTextFormat(null, "image/svg+xml")
        )
    }

    @Test
    fun genericMimeDoesNotOverrideRecognizedExtension() {
        assertEquals(
            StructuredTextFormat.YAML,
            detectStructuredTextFormat("settings.yaml", "text/plain")
        )
        assertEquals(
            StructuredTextFormat.JSON,
            detectStructuredTextFormat("settings.json", "application/octet-stream")
        )
    }

    @Test
    fun conflictingSpecificHintsFailClosed() {
        assertNull(detectStructuredTextFormat("settings.json", "application/xml"))
        assertNull(detectStructuredTextFormat("settings.xml", "application/yaml"))
    }

    @Test
    fun unsupportedOrAmbiguousNamesAreNotContentSniffed() {
        assertNull(detectStructuredTextFormat("README.md", "text/markdown"))
        assertNull(detectStructuredTextFormat("settings.jsonc", "text/plain"))
        assertNull(detectStructuredTextFormat("document", null))
        assertNull(detectStructuredTextFormat(null, "application/octet-stream"))
    }

    @Test
    fun validateDocumentUsesDetectedFormatAndSkipsUnsupportedFiles() {
        val validJson = validateStructuredTextDocument(
            text = "{\"enabled\":true}",
            displayName = "settings.json"
        )
        val invalidXml = validateStructuredTextDocument(
            text = "<root>",
            displayName = "payload.xml"
        )

        assertTrue(requireNotNull(validJson).isValid)
        assertEquals(StructuredTextFormat.JSON, validJson.format)
        assertFalse(requireNotNull(invalidXml).isValid)
        assertEquals(StructuredTextFormat.XML, invalidXml.format)
        assertNull(
            validateStructuredTextDocument(
                text = "# plain Markdown",
                displayName = "notes.md",
                mimeType = "text/markdown"
            )
        )
    }
}
