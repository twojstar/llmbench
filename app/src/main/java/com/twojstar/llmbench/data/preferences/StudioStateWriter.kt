package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.StudioStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class StudioStateWriter(
    private val store: StudioStateStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshots = Channel<StudioStateSnapshot>(Channel.CONFLATED)

    init {
        scope.launch {
            for (snapshot in snapshots) {
                store.save(snapshot)
            }
        }
    }

    fun enqueue(snapshot: StudioStateSnapshot) {
        snapshots.trySend(snapshot)
    }

    fun closeWith(snapshot: StudioStateSnapshot) {
        snapshots.trySend(snapshot)
        snapshots.close()
    }
}
