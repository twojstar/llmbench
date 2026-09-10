package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.BuiltInBenchTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInBenchPreferencesStoreTest {
    @Test
    fun enabledToolIdsResolveCaseInsensitivelyInCanonicalRegistryOrder() {
        assertEquals(
            linkedSetOf(
                BuiltInBenchTool.DOCBENCH_DOCUMENT,
                BuiltInBenchTool.CODEBENCH_QR_BARCODE
            ),
            resolveEnabledBuiltInBenchTools(
                setOf("CODEBENCH-QR-BARCODE", "docbench-document", "retired-tool")
            )
        )
    }

    @Test
    fun missingOrUnknownPreferenceStateEnablesNothing() {
        assertTrue(resolveEnabledBuiltInBenchTools(null).isEmpty())
        assertTrue(resolveEnabledBuiltInBenchTools(emptySet()).isEmpty())
        assertTrue(resolveEnabledBuiltInBenchTools(setOf("retired-tool")).isEmpty())
    }

    @Test
    fun stableToolIdsAreUniqueAndUnknownIdsFailClosed() {
        val ids = BuiltInBenchTool.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR, BuiltInBenchTool.fromId("DOCBENCH-TEXT-INSPECTOR"))
        assertNull(BuiltInBenchTool.fromId("retired-tool"))
        assertNull(BuiltInBenchTool.fromId(null))
    }
}
