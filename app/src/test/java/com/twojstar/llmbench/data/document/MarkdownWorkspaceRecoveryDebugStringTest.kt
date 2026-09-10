package com.twojstar.llmbench.data.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownWorkspaceRecoveryDebugStringTest {
    @Test
    fun recoverySnapshotDebugStringRedactsDraftAndLocalAssetMetadata() {
        val source = MarkdownWorkspaceRecoverySource(
            localSkillName = PRIVATE_SKILL_NAME,
            sourceDigest = PRIVATE_DIGEST
        )
        val snapshot = MarkdownWorkspaceRecoverySnapshot(
            text = PRIVATE_DRAFT,
            hadUtf8Bom = true,
            displayName = PRIVATE_DISPLAY_NAME,
            isDirty = true,
            source = source
        )

        val sourceDebug = source.toString()
        val snapshotDebug = snapshot.toString()

        assertFalse(sourceDebug.contains(PRIVATE_SKILL_NAME))
        assertFalse(sourceDebug.contains(PRIVATE_DIGEST))
        assertTrue(sourceDebug.contains("<redacted>"))
        assertFalse(snapshotDebug.contains(PRIVATE_DRAFT))
        assertFalse(snapshotDebug.contains(PRIVATE_DISPLAY_NAME))
        assertFalse(snapshotDebug.contains(PRIVATE_SKILL_NAME))
        assertFalse(snapshotDebug.contains(PRIVATE_DIGEST))
        assertTrue(snapshotDebug.contains("text=<redacted>"))
        assertTrue(snapshotDebug.contains("displayName=<redacted>"))
        assertTrue(snapshotDebug.contains("hadUtf8Bom=true"))
        assertTrue(snapshotDebug.contains("isDirty=true"))
        assertTrue(snapshotDebug.contains("sourcePresent=true"))
    }

    private companion object {
        const val PRIVATE_DRAFT = "private markdown recovery draft"
        const val PRIVATE_DISPLAY_NAME = "customer-secret-notes.md"
        const val PRIVATE_SKILL_NAME = "private-skill-name"
        const val PRIVATE_DIGEST = "private-source-digest"
    }
}
