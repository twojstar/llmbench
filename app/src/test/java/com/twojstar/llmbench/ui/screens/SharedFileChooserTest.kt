package com.twojstar.llmbench.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFileChooserTest {
    @Test
    fun acceptsEmptyWildcardExactAndFamilyMimeFilters() {
        assertTrue(fileChooserAcceptsMimeType(emptyArray(), null))
        assertTrue(fileChooserAcceptsMimeType(arrayOf("*/*"), null))
        assertTrue(fileChooserAcceptsMimeType(arrayOf("application/pdf"), "application/pdf"))
        assertTrue(fileChooserAcceptsMimeType(arrayOf("image/*"), "image/png"))
        assertTrue(
            fileChooserAcceptsMimeType(
                arrayOf("image/*, application/pdf"),
                "application/pdf; charset=binary"
            )
        )
    }

    @Test
    fun rejectsMismatchesUnknownTypesAndExtensionOnlyFilters() {
        assertFalse(fileChooserAcceptsMimeType(arrayOf("image/*"), "application/pdf"))
        assertFalse(fileChooserAcceptsMimeType(arrayOf("application/pdf"), null))
        assertFalse(fileChooserAcceptsMimeType(arrayOf(".pdf"), "application/pdf"))
        assertFalse(fileChooserAcceptsMimeType(arrayOf("not-a-mime"), "application/pdf"))
    }
}
