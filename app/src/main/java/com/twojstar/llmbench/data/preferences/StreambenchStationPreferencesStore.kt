package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.streambench.StreambenchM3uParser
import com.twojstar.llmbench.data.streambench.StreambenchPlaylistEntry
import java.security.MessageDigest

private const val SHA256_HEX_LENGTH = 64
private const val HEX_DIGITS = "0123456789abcdef"
internal const val STREAMBENCH_MAX_RECENTS = 30

internal fun streambenchStationKey(entry: StreambenchPlaylistEntry): String {
    val source = "${entry.providerId}\u0000${entry.url}"
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
    return CharArray(digest.size * 2).also { output ->
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            output[index * 2] = HEX_DIGITS[value ushr 4]
            output[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
    }.concatToString()
}

internal fun resolveStreambenchFavoriteKeys(values: Set<String>): Set<String> =
    values.asSequence()
        .filter(::isStreambenchStationKey)
        .take(StreambenchM3uParser.MAX_ENTRIES)
        .toCollection(linkedSetOf())

internal fun decodeStreambenchRecentKeys(value: String?): List<String> =
    value.orEmpty()
        .split(',')
        .asSequence()
        .filter(::isStreambenchStationKey)
        .distinct()
        .take(STREAMBENCH_MAX_RECENTS)
        .toList()

private fun isStreambenchStationKey(value: String): Boolean =
    value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

/** Local-only Streambench UI preferences. This file is intentionally absent from Android backup allowlists. */
internal class StreambenchStationPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadFavoriteKeys(): Set<String> = resolveStreambenchFavoriteKeys(
        preferences.getStringSet(FAVORITES_KEY, emptySet()).orEmpty()
    )

    fun toggleFavorite(key: String): Set<String> {
        if (!isStreambenchStationKey(key)) return loadFavoriteKeys()
        val updated = loadFavoriteKeys().toMutableSet().apply {
            if (!remove(key) && size < StreambenchM3uParser.MAX_ENTRIES) add(key)
        }
        preferences.edit().putStringSet(FAVORITES_KEY, updated).apply()
        return updated
    }

    fun loadRecentKeys(): List<String> = decodeStreambenchRecentKeys(
        preferences.getString(RECENTS_KEY, null)
    )

    fun recordRecent(key: String): List<String> {
        if (!isStreambenchStationKey(key)) return loadRecentKeys()
        val updated = buildList {
            add(key)
            addAll(loadRecentKeys().filterNot { it == key })
        }.take(STREAMBENCH_MAX_RECENTS)
        preferences.edit().putString(RECENTS_KEY, updated.joinToString(",")).apply()
        return updated
    }

    private companion object {
        const val PREFERENCES_NAME = "streambench_station_preferences"
        const val FAVORITES_KEY = "favorite_station_keys"
        const val RECENTS_KEY = "recent_station_keys"
    }
}
