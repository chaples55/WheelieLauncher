package com.acousticfish.wheelielauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class DockRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        /** Max circle positions including the drawer button when shown. */
        const val MAX_DOCK_SLOTS = 11
        const val MIN_SLOTS = 3
        /** Max user apps on the circle. */
        const val MAX_APPS = 10

        private val MUSIC_PACKAGES = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
            "com.amazon.mp3",
            "com.maxmpz.audioplayer",
            "com.neutroncode.mp",
            "com.extreamsd.usbaudioplayerpro",
            "com.foobar2000.android",
            "com.tidal.android",
            "com.aspiro.tidal",
            "deezer.android.app",
            "com.pandora.android",
            "com.soundcloud.android",
            "com.qobuz.music",
        )
    }

    val dockItems: Flow<List<DockItem>> = settingsRepository.dockItemsJson.map { json ->
        decodeDock(json)
    }

    suspend fun ensureSeeded() {
        val settings = settingsRepository.settings.first()
        if (settings.dockSeeded) return
        val existing = settingsRepository.dockItemsJson.first()
        if (!existing.isNullOrBlank() && decodeDock(existing).isNotEmpty()) {
            settingsRepository.setDockSeeded(true)
            return
        }
        val seeded = buildSeedDock()
        saveDock(seeded)
        settingsRepository.setDockSeeded(true)
    }

    suspend fun saveDock(items: List<DockItem>) {
        val capped = items.take(MAX_APPS)
        settingsRepository.setDockItemsJson(encodeDock(capped))
    }

    suspend fun addToDock(componentName: ComponentName): Boolean {
        val current = dockItems.first().toMutableList()
        if (current.any { it.componentName == componentName }) return true
        if (current.size >= MAX_APPS) return false
        current.add(DockItem(componentName))
        saveDock(current)
        return true
    }

    suspend fun removeFromDock(componentName: ComponentName) {
        saveDock(dockItems.first().filterNot { it.componentName == componentName })
    }

    suspend fun moveToAngleIndex(componentName: ComponentName, targetAppIndex: Int) {
        val current = dockItems.first().toMutableList()
        val from = current.indexOfFirst { it.componentName == componentName }
        if (from < 0) return
        val item = current.removeAt(from)
        val insertAt = targetAppIndex.coerceIn(0, current.size)
        current.add(insertAt, item)
        saveDock(current)
    }

    suspend fun updateCustomIcon(componentName: ComponentName, customIcon: String?) {
        val current = dockItems.first()
        val updated = current.map { item ->
            if (item.componentName == componentName) item.copy(customIcon = customIcon) else item
        }
        if (updated != current) saveDock(updated)
    }

    suspend fun clearCustomIcons() {
        val current = dockItems.first()
        val updated = current.map { it.copy(customIcon = null) }
        if (updated != current) saveDock(updated)
    }

    private fun buildSeedDock(): List<DockItem> {
        val result = mutableListOf<DockItem>()
        resolveBrowser()?.let { result.add(DockItem(it)) }
        val music = resolveMusicApps().take(3)
        for (app in music) {
            if (result.none { it.componentName.packageName == app.packageName }) {
                result.add(DockItem(app))
            }
            if (result.size >= MAX_APPS) break
        }
        return result
    }

    private fun resolveBrowser(): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.let {
            ComponentName(it.packageName, it.name)
        } ?: launcherComponent("com.android.chrome")
    }

    private fun resolveMusicApps(): List<ComponentName> {
        val found = linkedSetOf<ComponentName>()
        for (pkg in MUSIC_PACKAGES) {
            launcherComponent(pkg)?.let { found.add(it) }
        }
        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        val resolved = context.packageManager.queryIntentActivities(musicIntent, PackageManager.MATCH_ALL)
        for (info in resolved) {
            found.add(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
        }
        return found.toList()
    }

    private fun launcherComponent(packageName: String): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val info = context.packageManager.resolveActivity(intent, 0) ?: return null
        return ComponentName(info.activityInfo.packageName, info.activityInfo.name)
    }

    private fun encodeDock(items: List<DockItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("component", item.componentName.flattenToString())
                    put("label", item.customLabel)
                    put("icon", item.customIcon)
                },
            )
        }
        return arr.toString()
    }

    private fun decodeDock(json: String?): List<DockItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val cn = ComponentName.unflattenFromString(obj.getString("component")) ?: continue
                    add(
                        DockItem(
                            componentName = cn,
                            customLabel = obj.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                            customIcon = obj.optString("icon").takeIf { it.isNotEmpty() && it != "null" },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
