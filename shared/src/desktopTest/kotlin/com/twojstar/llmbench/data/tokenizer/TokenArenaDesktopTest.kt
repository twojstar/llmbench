package com.twojstar.llmbench.data.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenArenaDesktopTest {
    @Test
    fun mutatingJvmGetterCopyDoesNotChangeExperimentState() {
        val first = TokenArenaVariant("first", "First", "one")
        val second = TokenArenaVariant("second", "Second", "two")
        val experiment = TokenArenaExperiment.create(
            id = "jvm-defensive-copy",
            intentLabel = "same intent",
            variants = listOf(first, second)
        )

        val exposed = experiment.variants
        val clearMethod = exposed.javaClass.getMethod("clear")
        clearMethod.invoke(exposed)

        assertTrue(exposed.isEmpty())
        assertEquals(listOf(first, second), experiment.variants)
    }
}
