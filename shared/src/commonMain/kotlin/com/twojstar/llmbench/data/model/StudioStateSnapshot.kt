package com.twojstar.llmbench.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val CURRENT_STUDIO_STATE_VERSION = 1

sealed class StudioStateDecodeResult {
    data class Success(val snapshot: StudioStateSnapshot) : StudioStateDecodeResult()
    data class UnsupportedVersion(val version: Int) : StudioStateDecodeResult()
    data object MissingOrInvalid : StudioStateDecodeResult()
}

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

    fun decode(raw: String?): StudioStateSnapshot? = when (val result = decodeResult(raw)) {
        is StudioStateDecodeResult.Success -> result.snapshot
        else -> null
    }

    fun decodeResult(raw: String?): StudioStateDecodeResult {
        if (raw.isNullOrBlank()) return StudioStateDecodeResult.MissingOrInvalid
        val version = runCatching {
            json.parseToJsonElement(raw).jsonObject["version"]?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: return StudioStateDecodeResult.MissingOrInvalid
        if (version != CURRENT_STUDIO_STATE_VERSION) {
            return StudioStateDecodeResult.UnsupportedVersion(version)
        }
        val snapshot = runCatching {
            json.decodeFromString<StudioStateSnapshot>(raw)
        }.getOrNull() ?: return StudioStateDecodeResult.MissingOrInvalid
        return StudioStateDecodeResult.Success(snapshot)
    }
}
