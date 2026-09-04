package com.twojstar.llmbench.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CURRENT_STUDIO_STATE_VERSION = 1

@Serializable
data class StudioStateSnapshot(
    val version: Int = CURRENT_STUDIO_STATE_VERSION,
    val baseProfile: Profile,
    val selectedBuiltInOverlayId: String? = null,
    val selectedCustomOverlayIndex: Int? = null,
    val customOverlays: List<ProfileOverlay> = emptyList(),
    val language: String = "auto"
)

object StudioStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: StudioStateSnapshot): String = json.encodeToString(snapshot)

    fun decode(raw: String?): StudioStateSnapshot? {
        if (raw.isNullOrBlank()) return null
        val snapshot = runCatching {
            json.decodeFromString<StudioStateSnapshot>(raw)
        }.getOrNull() ?: return null
        return snapshot.takeIf { it.version == CURRENT_STUDIO_STATE_VERSION }
    }
}
