package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredTextDiagnosticsTest {
    @Test
    fun validatesStrictJsonAndRejectsJsonExtensionsOrMalformedPrimitives() {
        assertTrue(
            StructuredTextDiagnostics.validate(
                "{\"name\":\"LlmBench\",\"enabled\":true}",
                StructuredTextFormat.JSON
            ).isValid
        )
        listOf(
            "{\"name\":\"LlmBench\",}",
            "{/* comment */\"name\":\"LlmBench\"}",
            "{\"enabled\":tru}",
            "{\"count\":01}",
            "{\"count\":1.}",
            "{\"count\":1e}",
            "{\"text\":\"\\u+123\"}",
            "{\"text\":\"\\u-123\"}"
        ).forEach { source ->
            assertFalse(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.JSON).isValid,
                source
            )
        }
    }

    @Test
    fun formatsOnlyValidJsonAndPreservesLargeNumericLexemes() {
        val source = "{\"n\":123456789012345678901234567890,\"items\":[1,2]}"
        val result = StructuredTextDiagnostics.formatJson(source)

        assertTrue(result.isSuccess)
        assertTrue(result.changed)
        assertTrue(result.text.contains("123456789012345678901234567890"))
        assertTrue(result.text.contains("\n"))
    }

    @Test
    fun invalidJsonFormattingLeavesSourceUntouched() {
        val source = "{\"broken\":}"
        val result = StructuredTextDiagnostics.formatJson(source)

        assertFalse(result.isSuccess)
        assertFalse(result.changed)
        assertEquals(source, result.text)
    }

    @Test
    fun duplicateObjectKeysBlockLossyFormatting() {
        val source = "{\"a\":1,\"\\u0061\":2}"
        assertTrue(StructuredTextDiagnostics.validate(source, StructuredTextFormat.JSON).isValid)

        val result = StructuredTextDiagnostics.formatJson(source)
        assertFalse(result.isSuccess)
        assertFalse(result.changed)
        assertEquals(source, result.text)
        assertTrue(result.errorMessage.orEmpty().contains("duplicate", ignoreCase = true))
    }

    @Test
    fun excessiveJsonNestingReturnsValidationErrorInsteadOfRecursingUnbounded() {
        val source = "[".repeat(256) + "0" + "]".repeat(256)
        val validation = StructuredTextDiagnostics.validate(source, StructuredTextFormat.JSON)
        val formatting = StructuredTextDiagnostics.formatJson(source)

        assertFalse(validation.isValid)
        assertTrue(validation.errorMessage.orEmpty().contains("nesting", ignoreCase = true))
        assertFalse(formatting.isSuccess)
        assertEquals(source, formatting.text)
    }

    @Test
    fun jsonFormattingStopsBeforeIndentationCanBalloonOutput() {
        val payload = List(35_000) { "0" }.joinToString(",")
        val source = "[".repeat(128) + payload + "]".repeat(128)
        val result = StructuredTextDiagnostics.formatJson(source)

        assertFalse(result.isSuccess)
        assertFalse(result.changed)
        assertEquals(source, result.text)
        assertTrue(result.errorMessage.orEmpty().contains("limit", ignoreCase = true))
    }

    @Test
    fun yamlSyntaxValidationAcceptsAnchorsEmptyDocumentsAndComplexKeys() {
        listOf(
            "name: LlmBench\nitems:\n  - one\n  - two\n",
            "base: &defaults\n  model: fast\ncopy: *defaults\n",
            "",
            "---\n...\n",
            "?\n: null-key\n",
            "? [one, two]\n: sequence-key\n",
            "? {one: 1, two: 2}\n: mapping-key\n",
            "---\nbase: &item one\ncopy: *item\n---\nbase: &item two\ncopy: *item\n"
        ).forEach { source ->
            assertTrue(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.YAML).isValid,
                source
            )
        }
    }

    @Test
    fun yamlAliasesMustReferenceAnchorsFromTheSameDocument() {
        assertFalse(
            StructuredTextDiagnostics.validate(
                "copy: *missing\n",
                StructuredTextFormat.YAML
            ).isValid
        )
        assertFalse(
            StructuredTextDiagnostics.validate(
                "---\nbase: &item one\ncopy: *item\n---\ncopy: *item\n",
                StructuredTextFormat.YAML
            ).isValid
        )
    }

    @Test
    fun malformedYamlStillFailsSyntaxValidation() {
        assertFalse(
            StructuredTextDiagnostics.validate(
                "name: \"unterminated\n",
                StructuredTextFormat.YAML
            ).isValid
        )
    }

    @Test
    fun xmlValidationAcceptsPortableWellFormedSyntax() {
        listOf(
            "<root />",
            "<?xml version=\"1.0\"?><root><child id=\"1\">text &amp; more</child></root>",
            "<!-- before --><root xmlns=\"urn:llmbench\"><![CDATA[a < b]]></root><!-- after -->",
            "<?tool preview?><root xmlns:x=\"urn:x\"><x:item /></root><?done ok?>",
            "<root>&amp;&lt;&gt;&apos;&quot;</root>"
        ).forEach { source ->
            assertTrue(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML).isValid,
                source
            )
        }
    }

    @Test
    fun malformedOrMultiRootXmlFailsValidation() {
        listOf(
            "",
            "   \n\t",
            "<root>",
            "<root><child></root>",
            "<first /><second />",
            "text-before<root />",
            "<root attr=\"unterminated",
            "<!-- unterminated",
            "<root><![CDATA[unterminated</root>"
        ).forEach { source ->
            assertFalse(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML).isValid,
                source
            )
        }
    }

    @Test
    fun xmlUnknownEntitiesAreRejectedWhilePredefinedEntitiesRemainValid() {
        assertFalse(
            StructuredTextDiagnostics.validate(
                "<root>&undeclared;</root>",
                StructuredTextFormat.XML
            ).isValid
        )
        assertTrue(
            StructuredTextDiagnostics.validate(
                "<root>&amp;&lt;&gt;&apos;&quot;</root>",
                StructuredTextFormat.XML
            ).isValid
        )
    }

    @Test
    fun xmlDuplicateAttributesAreRejectedByExpandedName() {
        listOf(
            "<root id=\"1\" id=\"2\" />",
            "<root xmlns:x=\"urn:id\" xmlns:y=\"urn:id\" x:id=\"1\" y:id=\"2\" />",
            "<root xmlns:x=\"urn:a\" xmlns:x=\"urn:b\" />"
        ).forEach { source ->
            assertFalse(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML).isValid,
                source
            )
        }
    }

    @Test
    fun xmlInvalidCharactersAndReferencesAreRejected() {
        listOf(
            "<root>\u0001</root>",
            "<root>\uD800</root>",
            "<root>&#0;</root>",
            "<root>&#x1F;</root>",
            "<root attr=\"&#0;\" />"
        ).forEach { source ->
            assertFalse(
                StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML).isValid,
                source
            )
        }
    }

    @Test
    fun xmlDoctypeIsRejectedWithoutEntityExpansion() {
        val source = "<!DOCTYPE root [<!ENTITY secret \"value\">]><root>&secret;</root>"
        val result = StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML)

        assertFalse(result.isValid)
        assertTrue(result.errorMessage.orEmpty().contains("DOCTYPE", ignoreCase = true))
    }

    @Test
    fun excessiveXmlNestingReturnsValidationError() {
        val source = "<n>".repeat(129) + "x" + "</n>".repeat(129)
        val result = StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML)

        assertFalse(result.isValid)
        assertTrue(result.errorMessage.orEmpty().contains("nesting", ignoreCase = true))
    }

    @Test
    fun oversizedXmlIsRejectedBeforeParsing() {
        val source = "<root>" + "x".repeat(3 * 1024 * 1024) + "</root>"
        val result = StructuredTextDiagnostics.validate(source, StructuredTextFormat.XML)

        assertFalse(result.isValid)
        assertTrue(result.errorMessage.orEmpty().contains("limit", ignoreCase = true))
    }
}
