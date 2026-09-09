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
            "{\"count\":1e}"
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
    fun validatesYamlWithoutAttemptingHeuristicRepair() {
        assertTrue(
            StructuredTextDiagnostics.validate(
                "name: LlmBench\nitems:\n  - one\n  - two\n",
                StructuredTextFormat.YAML
            ).isValid
        )
        assertFalse(
            StructuredTextDiagnostics.validate(
                "name: \"unterminated\n",
                StructuredTextFormat.YAML
            ).isValid
        )
    }
}
