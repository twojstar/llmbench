package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.StudioStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StudioStateWriterTest {
    @Test
    fun latestSnapshotIsVisibleWhilePreviousDiskWriteIsBlocked() {
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = StudioStateWriter.createForTest(
            save = {
                saveStarted.countDown()
                releaseSave.await(2, TimeUnit.SECONDS)
                true
            },
            scope = scope
        )
        StudioStateWriter.replaceInstanceForTest(writer)
        try {
            val first = StudioStateSnapshot(
                baseProfile = PresetProfiles.DefaultBaseProfile,
                language = "en"
            )
            val final = first.copy(language = "pl")

            val ownerId = writer.registerOwner()
            writer.enqueue(first, ownerId)
            assertTrue(saveStarted.await(2, TimeUnit.SECONDS))

            writer.enqueue(final, ownerId)

            assertSame(final, StudioStateWriter.currentSnapshot())
        } finally {
            StudioStateWriter.replaceInstanceForTest(null)
            releaseSave.countDown()
            scope.cancel()
        }
    }

    @Test
    fun staleOwnerCannotOverwriteNewerSnapshot() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = StudioStateWriter.createForTest(save = { true }, scope = scope)
        try {
            val oldOwner = writer.registerOwner()
            val newOwner = writer.registerOwner()
            val newer = StudioStateSnapshot(PresetProfiles.DefaultBaseProfile, language = "pl")
            val stale = newer.copy(language = "en")

            writer.enqueue(newer, newOwner)
            writer.enqueue(stale, oldOwner)

            assertSame(newer, writer.currentSnapshot())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedSaveIsRetried() {
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val saved = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = StudioStateWriter.createForTest(
            save = {
                val attempt = attempts.incrementAndGet()
                if (attempt >= 2) saved.countDown()
                attempt >= 2
            },
            scope = scope
        )
        try {
            val ownerId = writer.registerOwner()
            writer.enqueue(StudioStateSnapshot(PresetProfiles.DefaultBaseProfile), ownerId)

            assertTrue(saved.await(2, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())
        } finally {
            scope.cancel()
        }
    }
}
