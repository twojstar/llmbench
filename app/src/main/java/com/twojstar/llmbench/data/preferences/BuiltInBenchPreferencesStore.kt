package com.twojstar.llmbench.data.preferences

import android.content.Context
import com.twojstar.llmbench.data.model.BuiltInBenchTool

/** Resolve persisted tool ids into the canonical registry order, ignoring unknown values safely. */
internal fun resolveEnabledBuiltInBenchTools(ids: Set<String>?): Set<BuiltInBenchTool> =
    BuiltInBenchTool.entries.filterTo(linkedSetOf()) { tool ->
        ids.orEmpty().any { id -> id.equals(tool.id, ignoreCase = true) }
    }

/**
 * Device-local global enablement for first-party Bench tools.
 *
 * The empty/default state enables nothing. This preference file is intentionally not part of the
 * app's explicit Android backup/device-transfer allowlist; callers still need per-action permission
 * scopes and availability checks after a tool has been enabled.
 */
internal class BuiltInBenchPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadEnabledTools(): Set<BuiltInBenchTool> = resolveEnabledBuiltInBenchTools(
        preferences.getStringSet(ENABLED_TOOLS_KEY, emptySet())
    )

    fun saveEnabledTools(enabledTools: Set<BuiltInBenchTool>) {
        preferences.edit()
            .putStringSet(ENABLED_TOOLS_KEY, enabledTools.mapTo(linkedSetOf()) { it.id })
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "built_in_bench_preferences"
        const val ENABLED_TOOLS_KEY = "enabled_tools"
    }
}
