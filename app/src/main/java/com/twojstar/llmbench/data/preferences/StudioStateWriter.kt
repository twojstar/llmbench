package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.StudioStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class StudioStateWriter private constructor(
    private val save: (StudioStateSnapshot) -> Boolean,
    scope: CoroutineScope
) {
    private val snapshots = Channel<StudioStateSnapshot>(Channel.CONFLATED)
    private val latestSnapshot = AtomicReference<StudioStateSnapshot?>(null)
    private val ownerSequence = AtomicLong(0)
    private val currentOwner = AtomicLong(0)

    init {
        scope.launch {
            for (snapshot in snapshots) {
                var attempt = 0
                while (!save(snapshot) && attempt < MAX_SAVE_RETRIES) {
                    attempt += 1
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
    }

    fun registerOwner(): Long = ownerSequence.incrementAndGet().also(currentOwner::set)

    fun enqueue(snapshot: StudioStateSnapshot, ownerId: Long) {
        if (currentOwner.get() != ownerId) return
        latestSnapshot.set(snapshot)
        snapshots.trySend(snapshot)
    }

    fun currentSnapshot(): StudioStateSnapshot? = latestSnapshot.get()

    companion object {
        @Volatile
        private var instance: StudioStateWriter? = null

        fun getInstance(store: StudioStateStore, fallback: Boolean = false): StudioStateWriter =
            instance ?: synchronized(this) {
                instance ?: StudioStateWriter(
                    save = if (fallback) store::saveFallback else store::save,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                ).also { instance = it }
            }

        fun currentSnapshot(): StudioStateSnapshot? = instance?.currentSnapshot()

        internal fun createForTest(
            save: (StudioStateSnapshot) -> Boolean,
            scope: CoroutineScope
        ): StudioStateWriter = StudioStateWriter(save, scope)

        internal fun replaceInstanceForTest(writer: StudioStateWriter?) {
            instance = writer
        }

        private const val MAX_SAVE_RETRIES = 2
        private const val RETRY_DELAY_MS = 50L
    }
}
