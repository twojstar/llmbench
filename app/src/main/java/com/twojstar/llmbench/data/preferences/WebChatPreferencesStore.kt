package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.model.WebAiService

internal fun resolveWebService(id: String?): WebAiService =
    WebAiService.entries.firstOrNull { service ->
        id?.equals(service.id, ignoreCase = true) == true
    } ?: WebAiService.CLAUDE

internal fun resolveFavoriteWebServices(ids: Set<String>): Set<WebAiService> =
    WebAiService.entries.filterTo(linkedSetOf()) { service ->
        ids.any { id -> id.equals(service.id, ignoreCase = true) }
    }

internal class WebChatPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadSelectedService(): WebAiService = resolveWebService(
        preferences.getString(SELECTED_SERVICE_KEY, null)
    )

    fun saveSelectedService(service: WebAiService) {
        preferences.edit().putString(SELECTED_SERVICE_KEY, service.id).apply()
    }

    fun loadFavorites(): Set<WebAiService> = resolveFavoriteWebServices(
        preferences.getStringSet(FAVORITE_SERVICES_KEY, emptySet()).orEmpty()
    )

    fun saveFavorites(favorites: Set<WebAiService>) {
        preferences.edit()
            .putStringSet(FAVORITE_SERVICES_KEY, favorites.mapTo(linkedSetOf()) { it.id })
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "web_chat_preferences"
        const val SELECTED_SERVICE_KEY = "selected_service"
        const val FAVORITE_SERVICES_KEY = "favorite_services"
    }
}
