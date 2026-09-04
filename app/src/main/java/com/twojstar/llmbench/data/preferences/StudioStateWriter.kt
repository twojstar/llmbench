package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.StudioStateSnapshot

internal class StudioStateWriter(
    private val store: StudioStateStore
) {
    fun enqueue(snapshot: StudioStateSnapshot) {
        store.save(snapshot)
    }

    fun closeWith(snapshot: StudioStateSnapshot) {
        store.save(snapshot)
    }
}
