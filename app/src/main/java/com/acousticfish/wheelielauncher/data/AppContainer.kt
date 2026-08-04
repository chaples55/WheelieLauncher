package com.acousticfish.wheelielauncher.data

import android.content.Context
import com.acousticfish.wheelielauncher.icons.IconPackRepository
import com.acousticfish.wheelielauncher.media.MediaSessionRepository
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val iconBitmapCache = IconBitmapCache()
    val artworkCache = ArtworkCache()
    val settingsRepository = SettingsRepository(appContext)
    val dockRepository = DockRepository(appContext, settingsRepository)
    val installedAppsRepository = InstalledAppsRepository(appContext, settingsRepository)
    val iconPackRepository = IconPackRepository(appContext, settingsRepository)
    val mediaSessionRepository = MediaSessionRepository(appContext, artworkCache)

    init {
        // Before UI collects settings, so a prior app-only wallpaper never flashes on upgrade.
        runBlocking { settingsRepository.clearLegacyAppWallpaperOnce() }
    }
}
