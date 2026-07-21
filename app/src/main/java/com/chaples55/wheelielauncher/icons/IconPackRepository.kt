package com.chaples55.wheelielauncher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.chaples55.wheelielauncher.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
)

data class IconPackDrawable(
    val name: String,
    val drawableName: String,
)

class IconPackRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val cache = ConcurrentHashMap<String, Map<String, String>>()
    private val drawableListCache = ConcurrentHashMap<String, List<IconPackDrawable>>()

    fun installedPacks(): Flow<List<IconPackInfo>> = flow {
        emit(discoverPacks())
    }.flowOn(Dispatchers.IO)

    suspend fun resolveIcon(
        componentName: ComponentName,
        customIcon: String? = null,
    ): Drawable? = withContext(Dispatchers.IO) {
        if (!customIcon.isNullOrBlank()) {
            loadCustomIcon(customIcon)?.let { return@withContext it }
        }
        val settings = settingsRepository.settings.first()
        val pack = settings.iconPackPackage
        if (pack != null) {
            val map = loadAppFilter(pack)
            val drawableName = map[componentName.flattenToString()]
                ?: map[componentName.packageName]
            if (drawableName != null) {
                loadPackDrawable(pack, drawableName)?.let { return@withContext it }
            }
        }
        try {
            context.packageManager.getActivityIcon(componentName)
        } catch (_: Exception) {
            try {
                context.packageManager.getApplicationIcon(componentName.packageName)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun listPackDrawables(packPackage: String): List<IconPackDrawable> = withContext(Dispatchers.IO) {
        drawableListCache[packPackage] ?: buildDrawableList(packPackage).also {
            drawableListCache[packPackage] = it
        }
    }

    suspend fun searchPackDrawables(packPackage: String, query: String): List<IconPackDrawable> {
        val all = listPackDrawables(packPackage)
        if (query.isBlank()) return all
        val q = query.lowercase()
        return all.filter { it.name.lowercase().contains(q) || it.drawableName.lowercase().contains(q) }
    }

    fun loadPackDrawable(packPackage: String, drawableName: String): Drawable? {
        return try {
            val resources = context.packageManager.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier(drawableName, "drawable", packPackage)
            if (id != 0) resources.getDrawable(id, null) else null
        } catch (_: Exception) {
            null
        }
    }

    fun loadCustomIcon(token: String): Drawable? {
        return when {
            token.startsWith("content:") || token.startsWith("file:") -> {
                try {
                    context.contentResolver.openInputStream(android.net.Uri.parse(token))?.use {
                        Drawable.createFromStream(it, token)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            token.contains("/") -> {
                val parts = token.split("/", limit = 2)
                if (parts.size == 2) loadPackDrawable(parts[0], parts[1]) else null
            }
            else -> null
        }
    }

    private fun discoverPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val intents = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.gau.go.launcherex.theme"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
            Intent("com.teslacoilsw.launcher.THEME"),
        )
        val packs = linkedMapOf<String, IconPackInfo>()
        for (intent in intents) {
            val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                if (pkg !in packs) {
                    packs[pkg] = IconPackInfo(
                        packageName = pkg,
                        label = info.loadLabel(pm)?.toString() ?: pkg,
                    )
                }
            }
        }
        return packs.values.sortedBy { it.label.lowercase() }
    }

    private fun loadAppFilter(packPackage: String): Map<String, String> {
        cache[packPackage]?.let { return it }
        val map = parseAppFilter(packPackage)
        cache[packPackage] = map
        return map
    }

    private fun parseAppFilter(packPackage: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val resources = context.packageManager.getResourcesForApplication(packPackage)
            val assetNames = listOf("appfilter.xml", "appfilter", "theme_resources.xml")
            var parser: XmlPullParser? = null
            for (name in assetNames) {
                try {
                    val stream = resources.assets.open(name)
                    parser = android.util.Xml.newPullParser().also {
                        it.setInput(stream, null)
                    }
                    break
                } catch (_: Exception) {
                }
            }
            if (parser == null) {
                val xmlId = resources.getIdentifier("appfilter", "xml", packPackage)
                if (xmlId != 0) {
                    parser = resources.getXml(xmlId)
                }
            }
            val p = parser ?: return emptyMap()
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tag = p.name
                    if (tag == "item" || tag == "icon") {
                        val component = p.getAttributeValue(null, "component")
                            ?: p.getAttributeValue(null, "android:component")
                        val drawable = p.getAttributeValue(null, "drawable")
                            ?: p.getAttributeValue(null, "android:drawable")
                        if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            val cleaned = component
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")
                            result[cleaned] = drawable
                            val pkg = cleaned.substringBefore("/")
                            if (pkg.isNotBlank()) {
                                result.putIfAbsent(pkg, drawable)
                            }
                        }
                    }
                }
                event = p.next()
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun buildDrawableList(packPackage: String): List<IconPackDrawable> {
        return try {
            val resources = context.packageManager.getResourcesForApplication(packPackage)
            val field = Class.forName("$packPackage.R\$drawable")
            field.declaredFields.mapNotNull { f ->
                try {
                    val name = f.name
                    IconPackDrawable(name = name, drawableName = name)
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.name }
        } catch (_: Exception) {
            // Fallback: use appfilter drawable names
            loadAppFilter(packPackage).values.distinct().map {
                IconPackDrawable(name = it, drawableName = it)
            }.sortedBy { it.name }
        }
    }
}
