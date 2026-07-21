package com.chaples55.wheelielauncher.data

import android.content.Context
import com.chaples55.wheelielauncher.icons.IconPackRepository
import com.chaples55.wheelielauncher.media.MediaSessionRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val iconBitmapCache = IconBitmapCache()
    val artworkCache = ArtworkCache()
    val settingsRepository = SettingsRepository(appContext)
    val dockRepository = DockRepository(appContext, settingsRepository)
    val installedAppsRepository = InstalledAppsRepository(appContext, settingsRepository)
    val iconPackRepository = IconPackRepository(appContext, settingsRepository)
    val mediaSessionRepository = MediaSessionRepository(appContext, artworkCache)
}
