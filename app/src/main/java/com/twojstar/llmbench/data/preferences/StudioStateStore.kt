package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.model.Profile
import com.twojstar.llmbench.data.model.ProfileOverlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CURRENT_STUDIO_STATE_VERSION = 1

@Serializable
internal data class StudioStateSnapshot(
    val version: Int = CURRENT_STUDIO_STATE_VERSION,
    val baseProfile: Profile,
    val selectedOverlayId: String? = null,
    val customOverlays: List<ProfileOverlay> = emptyList(),
    val language: String = "auto"
)

internal object StudioStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: StudioStateSnapshot): String = json.encodeToString(snapshot)

    fun decode(raw: String?): StudioStateSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<StudioStateSnapshot>(raw) }.getOrNull()
    }
}

internal class StudioStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): StudioStateSnapshot? = StudioStateCodec.decode(
        preferences.getString(SNAPSHOT_KEY, null)
    )

    fun save(snapshot: StudioStateSnapshot): Boolean = runCatching {
        preferences.edit()
            .putString(SNAPSHOT_KEY, StudioStateCodec.encode(snapshot))
            .apply()
        true
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "studio_state"
        const val SNAPSHOT_KEY = "snapshot_v1"
    }
}
