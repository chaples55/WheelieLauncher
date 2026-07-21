package com.chaples55.wheelielauncher.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.LauncherSettings
import com.chaples55.wheelielauncher.data.SettingsRepository
import com.chaples55.wheelielauncher.icons.IconPackInfo

/**
 * Imperative settings UI. SeekBars commit on stop-tracking to avoid DataStore thrash.
 */
class SettingsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    fun interface Host {
        fun onUpdate(block: suspend (SettingsRepository) -> Unit)
    }

    private val content: LinearLayout
    private var host: Host? = null
    private var onBack: (() -> Unit)? = null
    private var onManageHidden: (() -> Unit)? = null
    private var onPickWallpaper: (() -> Unit)? = null
    private var iconPacks: List<IconPackInfo> = emptyList()
    private var settings: LauncherSettings = LauncherSettings()
    private var built = false

    // Local slider mirrors so drag feels instant before commit.
    private var dockIconSize = 48f
    private var dockRing = 0.78f
    private var nowPlayingSize = 120f
    private var drawerColumns = 4
    private var drawerIconSize = 48f
    private var statusScrim = 0.4f

    private var dockLabelsSwitch: Switch? = null
    private var drawerLabelsSwitch: Switch? = null
    private var drawerSearchSwitch: Switch? = null
    private var statusBarSwitch: Switch? = null
    private var dockIconLabel: TextView? = null
    private var dockRingLabel: TextView? = null
    private var npSizeLabel: TextView? = null
    private var drawerColsLabel: TextView? = null
    private var drawerIconLabel: TextView? = null
    private var scrimLabel: TextView? = null
    private var wallpaperSubtitle: TextView? = null
    private var clearWallpaperRow: LinearLayout? = null
    private var iconPackSubtitle: TextView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_panel, this, true)
        orientation = VERTICAL
        content = findViewById(R.id.settings_content)
        findViewById<ImageButton>(R.id.settings_back).setOnClickListener { onBack?.invoke() }
    }

    fun bindCallbacks(
        host: Host,
        onBack: () -> Unit,
        onManageHidden: () -> Unit,
        onPickWallpaper: () -> Unit,
    ) {
        this.host = host
        this.onBack = onBack
        this.onManageHidden = onManageHidden
        this.onPickWallpaper = onPickWallpaper
    }

    fun bind(settings: LauncherSettings, iconPacks: List<IconPackInfo>) {
        this.settings = settings
        this.iconPacks = iconPacks
        dockIconSize = settings.dockIconSizeDp
        dockRing = settings.dockRingRadiusFraction
        nowPlayingSize = settings.nowPlayingSizeDp
        drawerColumns = settings.drawerColumns
        drawerIconSize = settings.drawerIconSizeDp
        statusScrim = settings.statusBarScrimOpacity
        if (!built) {
            buildRows()
            built = true
        }
        syncWidgets()
    }

    private fun buildRows() {
        content.removeAllViews()
        val inflater = LayoutInflater.from(context)

        fun section(title: String) {
            val tv = inflater.inflate(R.layout.settings_section, content, false) as TextView
            tv.text = title
            content.addView(tv)
        }

        fun switchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit): Switch {
            val row = inflater.inflate(R.layout.settings_switch_row, content, false)
            row.findViewById<TextView>(R.id.settings_switch_label).text = label
            val sw = row.findViewById<Switch>(R.id.settings_switch)
            sw.isChecked = checked
            sw.setOnCheckedChangeListener { _, isChecked -> onChecked(isChecked) }
            content.addView(row)
            return sw
        }

        fun sliderRow(
            initialLabel: String,
            value: Float,
            min: Float,
            max: Float,
            steps: Int,
            onLabel: (Float) -> String,
            onCommit: (Float) -> Unit,
            onLive: ((Float) -> Unit)? = null,
        ): Pair<TextView, SeekBar> {
            val row = inflater.inflate(R.layout.settings_slider_row, content, false)
            val label = row.findViewById<TextView>(R.id.settings_slider_label)
            val bar = row.findViewById<SeekBar>(R.id.settings_slider)
            label.text = initialLabel
            val maxProgress = if (steps > 0) steps + 1 else 1000
            bar.max = maxProgress
            fun progressOf(v: Float) = (((v - min) / (max - min)) * maxProgress).toInt().coerceIn(0, maxProgress)
            fun valueOf(p: Int) = min + (max - min) * (p.toFloat() / maxProgress)
            bar.progress = progressOf(value)
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = valueOf(progress)
                    label.text = onLabel(v)
                    onLive?.invoke(v)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val v = valueOf(bar.progress)
                    label.text = onLabel(v)
                    onCommit(v)
                }
            })
            content.addView(row)
            return label to bar
        }

        fun navRow(title: String, subtitle: String?, onClick: () -> Unit): Pair<TextView, TextView?> {
            val row = inflater.inflate(R.layout.settings_nav_row, content, false) as LinearLayout
            val titleView = row.findViewById<TextView>(R.id.settings_nav_title)
            val subView = row.findViewById<TextView>(R.id.settings_nav_subtitle)
            titleView.text = title
            if (subtitle != null) {
                subView.visibility = VISIBLE
                subView.text = subtitle
            } else {
                subView.visibility = GONE
            }
            row.setOnClickListener { onClick() }
            content.addView(row)
            return titleView to subView
        }

        section("Dock")
        dockLabelsSwitch = switchRow("Show dock labels", settings.dockShowLabels) {
            host?.onUpdate { repo -> repo.setDockShowLabels(it) }
        }
        dockIconLabel = sliderRow(
            initialLabel = "Dock icon size",
            value = dockIconSize,
            min = 36f,
            max = 72f,
            steps = 0,
            onLabel = { "Dock icon size (${it.toInt()} dp)" },
            onCommit = {
                dockIconSize = it
                host?.onUpdate { repo -> repo.setDockIconSize(it) }
            },
            onLive = { dockIconSize = it },
        ).first
        dockRingLabel = sliderRow(
            initialLabel = "Icon ring size (${(dockRing * 100).toInt()}%)",
            value = dockRing,
            min = 0.45f,
            max = 0.92f,
            steps = 0,
            onLabel = { "Icon ring size (${(it * 100).toInt()}%)" },
            onCommit = {
                dockRing = it
                host?.onUpdate { repo -> repo.setDockRingRadius(it) }
            },
            onLive = { dockRing = it },
        ).first
        npSizeLabel = sliderRow(
            initialLabel = "Now Playing size",
            value = nowPlayingSize,
            min = 72f,
            max = 220f,
            steps = 0,
            onLabel = { "Now Playing size (${it.toInt()} dp)" },
            onCommit = {
                nowPlayingSize = it
                host?.onUpdate { repo -> repo.setNowPlayingSize(it) }
            },
            onLive = { nowPlayingSize = it },
        ).first

        section("Drawer")
        drawerLabelsSwitch = switchRow("Show drawer labels", settings.drawerShowLabels) {
            host?.onUpdate { repo -> repo.setDrawerShowLabels(it) }
        }
        drawerSearchSwitch = switchRow(context.getString(R.string.show_drawer_search), settings.drawerShowSearch) {
            host?.onUpdate { repo -> repo.setDrawerShowSearch(it) }
        }
        drawerColsLabel = sliderRow(
            initialLabel = "Drawer columns ($drawerColumns)",
            value = drawerColumns.toFloat(),
            min = 2f,
            max = 6f,
            steps = 3,
            onLabel = { "Drawer columns (${it.toInt()})" },
            onCommit = {
                drawerColumns = it.toInt().coerceIn(2, 6)
                host?.onUpdate { repo -> repo.setDrawerColumns(drawerColumns) }
            },
            onLive = { drawerColumns = it.toInt().coerceIn(2, 6) },
        ).first
        drawerIconLabel = sliderRow(
            initialLabel = "Drawer icon size (${drawerIconSize.toInt()} dp)",
            value = drawerIconSize,
            min = 36f,
            max = 72f,
            steps = 0,
            onLabel = { "Drawer icon size (${it.toInt()} dp)" },
            onCommit = {
                drawerIconSize = it
                host?.onUpdate { repo -> repo.setDrawerIconSize(it) }
            },
            onLive = { drawerIconSize = it },
        ).first
        navRow(context.getString(R.string.hidden_apps), null) {
            onManageHidden?.invoke()
        }

        section("Appearance")
        statusBarSwitch = switchRow("Show status bar", settings.showStatusBar) {
            host?.onUpdate { repo -> repo.setShowStatusBar(it) }
        }
        scrimLabel = sliderRow(
            initialLabel = "Status bar scrim (${(statusScrim * 100).toInt()}%)",
            value = statusScrim,
            min = 0f,
            max = 1f,
            steps = 0,
            onLabel = { "Status bar scrim (${(it * 100).toInt()}%)" },
            onCommit = {
                statusScrim = it
                host?.onUpdate { repo -> repo.setStatusBarScrim(it) }
            },
            onLive = { statusScrim = it },
        ).first
        wallpaperSubtitle = navRow(
            context.getString(R.string.default_wallpaper),
            if (settings.defaultWallpaperUri != null) "Custom image set" else "Built-in default",
        ) {
            onPickWallpaper?.invoke()
        }.second
        clearWallpaperRow = navRow(context.getString(R.string.clear_wallpaper), null) {
            host?.onUpdate { it.setWallpaperUri(null) }
        }.first.parent as LinearLayout

        section("Icons")
        iconPackSubtitle = navRow("Icon pack", iconPackLabel()) {
            showIconPackDialog()
        }.second

        section("System")
        navRow(context.getString(R.string.set_as_home), null) {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        navRow(context.getString(R.string.enable_notification_access), null) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun syncWidgets() {
        dockLabelsSwitch?.isChecked = settings.dockShowLabels
        drawerLabelsSwitch?.isChecked = settings.drawerShowLabels
        drawerSearchSwitch?.isChecked = settings.drawerShowSearch
        statusBarSwitch?.isChecked = settings.showStatusBar
        dockIconLabel?.text = "Dock icon size (${dockIconSize.toInt()} dp)"
        dockRingLabel?.text = "Icon ring size (${(dockRing * 100).toInt()}%)"
        npSizeLabel?.text = "Now Playing size (${nowPlayingSize.toInt()} dp)"
        drawerColsLabel?.text = "Drawer columns ($drawerColumns)"
        drawerIconLabel?.text = "Drawer icon size (${drawerIconSize.toInt()} dp)"
        scrimLabel?.text = "Status bar scrim (${(statusScrim * 100).toInt()}%)"
        wallpaperSubtitle?.apply {
            visibility = VISIBLE
            text = if (settings.defaultWallpaperUri != null) "Custom image set" else "Built-in default"
        }
        clearWallpaperRow?.visibility =
            if (settings.defaultWallpaperUri != null) VISIBLE else GONE
        iconPackSubtitle?.apply {
            visibility = VISIBLE
            text = iconPackLabel()
        }
    }

    private fun iconPackLabel(): String {
        val pack = iconPacks.find { it.packageName == settings.iconPackPackage }?.label
        return pack ?: if (settings.iconPackPackage == null) "System default" else settings.iconPackPackage!!
    }

    private fun showIconPackDialog() {
        val labels = mutableListOf("System default")
        val values = mutableListOf<String?>(null)
        iconPacks.forEach {
            labels.add(it.label)
            values.add(it.packageName)
        }
        AlertDialog.Builder(context)
            .setTitle("Icon pack")
            .setItems(labels.toTypedArray()) { _, which ->
                host?.onUpdate { it.setIconPack(values[which]) }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
