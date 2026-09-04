package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.StudioStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

internal class StudioStateWriter private constructor(
    private val save: (StudioStateSnapshot) -> Boolean,
    scope: CoroutineScope
) {
    private val snapshots = Channel<StudioStateSnapshot>(Channel.CONFLATED)
    private val latestSnapshot = AtomicReference<StudioStateSnapshot?>(null)

    init {
        scope.launch {
            for (snapshot in snapshots) {
                save(snapshot)
            }
        }
    }

    fun enqueue(snapshot: StudioStateSnapshot) {
        latestSnapshot.set(snapshot)
        snapshots.trySend(snapshot)
    }

    fun currentSnapshot(): StudioStateSnapshot? = latestSnapshot.get()

    companion object {
        @Volatile
        private var instance: StudioStateWriter? = null

        fun getInstance(store: StudioStateStore): StudioStateWriter = instance ?: synchronized(this) {
            instance ?: StudioStateWriter(
                save = store::save,
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
    }
}
