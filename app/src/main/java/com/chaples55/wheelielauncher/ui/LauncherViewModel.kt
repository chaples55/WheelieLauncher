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
    }

    fun refreshApps() {
        viewModelScope.launch { container.installedAppsRepository.refresh() }
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

    fun stepSelection(delta: Int, slotCount: Int) {
        if (slotCount <= 0) return
        val next = ((selectedDockIndex.value + delta) % slotCount + slotCount) % slotCount
        selectedDockIndex.value = next
    }

    fun rotateBy(deltaDegrees: Float) {
        // Kept for compatibility; wheel now steps selection instead.
        rotationDegrees.value += deltaDegrees
    }

    fun setRotation(degrees: Float) {
        rotationDegrees.value = degrees
    }

    fun snapRotation(slotCount: Int) {
        // No-op: icons stay fixed; selection is discrete.
    }

    fun launch(componentName: ComponentName) {
        container.installedAppsRepository.launch(componentName)
    }

    fun activateSelected(dockItems: List<DockItem>, selectedSlot: Int) {
        if (selectedSlot == 0) {
            openDrawer()
            return
        }
        val appIndex = selectedSlot - 1
        val item = dockItems.getOrNull(appIndex) ?: return
        launch(item.componentName)
    }

    fun togglePlayPause() {
        container.mediaSessionRepository.togglePlayPause()
    }

    fun artworkBitmap(): Bitmap? = container.mediaSessionRepository.currentArtworkBitmap()

    suspend fun resolveIcon(componentName: ComponentName, customIcon: String? = null): Drawable? {
        val settings = uiState.value.settings
        val custom = customIcon ?: settings.customizations[componentName.key()]?.customIcon
        return container.iconPackRepository.resolveIcon(componentName, custom)
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
