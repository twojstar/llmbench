package com.twojstar.llmbench.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFileChooserTest {
    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }

    @Test
    fun acceptsEmptyWildcardExactAndFamilyMimeFilters() {
        assertTrue(fileChooserAcceptsMimeType(emptyArray(), null))
        assertTrue(fileChooserAcceptsMimeType(arrayOf("*/*"), null))
        assertTrue(fileChooserAcceptsMimeType(arrayOf(PDF_MIME_TYPE), PDF_MIME_TYPE))
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
        assertFalse(fileChooserAcceptsMimeType(arrayOf("image/*"), PDF_MIME_TYPE))
        assertFalse(fileChooserAcceptsMimeType(arrayOf(PDF_MIME_TYPE), null))
        assertFalse(fileChooserAcceptsMimeType(arrayOf(".pdf"), PDF_MIME_TYPE))
        assertFalse(fileChooserAcceptsMimeType(arrayOf("not-a-mime"), PDF_MIME_TYPE))
    }
}
