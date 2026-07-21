package com.chaples55.wheelielauncher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaples55.wheelielauncher.data.AppContainer
import com.chaples55.wheelielauncher.data.AppCustomization
import com.chaples55.wheelielauncher.data.DockItem
import com.chaples55.wheelielauncher.data.DockRepository
import com.chaples55.wheelielauncher.data.IconBitmapCache
import com.chaples55.wheelielauncher.data.LauncherApp
import com.chaples55.wheelielauncher.data.LauncherSettings
import com.chaples55.wheelielauncher.data.NowPlayingState
import com.chaples55.wheelielauncher.data.SettingsRepository
import com.chaples55.wheelielauncher.data.key
import com.chaples55.wheelielauncher.icons.IconPackInfo
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
    val nowPlaying: NowPlayingState = NowPlayingState(),
    val drawerOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val selectedDockIndex: Int = 0,
    val rotationDegrees: Float = 0f,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val showOnboardingHome: Boolean = false,
    val showOnboardingMedia: Boolean = false,
)

class LauncherViewModel(private val container: AppContainer) : ViewModel() {
    private val drawerOpen = MutableStateFlow(false)
    private val settingsOpen = MutableStateFlow(false)
    private val selectedDockIndex = MutableStateFlow(0)
    private val rotationDegrees = MutableStateFlow(0f)
    private val showOnboardingHome = MutableStateFlow(false)
    private val showOnboardingMedia = MutableStateFlow(false)

    private data class NavState(
        val drawerOpen: Boolean,
        val settingsOpen: Boolean,
        val selectedDockIndex: Int,
        val rotationDegrees: Float,
        val showOnboardingHome: Boolean,
        val showOnboardingMedia: Boolean,
    )

    private val navState = combine(
        combine(drawerOpen, settingsOpen, selectedDockIndex) { d, s, i -> Triple(d, s, i) },
        combine(rotationDegrees, showOnboardingHome, showOnboardingMedia) { r, h, m -> Triple(r, h, m) },
    ) { a, b ->
        NavState(
            drawerOpen = a.first,
            settingsOpen = a.second,
            selectedDockIndex = a.third,
            rotationDegrees = b.first,
            showOnboardingHome = b.second,
            showOnboardingMedia = b.third,
        )
    }

    private data class CoreState(
        val settings: LauncherSettings,
        val dockItems: List<DockItem>,
        val apps: List<LauncherApp>,
        val nowPlaying: NowPlayingState,
        val iconPacks: List<IconPackInfo>,
    )

    private val coreState = combine(
        container.settingsRepository.settings,
        container.dockRepository.dockItems,
        container.installedAppsRepository.apps(),
        container.mediaSessionRepository.state,
        container.iconPackRepository.installedPacks(),
    ) { settings, dock, apps, media, packs ->
        CoreState(settings, dock, apps, media, packs)
    }

    val uiState: StateFlow<HomeUiState> = combine(coreState, navState) { core, nav ->
        HomeUiState(
            settings = core.settings,
            dockItems = core.dockItems,
            apps = core.apps,
            nowPlaying = core.nowPlaying,
            drawerOpen = nav.drawerOpen,
            settingsOpen = nav.settingsOpen,
            selectedDockIndex = nav.selectedDockIndex,
            rotationDegrees = nav.rotationDegrees,
            iconPacks = core.iconPacks,
            showOnboardingHome = nav.showOnboardingHome,
            showOnboardingMedia = nav.showOnboardingMedia,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

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
        // Preload icons whenever the visible app list or icon size changes.
        viewModelScope.launch {
            uiState
                .map { it.apps to it.settings.drawerIconSizeDp }
                .distinctUntilChanged()
                .collect { (apps, sizeDp) ->
                    if (apps.isNotEmpty()) preloadIcons(apps, sizeDp)
                }
        }
    }

    override fun onCleared() {
        container.mediaSessionRepository.stop()
        super.onCleared()
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

    fun openNowPlayingApp() {
        container.mediaSessionRepository.openSessionApp()
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
        return container.iconBitmapCache.get(IconBitmapCache.key(componentName, custom, sizePx))
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
        val key = IconBitmapCache.key(componentName, custom, sizePx)
        return container.iconBitmapCache.getOrLoad(key, sizePx) {
            container.iconPackRepository.resolveIcon(componentName, custom)
        }
    }

    fun preloadIcons(apps: List<LauncherApp>, sizeDp: Float) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            // Preload common densities so peek hits on device screens.
            val densities = listOf(2f, 2.75f, 3f, 3.5f)
            val sizeDps = listOf(sizeDp, settings.dockIconSizeDp).distinct()
            for (dp in sizeDps) {
                for (density in densities) {
                    val sizePx = (dp * density).toInt().coerceIn(72, 256)
                    val loaders = apps.map { app ->
                        val custom = settings.customizations[app.componentName.key()]?.customIcon
                        val key = IconBitmapCache.key(app.componentName, custom, sizePx)
                        key to suspend {
                            container.iconPackRepository.resolveIcon(app.componentName, custom)
                        }
                    }
                    container.iconBitmapCache.preload(loaders, sizePx)
                }
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
            val apps = uiState.value.apps
            val size = uiState.value.settings.drawerIconSizeDp
            preloadIcons(apps, size)
        }
    }

    companion object {
        fun slotCountFor(appCount: Int): Int =
            (appCount + 1).coerceIn(DockRepository.MIN_SLOTS, DockRepository.MAX_DOCK_SLOTS)
    }
}

class LauncherViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LauncherViewModel(container) as T
    }
}
