package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.model.StudioStateCodec
import com.twojstar.llmbench.data.model.StudioStateDecodeResult
import com.twojstar.llmbench.data.model.StudioStateSnapshot

internal class StudioStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): StudioStateDecodeResult = loadFrom(SNAPSHOT_KEY)

    fun loadFallback(): StudioStateDecodeResult = loadFrom(FALLBACK_SNAPSHOT_KEY)

    fun save(snapshot: StudioStateSnapshot): Boolean = saveTo(SNAPSHOT_KEY, snapshot)

    fun saveFallback(snapshot: StudioStateSnapshot): Boolean = saveTo(FALLBACK_SNAPSHOT_KEY, snapshot)

    private fun loadFrom(key: String): StudioStateDecodeResult = StudioStateCodec.decodeResult(
        preferences.getString(key, null)
    )

    private fun saveTo(key: String, snapshot: StudioStateSnapshot): Boolean = runCatching {
        preferences.edit()
            .putString(key, StudioStateCodec.encode(snapshot))
            .commit()
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "studio_state"
        const val SNAPSHOT_KEY = "snapshot_v1"
        const val FALLBACK_SNAPSHOT_KEY = "snapshot_fallback_v1"
    }
}
