package com.acousticfish.wheelielauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val packageManager: PackageManager = context.packageManager
    private val rawApps = MutableStateFlow<List<LauncherApp>>(emptyList())

    init {
        // Load synchronously-ish on first access via refresh from ViewModel; seed empty
    }

    fun apps(): Flow<List<LauncherApp>> = combine(rawApps, settingsRepository.settings) { apps, settings ->
        apps
            .filter { it.packageName !in settings.hiddenPackages }
            .filter { it.packageName != context.packageName }
            .map { app ->
                val custom = settings.customizations[app.componentName.key()]
                if (custom?.customLabel != null) {
                    app.copy(label = custom.customLabel)
                } else {
                    app
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun refresh(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val loaded = loadApps()
        rawApps.value = loaded
        loaded
    }

    fun loadIcon(componentName: ComponentName): Drawable? {
        return try {
            packageManager.getActivityIcon(componentName)
        } catch (_: Exception) {
            try {
                packageManager.getApplicationIcon(componentName.packageName)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun launch(componentName: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun loadApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val cn = ComponentName(activity.packageName, activity.name)
            LauncherApp(
                componentName = cn,
                label = info.loadLabel(packageManager)?.toString() ?: activity.packageName,
            )
        }.distinctBy { it.componentName }
    }
}
