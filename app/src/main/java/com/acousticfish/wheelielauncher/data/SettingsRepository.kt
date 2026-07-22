package com.acousticfish.wheelielauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wheelie_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val dockShowLabels = booleanPreferencesKey("dock_show_labels")
        val drawerShowLabels = booleanPreferencesKey("drawer_show_labels")
        val dockIconSize = floatPreferencesKey("dock_icon_size")
        val drawerIconSize = floatPreferencesKey("drawer_icon_size")
        val drawerColumns = intPreferencesKey("drawer_columns")
        val drawerShowSearch = booleanPreferencesKey("drawer_show_search")
        val showStatusBar = booleanPreferencesKey("show_status_bar")
        val statusBarScrim = floatPreferencesKey("status_bar_scrim")
        val wallpaperUri = stringPreferencesKey("wallpaper_uri")
        val iconPack = stringPreferencesKey("icon_pack")
        val hiddenPackages = stringSetPreferencesKey("hidden_packages")
        val customizations = stringPreferencesKey("customizations_json")
        val dockItems = stringPreferencesKey("dock_items_json")
        val dockSeeded = booleanPreferencesKey("dock_seeded")
        val onboardingHome = booleanPreferencesKey("onboarding_home")
        val onboardingMedia = booleanPreferencesKey("onboarding_media")
        val nowPlayingSize = floatPreferencesKey("now_playing_size")
        val dockRingRadius = floatPreferencesKey("dock_ring_radius")
        val swipeUpToOpenDrawer = booleanPreferencesKey("swipe_up_open_drawer")
        val hideDrawerButton = booleanPreferencesKey("hide_drawer_button")
        val swipeSensitivity = floatPreferencesKey("swipe_sensitivity")
        val showClock = booleanPreferencesKey("show_clock")
        val showEqButton = booleanPreferencesKey("show_eq_button")
        val eqAppComponent = stringPreferencesKey("eq_app_component")
        val showSkipButtons = booleanPreferencesKey("show_skip_buttons")
        val drawerBackgroundOpacity = floatPreferencesKey("drawer_bg_opacity")
        val progressBarThickness = floatPreferencesKey("progress_bar_thickness")
        val showBatteryBar = booleanPreferencesKey("show_battery_bar")
        val showTrackInfo = booleanPreferencesKey("show_track_info")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            dockShowLabels = prefs[Keys.dockShowLabels] ?: false,
            drawerShowLabels = prefs[Keys.drawerShowLabels] ?: true,
            dockIconSizeDp = prefs[Keys.dockIconSize] ?: 48f,
            drawerIconSizeDp = prefs[Keys.drawerIconSize] ?: 48f,
            drawerColumns = prefs[Keys.drawerColumns] ?: 4,
            drawerShowSearch = prefs[Keys.drawerShowSearch] ?: true,
            showStatusBar = prefs[Keys.showStatusBar] ?: true,
            statusBarScrimOpacity = prefs[Keys.statusBarScrim] ?: 0.4f,
            defaultWallpaperUri = prefs[Keys.wallpaperUri],
            iconPackPackage = prefs[Keys.iconPack],
            hiddenPackages = prefs[Keys.hiddenPackages] ?: emptySet(),
            customizations = decodeCustomizations(prefs[Keys.customizations]),
            dockSeeded = prefs[Keys.dockSeeded] ?: false,
            onboardingHomeDone = prefs[Keys.onboardingHome] ?: false,
            onboardingMediaDone = prefs[Keys.onboardingMedia] ?: false,
            nowPlayingSizeDp = prefs[Keys.nowPlayingSize] ?: 120f,
            dockRingRadiusFraction = prefs[Keys.dockRingRadius] ?: 0.78f,
            swipeUpToOpenDrawer = prefs[Keys.swipeUpToOpenDrawer] ?: false,
            hideDrawerButton = prefs[Keys.hideDrawerButton] ?: false,
            swipeSensitivity = (prefs[Keys.swipeSensitivity] ?: 1f).coerceIn(0.25f, 2f),
            showClock = prefs[Keys.showClock] ?: true,
            showEqButton = prefs[Keys.showEqButton] ?: false,
            eqAppComponent = prefs[Keys.eqAppComponent],
            showSkipButtons = prefs[Keys.showSkipButtons] ?: false,
            drawerBackgroundOpacity = (prefs[Keys.drawerBackgroundOpacity] ?: 0.45f).coerceIn(0f, 1f),
            progressBarThicknessDp = (prefs[Keys.progressBarThickness] ?: 4f).coerceIn(2f, 14f),
            showBatteryBar = prefs[Keys.showBatteryBar] ?: true,
            showTrackInfo = prefs[Keys.showTrackInfo] ?: true,
        )
    }

    val dockItemsJson: Flow<String?> = context.dataStore.data.map { it[Keys.dockItems] }

    suspend fun setDockShowLabels(value: Boolean) = edit { it[Keys.dockShowLabels] = value }
    suspend fun setDrawerShowLabels(value: Boolean) = edit { it[Keys.drawerShowLabels] = value }
    suspend fun setDockIconSize(value: Float) = edit { it[Keys.dockIconSize] = value }
    suspend fun setDrawerIconSize(value: Float) = edit { it[Keys.drawerIconSize] = value }
    suspend fun setDrawerColumns(value: Int) = edit { it[Keys.drawerColumns] = value.coerceIn(2, 6) }
    suspend fun setDrawerShowSearch(value: Boolean) = edit { it[Keys.drawerShowSearch] = value }
    suspend fun setShowStatusBar(value: Boolean) = edit { it[Keys.showStatusBar] = value }
    suspend fun setStatusBarScrim(value: Float) = edit { it[Keys.statusBarScrim] = value.coerceIn(0f, 1f) }
    suspend fun setWallpaperUri(uri: String?) = edit {
        if (uri == null) it.remove(Keys.wallpaperUri) else it[Keys.wallpaperUri] = uri
    }
    suspend fun setIconPack(packageName: String?) = edit {
        if (packageName == null) it.remove(Keys.iconPack) else it[Keys.iconPack] = packageName
    }
    suspend fun setHiddenPackages(packages: Set<String>) = edit { it[Keys.hiddenPackages] = packages }
    suspend fun hidePackage(packageName: String) = edit {
        val current = it[Keys.hiddenPackages] ?: emptySet()
        it[Keys.hiddenPackages] = current + packageName
    }
    suspend fun unhidePackage(packageName: String) = edit {
        val current = it[Keys.hiddenPackages] ?: emptySet()
        it[Keys.hiddenPackages] = current - packageName
    }
    suspend fun setCustomization(componentKey: String, customization: AppCustomization?) = edit {
        val map = decodeCustomizations(it[Keys.customizations]).toMutableMap()
        if (customization == null || (customization.customLabel == null && customization.customIcon == null)) {
            map.remove(componentKey)
        } else {
            map[componentKey] = customization
        }
        it[Keys.customizations] = encodeCustomizations(map)
    }
    suspend fun setDockItemsJson(json: String) = edit { it[Keys.dockItems] = json }
    suspend fun setDockSeeded(value: Boolean) = edit { it[Keys.dockSeeded] = value }
    suspend fun setOnboardingHomeDone(value: Boolean) = edit { it[Keys.onboardingHome] = value }
    suspend fun setOnboardingMediaDone(value: Boolean) = edit { it[Keys.onboardingMedia] = value }
    suspend fun setNowPlayingSize(value: Float) = edit { it[Keys.nowPlayingSize] = value.coerceIn(72f, 220f) }
    suspend fun setDockRingRadius(value: Float) = edit { it[Keys.dockRingRadius] = value.coerceIn(0.45f, 0.92f) }
    suspend fun setSwipeUpToOpenDrawer(value: Boolean) = edit {
        it[Keys.swipeUpToOpenDrawer] = value
        if (!value) it[Keys.hideDrawerButton] = false
    }
    suspend fun setHideDrawerButton(value: Boolean) = edit {
        it[Keys.hideDrawerButton] = value
    }
    suspend fun setSwipeSensitivity(value: Float) = edit {
        it[Keys.swipeSensitivity] = value.coerceIn(0.25f, 2f)
    }
    suspend fun setShowClock(value: Boolean) = edit { it[Keys.showClock] = value }
    suspend fun setShowEqButton(value: Boolean) = edit { it[Keys.showEqButton] = value }
    suspend fun setEqAppComponent(value: String?) = edit {
        if (value.isNullOrBlank()) it.remove(Keys.eqAppComponent) else it[Keys.eqAppComponent] = value
    }
    suspend fun setShowSkipButtons(value: Boolean) = edit { it[Keys.showSkipButtons] = value }
    suspend fun setDrawerBackgroundOpacity(value: Float) = edit {
        it[Keys.drawerBackgroundOpacity] = value.coerceIn(0f, 1f)
    }
    suspend fun setProgressBarThickness(value: Float) = edit {
        it[Keys.progressBarThickness] = value.coerceIn(2f, 14f)
    }
    suspend fun setShowBatteryBar(value: Boolean) = edit { it[Keys.showBatteryBar] = value }
    suspend fun setShowTrackInfo(value: Boolean) = edit { it[Keys.showTrackInfo] = value }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        fun encodeCustomizations(map: Map<String, AppCustomization>): String {
            val obj = JSONObject()
            map.forEach { (key, value) ->
                obj.put(key, JSONObject().apply {
                    put("label", value.customLabel)
                    put("icon", value.customIcon)
                })
            }
            return obj.toString()
        }

        fun decodeCustomizations(json: String?): Map<String, AppCustomization> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { key ->
                        val item = obj.getJSONObject(key)
                        put(
                            key,
                            AppCustomization(
                                customLabel = item.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                                customIcon = item.optString("icon").takeIf { it.isNotEmpty() && it != "null" },
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
}
