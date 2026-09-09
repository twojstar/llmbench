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
            detectStructuredTextFormat(null, APPLICATION_YAML)
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
            detectStructuredTextFormat(SETTINGS_YAML, TEXT_PLAIN)
        )
        assertEquals(
            StructuredTextFormat.JSON,
            detectStructuredTextFormat(SETTINGS_JSON, APPLICATION_OCTET_STREAM)
        )
    }

    @Test
    fun malformedMimeHintsRemainUnknownInsteadOfConflictingWithFileName() {
        listOf(
            "garbage/+json",
            "/+xml",
            "application/+json",
            "application/json/extra",
            "application /json"
        ).forEach { mimeType ->
            assertEquals(
                StructuredTextFormat.YAML,
                detectStructuredTextFormat(SETTINGS_YAML, mimeType),
                mimeType
            )
        }
        assertNull(detectStructuredTextFormat(null, "application/+json"))
        assertNull(detectStructuredTextFormat(null, "application/xml/extra"))
    }

    @Test
    fun conflictingSpecificHintsFailClosed() {
        assertNull(detectStructuredTextFormat(SETTINGS_JSON, "application/xml"))
        assertNull(detectStructuredTextFormat("settings.xml", APPLICATION_YAML))
    }

    @Test
    fun unsupportedOrAmbiguousNamesAreNotContentSniffed() {
        assertNull(detectStructuredTextFormat("README.md", "text/markdown"))
        assertNull(detectStructuredTextFormat("settings.jsonc", TEXT_PLAIN))
        assertNull(detectStructuredTextFormat("document", null))
        assertNull(detectStructuredTextFormat(null, APPLICATION_OCTET_STREAM))
    }

    @Test
    fun validateDocumentUsesDetectedFormatAndSkipsUnsupportedFiles() {
        val validJson = validateStructuredTextDocument(
            text = "{\"enabled\":true}",
            displayName = SETTINGS_JSON
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

    private companion object {
        const val SETTINGS_JSON = "settings.json"
        const val SETTINGS_YAML = "settings.yaml"
        const val APPLICATION_OCTET_STREAM = "application/octet-stream"
        const val APPLICATION_YAML = "application/yaml"
        const val TEXT_PLAIN = "text/plain"
    }
}
