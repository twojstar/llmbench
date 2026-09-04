package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.PresetProfiles
import com.twojstar.llmbench.data.model.StudioStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

            writer.enqueue(first)
            assertTrue(saveStarted.await(2, TimeUnit.SECONDS))

            writer.enqueue(final)

            assertSame(final, StudioStateWriter.currentSnapshot())
        } finally {
            StudioStateWriter.replaceInstanceForTest(null)
            releaseSave.countDown()
            scope.cancel()
        }
    }
}
