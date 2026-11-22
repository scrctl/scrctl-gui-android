package org.scrctl.android.gui.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Application preferences stored outside the encrypted connection library. */
class SettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("openscreens.settings", Context.MODE_PRIVATE)

    private fun toolbarItems() = SessionToolbarItem.decode(preferences.getString("toolbarItems", null))

    fun observeToolbarItems() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "toolbarItems" || key == null) trySend(toolbarItems())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(toolbarItems())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun load(): AppSettings = AppSettings(
        themeMode = enumValueOrDefault(preferences.getString("themeMode", null), ThemeMode.System),
        updateChannel = enumValueOrDefault(
            preferences.getString("updateChannel", null),
            UpdateChannel.Release,
        ),
        librarySort = enumValueOrDefault(
            preferences.getString("librarySort", null),
            LibrarySortOrder.Az,
        ),
        discoverNearby = preferences.getBoolean("discoverNearby", true),
        showSessionPreviews = preferences.getBoolean("showSessionPreviews", true),
        toolbarItems = toolbarItems(),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString("themeMode", settings.themeMode.name)
            .putString("updateChannel", settings.updateChannel.name)
            .putString("librarySort", settings.librarySort.name)
            .putBoolean("discoverNearby", settings.discoverNearby)
            .putBoolean("showSessionPreviews", settings.showSessionPreviews)
            .putString("toolbarItems", settings.toolbarItems.joinToString(",") { it.id })
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        runCatching { enumValueOf<T>(value ?: default.name) }.getOrDefault(default)
}
