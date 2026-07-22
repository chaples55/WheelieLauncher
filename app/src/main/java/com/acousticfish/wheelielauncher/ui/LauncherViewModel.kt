package com.acousticfish.wheelielauncher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.acousticfish.wheelielauncher.data.AppContainer
import com.acousticfish.wheelielauncher.data.AppCustomization
import com.acousticfish.wheelielauncher.data.DockItem
import com.acousticfish.wheelielauncher.data.DockRepository
import com.acousticfish.wheelielauncher.data.IconBitmapCache
import com.acousticfish.wheelielauncher.data.LauncherApp
import com.acousticfish.wheelielauncher.data.LauncherSettings
import com.acousticfish.wheelielauncher.data.NowPlayingMeta
import com.acousticfish.wheelielauncher.data.PlaybackProgress
import com.acousticfish.wheelielauncher.data.SettingsRepository
import com.acousticfish.wheelielauncher.data.key
import com.acousticfish.wheelielauncher.icons.IconPackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val dockItems: List<DockItem> = emptyList(),
    val apps: List<LauncherApp> = emptyList(),
    val drawerOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val selectedDockIndex: Int = 0,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val showOnboardingHome: Boolean = false,
    val showOnboardingMedia: Boolean = false,
    /** packageName -> label for dock resolve without scanning apps each frame. */
    val appLabelsByComponent: Map<String, String> = emptyMap(),
)

class LauncherViewModel(private val container: AppContainer) : ViewModel() {
    private val drawerOpen = MutableStateFlow(false)
    private val settingsOpen = MutableStateFlow(false)
    private val selectedDockIndex = MutableStateFlow(0)
    private val showOnboardingHome = MutableStateFlow(false)
    private val showOnboardingMedia = MutableStateFlow(false)
    private val iconDensity = MutableStateFlow(3f)

    private data class NavState(
        val drawerOpen: Boolean,
        val settingsOpen: Boolean,
        val selectedDockIndex: Int,
        val showOnboardingHome: Boolean,
        val showOnboardingMedia: Boolean,
    )

    private val navState = combine(
        drawerOpen,
        settingsOpen,
        selectedDockIndex,
        showOnboardingHome,
        showOnboardingMedia,
    ) { d, s, i, h, m ->
        NavState(d, s, i, h, m)
    }

    private data class CoreState(
        val settings: LauncherSettings,
        val dockItems: List<DockItem>,
        val apps: List<LauncherApp>,
        val iconPacks: List<IconPackInfo>,
    )

    private val coreState = combine(
        container.settingsRepository.settings,
        container.dockRepository.dockItems,
        container.installedAppsRepository.apps(),
        container.iconPackRepository.installedPacks(),
    ) { settings, dock, apps, packs ->
        CoreState(settings, dock, apps, packs)
    }

    val uiState: StateFlow<HomeUiState> = combine(coreState, navState) { core, nav ->
        HomeUiState(
            settings = core.settings,
            dockItems = core.dockItems,
            apps = core.apps,
            drawerOpen = nav.drawerOpen,
            settingsOpen = nav.settingsOpen,
            selectedDockIndex = nav.selectedDockIndex,
            iconPacks = core.iconPacks,
            showOnboardingHome = nav.showOnboardingHome,
            showOnboardingMedia = nav.showOnboardingMedia,
            appLabelsByComponent = core.apps.associate { it.componentName.key() to it.label },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val nowPlayingMeta: StateFlow<NowPlayingMeta> = container.mediaSessionRepository.nowPlayingMeta
    val playbackProgress: StateFlow<PlaybackProgress> = container.mediaSessionRepository.progress

    init {
        viewModelScope.launch {
            container.installedAppsRepository.refresh()
            container.dockRepository.ensureSeeded()
        }
        container.mediaSessionRepository.start()
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                if (!settings.onboardingHomeDone) {
                    showOnboardingHome.value = true
                } else if (!settings.onboardingMediaDone) {
                    showOnboardingMedia.value = !settings.onboardingMediaDone
                }
            }
        }
        viewModelScope.launch {
            var first = true
            container.settingsRepository.settings
                .map { it.iconPackPackage }
                .distinctUntilChanged()
                .collect {
                    if (!first) {
                        container.iconBitmapCache.clear()
                        container.settingsRepository.clearCustomIcons()
                        container.dockRepository.clearCustomIcons()
                    }
                    first = false
                }
        }
        viewModelScope.launch {
            combine(
                uiState.map {
                    PreloadRequest(
                        apps = it.apps,
                        drawerSizeDp = it.settings.drawerIconSizeDp,
                        dockSizeDp = it.settings.dockIconSizeDp,
                        density = 0f, // filled below
                        iconPackPackage = it.settings.iconPackPackage,
                    )
                },
                iconDensity,
            ) { req, density ->
                req.copy(density = density)
            }
                .distinctUntilChanged()
                .collect { req ->
                    if (req.apps.isNotEmpty() && req.density > 0f) {
                        preloadIcons(
                            apps = req.apps,
                            drawerSizeDp = req.drawerSizeDp,
                            dockSizeDp = req.dockSizeDp,
                            density = req.density,
                            iconPackPackage = req.iconPackPackage,
                        )
                    }
                }
        }
    }

    private data class PreloadRequest(
        val apps: List<LauncherApp>,
        val drawerSizeDp: Float,
        val dockSizeDp: Float,
        val density: Float,
        val iconPackPackage: String?,
    )

    override fun onCleared() {
        container.mediaSessionRepository.stop()
        super.onCleared()
    }

    fun setIconDensity(density: Float) {
        if (density > 0f && iconDensity.value != density) {
            iconDensity.value = density
        }
    }

    fun openDrawer() {
        drawerOpen.value = true
    }

    fun closeDrawer() {
        drawerOpen.value = false
    }

    fun openSettings() {
        settingsOpen.value = true
    }

    fun closeSettings() {
        settingsOpen.value = false
    }

    fun onHomePressed() {
        drawerOpen.value = false
        settingsOpen.value = false
    }

    fun setSelectedDockIndex(index: Int) {
        selectedDockIndex.value = index
    }

    fun launch(componentName: ComponentName) {
        container.installedAppsRepository.launch(componentName)
    }

    fun togglePlayPause() {
        container.mediaSessionRepository.togglePlayPause()
    }

    fun skipToNext() {
        container.mediaSessionRepository.skipToNext()
    }

    fun skipToPrevious() {
        container.mediaSessionRepository.skipToPrevious()
    }

    fun openNowPlayingApp() {
        container.mediaSessionRepository.openSessionApp()
    }

    fun launchEqApp() {
        val key = uiState.value.settings.eqAppComponent ?: return
        val cn = ComponentName.unflattenFromString(key) ?: return
        launch(cn)
    }

    fun artworkBitmap(): Bitmap? = container.mediaSessionRepository.currentArtworkBitmap()

    fun blurredWallpaperBitmap(artworkBitmapKey: String?): Bitmap? {
        if (artworkBitmapKey.isNullOrBlank()) return null
        return container.artworkCache.getBlurred(artworkBitmapKey)
    }

    suspend fun ensureBlurredWallpaper(artworkBitmapKey: String, source: Bitmap): Bitmap? {
        return container.artworkCache.getOrCreateBlurred(artworkBitmapKey, source)
    }

    fun peekIconBitmap(componentName: ComponentName, customIcon: String?, sizePx: Int): Bitmap? {
        val settings = uiState.value.settings
        val custom = customIcon ?: settings.customizations[componentName.key()]?.customIcon
        return container.iconBitmapCache.get(
            IconBitmapCache.key(componentName, custom, sizePx, settings.iconPackPackage),
        )
    }

    suspend fun resolveIcon(componentName: ComponentName, customIcon: String? = null): Drawable? {
        val settings = uiState.value.settings
        val custom = customIcon ?: settings.customizations[componentName.key()]?.customIcon
        return container.iconPackRepository.resolveIcon(componentName, custom)
    }

    suspend fun cachedIconBitmap(
        componentName: ComponentName,
        customIcon: String?,
        sizePx: Int,
    ): Bitmap? {
        val settings = uiState.value.settings
        val custom = customIcon ?: settings.customizations[componentName.key()]?.customIcon
        val key = IconBitmapCache.key(componentName, custom, sizePx, settings.iconPackPackage)
        return container.iconBitmapCache.getOrLoad(key, sizePx) {
            container.iconPackRepository.resolveIcon(componentName, custom)
        }
    }

    private fun preloadIcons(
        apps: List<LauncherApp>,
        drawerSizeDp: Float,
        dockSizeDp: Float,
        density: Float,
        iconPackPackage: String?,
    ) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val sizeDps = listOf(drawerSizeDp, dockSizeDp).distinct()
            for (dp in sizeDps) {
                val sizePx = (dp * density).toInt().coerceIn(48, 512)
                val loaders = apps.map { app ->
                    val custom = settings.customizations[app.componentName.key()]?.customIcon
                    val key = IconBitmapCache.key(app.componentName, custom, sizePx, iconPackPackage)
                    key to suspend {
                        container.iconPackRepository.resolveIcon(app.componentName, custom)
                    }
                }
                container.iconBitmapCache.preload(loaders, sizePx)
            }
        }
    }

    fun addToDock(componentName: ComponentName, onFull: () -> Unit) {
        viewModelScope.launch {
            val ok = container.dockRepository.addToDock(componentName)
            if (!ok) onFull()
        }
    }

    fun removeFromDock(componentName: ComponentName) {
        viewModelScope.launch { container.dockRepository.removeFromDock(componentName) }
    }

    fun reorderDock(from: Int, to: Int) {
        viewModelScope.launch { container.dockRepository.reorder(from, to) }
    }

    fun moveDockItem(componentName: ComponentName, targetAppIndex: Int) {
        viewModelScope.launch { container.dockRepository.moveToAngleIndex(componentName, targetAppIndex) }
    }

    fun hideApp(packageName: String) {
        viewModelScope.launch { container.settingsRepository.hidePackage(packageName) }
    }

    fun unhideApp(packageName: String) {
        viewModelScope.launch { container.settingsRepository.unhidePackage(packageName) }
    }

    fun setCustomization(componentName: ComponentName, customization: AppCustomization?) {
        viewModelScope.launch {
            container.settingsRepository.setCustomization(componentName.key(), customization)
            container.dockRepository.updateCustomIcon(componentName, customization?.customIcon)
            // Drop stale cache entries for this component so dock/drawer reload immediately.
            container.iconBitmapCache.clear()
        }
    }

    fun openAppInfo(packageName: String) = container.installedAppsRepository.openAppInfo(packageName)
    fun uninstall(packageName: String) = container.installedAppsRepository.uninstall(packageName)

    fun updateSettings(block: suspend (SettingsRepository) -> Unit) {
        viewModelScope.launch { block(container.settingsRepository) }
    }

    fun dismissOnboardingHome() {
        viewModelScope.launch {
            container.settingsRepository.setOnboardingHomeDone(true)
            showOnboardingHome.value = false
            if (!uiState.value.settings.onboardingMediaDone) {
                showOnboardingMedia.value = true
            }
        }
    }

    fun dismissOnboardingMedia() {
        viewModelScope.launch {
            container.settingsRepository.setOnboardingMediaDone(true)
            showOnboardingMedia.value = false
        }
    }

    fun refreshMedia() {
        container.mediaSessionRepository.refreshSessions()
    }

    fun refreshApps() {
        viewModelScope.launch {
            container.installedAppsRepository.refresh()
            container.iconPackRepository.refreshInstalledPacks()
        }
    }

    companion object {
        fun slotCountFor(appCount: Int, includeDrawer: Boolean = true): Int =
            if (includeDrawer) {
                (appCount + 1).coerceIn(DockRepository.MIN_SLOTS, DockRepository.MAX_DOCK_SLOTS)
            } else {
                appCount.coerceIn(0, DockRepository.MAX_APPS)
            }
    }
}

class LauncherViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LauncherViewModel(container) as T
    }
}
