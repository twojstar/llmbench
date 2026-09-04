package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.model.StudioStateCodec
import com.twojstar.llmbench.data.model.StudioStateDecodeResult
import com.twojstar.llmbench.data.model.StudioStateSnapshot

internal class StudioStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): StudioStateDecodeResult = StudioStateCodec.decodeResult(
        preferences.getString(SNAPSHOT_KEY, null)
    )

    fun save(snapshot: StudioStateSnapshot): Boolean = runCatching {
        preferences.edit()
            .putString(SNAPSHOT_KEY, StudioStateCodec.encode(snapshot))
            .commit()
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "studio_state"
        const val SNAPSHOT_KEY = "snapshot_v1"
    }
}
