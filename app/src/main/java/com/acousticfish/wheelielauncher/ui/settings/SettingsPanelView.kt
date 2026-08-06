package com.acousticfish.wheelielauncher.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.acousticfish.wheelielauncher.R
import com.acousticfish.wheelielauncher.data.LauncherApp
import com.acousticfish.wheelielauncher.data.LauncherSettings
import com.acousticfish.wheelielauncher.data.SettingsRepository
import com.acousticfish.wheelielauncher.data.key
import com.acousticfish.wheelielauncher.icons.IconPackInfo

/**
 * Imperative settings UI with nested pages.
 * SeekBars commit on stop-tracking to avoid DataStore thrash.
 */
class SettingsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    fun interface Host {
        fun onUpdate(block: suspend (SettingsRepository) -> Unit)
    }

    private enum class Page { ROOT, HOME, DRAWER, GENERAL, SYSTEM, ABOUT }

    private val content: LinearLayout
    private val titleView: TextView
    private var host: Host? = null
    private var onBack: (() -> Unit)? = null
    private var onManageHidden: (() -> Unit)? = null
    private var onPickWallpaper: (() -> Unit)? = null
    private var iconPacks: List<IconPackInfo> = emptyList()
    private var apps: List<LauncherApp> = emptyList()
    private var settings: LauncherSettings = LauncherSettings()
    private var page: Page = Page.ROOT

    private var dockIconSize = 48f
    private var dockRing = 0.78f
    private var nowPlayingSize = 200f
    private var drawerColumns = 4
    private var drawerIconSize = 48f
    private var statusScrim = 0.4f
    private var swipeSensitivity = 2f
    private var drawerBgOpacity = 0.45f
    private var progressThickness = 4f
    private var clockSize = 22f
    private var chromeControlSize = 26f
    private var marqueeSpeed = 0.5f

    init {
        LayoutInflater.from(context).inflate(R.layout.settings_panel, this, true)
        orientation = VERTICAL
        content = findViewById(R.id.settings_content)
        titleView = findViewById(R.id.settings_title)
        findViewById<ImageButton>(R.id.settings_back).setOnClickListener { navigateBack() }
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

    fun bind(settings: LauncherSettings, iconPacks: List<IconPackInfo>, apps: List<LauncherApp>) {
        this.settings = settings
        this.iconPacks = iconPacks
        this.apps = apps
        dockIconSize = settings.dockIconSizeDp
        dockRing = settings.dockRingRadiusFraction
        nowPlayingSize = settings.nowPlayingSizeDp
        drawerColumns = settings.drawerColumns
        drawerIconSize = settings.drawerIconSizeDp
        statusScrim = settings.statusBarScrimOpacity
        swipeSensitivity = settings.swipeSensitivity
        drawerBgOpacity = settings.drawerBackgroundOpacity
        progressThickness = settings.progressBarThicknessDp
        clockSize = settings.clockSizeSp
        chromeControlSize = settings.chromeControlSizeDp
        marqueeSpeed = settings.marqueeSpeed
        showPage(page)
    }

    /** @return true if the back press was consumed by a submenu. */
    fun handleBack(): Boolean {
        if (page != Page.ROOT) {
            showPage(Page.ROOT)
            return true
        }
        return false
    }

    private fun navigateBack() {
        if (!handleBack()) onBack?.invoke()
    }

    private fun showPage(target: Page) {
        page = target
        content.removeAllViews()
        titleView.text = when (target) {
            Page.ROOT -> context.getString(R.string.settings)
            Page.HOME -> context.getString(R.string.settings_home)
            Page.DRAWER -> context.getString(R.string.settings_drawer)
            Page.GENERAL -> context.getString(R.string.settings_general)
            Page.SYSTEM -> context.getString(R.string.settings_system)
            Page.ABOUT -> context.getString(R.string.settings_about)
        }
        when (target) {
            Page.ROOT -> buildRoot()
            Page.HOME -> buildHome()
            Page.DRAWER -> buildDrawer()
            Page.GENERAL -> buildGeneral()
            Page.SYSTEM -> buildSystem()
            Page.ABOUT -> buildAbout()
        }
    }

    private fun inflater() = LayoutInflater.from(context)

    private fun section(title: String) {
        val tv = inflater().inflate(R.layout.settings_section, content, false) as TextView
        tv.text = title
        content.addView(tv)
    }

    private fun switchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit): Switch {
        val row = inflater().inflate(R.layout.settings_switch_row, content, false)
        row.findViewById<TextView>(R.id.settings_switch_label).text = label
        val sw = row.findViewById<Switch>(R.id.settings_switch)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, isChecked -> onChecked(isChecked) }
        content.addView(row)
        return sw
    }

    private fun sliderRow(
        initialLabel: String,
        value: Float,
        min: Float,
        max: Float,
        steps: Int,
        onLabel: (Float) -> String,
        onCommit: (Float) -> Unit,
        onLive: ((Float) -> Unit)? = null,
    ): TextView {
        val row = inflater().inflate(R.layout.settings_slider_row, content, false)
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
        return label
    }

    private fun navRow(title: String, subtitle: String?, onClick: () -> Unit): TextView? {
        val row = inflater().inflate(R.layout.settings_nav_row, content, false) as LinearLayout
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
        return subView
    }

    private fun buildRoot() {
        navRow(context.getString(R.string.settings_home), null) { showPage(Page.HOME) }
        navRow(context.getString(R.string.settings_drawer), null) { showPage(Page.DRAWER) }
        navRow(context.getString(R.string.settings_general), null) { showPage(Page.GENERAL) }
        navRow(context.getString(R.string.settings_system), null) { showPage(Page.SYSTEM) }
        navRow(context.getString(R.string.settings_about), null) { showPage(Page.ABOUT) }
    }

    private fun buildHome() {
        section("Dock")
        switchRow("Show dock labels", settings.dockShowLabels) {
            host?.onUpdate { repo -> repo.setDockShowLabels(it) }
        }
        sliderRow(
            initialLabel = "Dock icon size (${dockIconSize.toInt()} dp)",
            value = dockIconSize,
            min = 36f,
            max = 100f,
            steps = 0,
            onLabel = { "Dock icon size (${it.toInt()} dp)" },
            onCommit = {
                dockIconSize = it
                host?.onUpdate { repo -> repo.setDockIconSize(it) }
            },
            onLive = { dockIconSize = it },
        )
        sliderRow(
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
        )
        sliderRow(
            initialLabel = "Now Playing size (${nowPlayingSize.toInt()} dp)",
            value = nowPlayingSize,
            min = 72f,
            max = 264f,
            steps = 0,
            onLabel = { "Now Playing size (${it.toInt()} dp)" },
            onCommit = {
                nowPlayingSize = it
                host?.onUpdate { repo -> repo.setNowPlayingSize(it) }
            },
            onLive = { nowPlayingSize = it },
        )
        sliderRow(
            initialLabel = "${context.getString(R.string.progress_bar_thickness)} (${progressThickness.toInt()} dp)",
            value = progressThickness,
            min = 2f,
            max = 14f,
            steps = 0,
            onLabel = { "${context.getString(R.string.progress_bar_thickness)} (${it.toInt()} dp)" },
            onCommit = {
                progressThickness = it
                host?.onUpdate { repo -> repo.setProgressBarThickness(it) }
            },
            onLive = { progressThickness = it },
        )
        switchRow(context.getString(R.string.show_battery_bar), settings.showBatteryBar) {
            host?.onUpdate { repo -> repo.setShowBatteryBar(it) }
        }
        switchRow(context.getString(R.string.show_track_info), settings.showTrackInfo) {
            host?.onUpdate { repo -> repo.setShowTrackInfo(it) }
            showPage(Page.HOME)
        }
        if (settings.showTrackInfo) {
            sliderRow(
                initialLabel = marqueeSpeedLabel(marqueeSpeed),
                value = marqueeSpeed,
                min = 0.25f,
                max = 1f,
                steps = 0,
                onLabel = { marqueeSpeedLabel(it) },
                onCommit = {
                    marqueeSpeed = it
                    host?.onUpdate { repo -> repo.setMarqueeSpeed(it) }
                },
                onLive = { marqueeSpeed = it },
            )
        }

        section("Chrome")
        switchRow(context.getString(R.string.show_clock), settings.showClock) {
            host?.onUpdate { repo -> repo.setShowClock(it) }
            showPage(Page.HOME)
        }
        if (settings.showClock) {
            sliderRow(
                initialLabel = "${context.getString(R.string.clock_size)} (${clockSize.toInt()} sp)",
                value = clockSize,
                min = 14f,
                max = 48f,
                steps = 0,
                onLabel = { "${context.getString(R.string.clock_size)} (${it.toInt()} sp)" },
                onCommit = {
                    clockSize = it
                    host?.onUpdate { repo -> repo.setClockSizeSp(it) }
                },
                onLive = { clockSize = it },
            )
        }
        switchRow(context.getString(R.string.show_eq_button), settings.showEqButton) {
            host?.onUpdate { repo -> repo.setShowEqButton(it) }
            showPage(Page.HOME)
        }
        if (settings.showEqButton) {
            navRow(context.getString(R.string.eq_target_app), eqAppLabel()) {
                showEqAppDialog()
            }
        }
        switchRow(context.getString(R.string.show_skip_buttons), settings.showSkipButtons) {
            host?.onUpdate { repo -> repo.setShowSkipButtons(it) }
            showPage(Page.HOME)
        }
        if (settings.showEqButton || settings.showSkipButtons) {
            sliderRow(
                initialLabel = "${context.getString(R.string.chrome_control_size)} (${chromeControlSize.toInt()} dp)",
                value = chromeControlSize,
                min = 18f,
                max = 48f,
                steps = 0,
                onLabel = { "${context.getString(R.string.chrome_control_size)} (${it.toInt()} dp)" },
                onCommit = {
                    chromeControlSize = it
                    host?.onUpdate { repo -> repo.setChromeControlSizeDp(it) }
                },
                onLive = { chromeControlSize = it },
            )
        }
    }

    private fun buildDrawer() {
        switchRow("Show drawer labels", settings.drawerShowLabels) {
            host?.onUpdate { repo -> repo.setDrawerShowLabels(it) }
        }
        switchRow(context.getString(R.string.show_drawer_search), settings.drawerShowSearch) {
            host?.onUpdate { repo -> repo.setDrawerShowSearch(it) }
        }
        switchRow(context.getString(R.string.swipe_up_open_drawer), settings.swipeUpToOpenDrawer) {
            host?.onUpdate { repo -> repo.setSwipeUpToOpenDrawer(it) }
            showPage(Page.DRAWER)
        }
        if (settings.swipeUpToOpenDrawer) {
            switchRow(context.getString(R.string.hide_drawer_button), settings.hideDrawerButton) {
                host?.onUpdate { repo -> repo.setHideDrawerButton(it) }
            }
        }
        sliderRow(
            initialLabel = swipeSensitivityLabelText(swipeSensitivity),
            value = swipeSensitivity,
            min = 0.25f,
            max = 3f,
            steps = 0,
            onLabel = { swipeSensitivityLabelText(it) },
            onCommit = {
                swipeSensitivity = it
                host?.onUpdate { repo -> repo.setSwipeSensitivity(it) }
            },
            onLive = { swipeSensitivity = it },
        )
        sliderRow(
            initialLabel = drawerBgOpacityLabel(drawerBgOpacity),
            value = drawerBgOpacity,
            min = 0f,
            max = 1f,
            steps = 0,
            onLabel = { drawerBgOpacityLabel(it) },
            onCommit = {
                drawerBgOpacity = it
                host?.onUpdate { repo -> repo.setDrawerBackgroundOpacity(it) }
            },
            onLive = { drawerBgOpacity = it },
        )
        sliderRow(
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
        )
        sliderRow(
            initialLabel = "Drawer icon size (${drawerIconSize.toInt()} dp)",
            value = drawerIconSize,
            min = 36f,
            max = 100f,
            steps = 0,
            onLabel = { "Drawer icon size (${it.toInt()} dp)" },
            onCommit = {
                drawerIconSize = it
                host?.onUpdate { repo -> repo.setDrawerIconSize(it) }
            },
            onLive = { drawerIconSize = it },
        )
        navRow(context.getString(R.string.hidden_apps), null) {
            onManageHidden?.invoke()
        }
    }

    private fun buildGeneral() {
        section("Appearance")
        switchRow("Show status bar", settings.showStatusBar) {
            host?.onUpdate { repo -> repo.setShowStatusBar(it) }
        }
        sliderRow(
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
        )

        section("Wallpaper")
        navRow(
            context.getString(R.string.default_wallpaper),
            if (settings.defaultWallpaperUri != null) "Custom image set" else "System wallpaper",
        ) {
            onPickWallpaper?.invoke()
        }

        section("Icons")
        navRow("Icon pack", iconPackLabel()) {
            showIconPackDialog()
        }
    }

    private fun buildSystem() {
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

    private fun buildAbout() {
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val iconSize = (96 * density).toInt()

        val header = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, (8 * density).toInt())
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(appIconDrawable())
            contentDescription = context.getString(R.string.app_name)
        }
        header.addView(icon)
        content.addView(header)

        navRow(context.getString(R.string.app_name), context.getString(R.string.app_name), onClick = {})
        navRow(context.getString(R.string.about_version), appVersionName(), onClick = {})
        navRow(context.getString(R.string.about_author), context.getString(R.string.about_author_name), onClick = {})
        navRow(context.getString(R.string.about_privacy_policy), null) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/chaples55/WheelieLauncher/blob/main/PRIVACY.md"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        navRow(
            context.getString(R.string.about_donation),
            context.getString(R.string.about_donation_subtitle),
        ) {
            Toast.makeText(context, R.string.about_donation_subtitle, Toast.LENGTH_SHORT).show()
        }
    }

    private fun appIconDrawable(): Drawable? = try {
        context.packageManager.getApplicationIcon(context.packageName)
    } catch (_: Exception) {
        null
    }

    private fun appVersionName(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    } catch (_: PackageManager.NameNotFoundException) {
        "1.0"
    }

    private fun swipeSensitivityLabelText(value: Float): String =
        "${context.getString(R.string.swipe_sensitivity)} (${String.format("%.2f", value)}×)"

    private fun marqueeSpeedLabel(value: Float): String =
        "${context.getString(R.string.marquee_speed)} (${(value * 100).toInt()}%)"

    private fun drawerBgOpacityLabel(value: Float): String =
        "${context.getString(R.string.drawer_background_opacity)} (${(value * 100).toInt()}%)"

    private fun iconPackLabel(): String {
        val pack = iconPacks.find { it.packageName == settings.iconPackPackage }?.label
        return pack ?: if (settings.iconPackPackage == null) "System default" else settings.iconPackPackage!!
    }

    private fun eqAppLabel(): String {
        val key = settings.eqAppComponent ?: return context.getString(R.string.not_set)
        return apps.find { it.componentName.key() == key }?.label
            ?: key.substringAfter('/').ifBlank { key }
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

    private fun showEqAppDialog() {
        if (apps.isEmpty()) {
            AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.not_set))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.eq_target_app)
            .setItems(labels) { _, which ->
                val app = apps[which]
                host?.onUpdate { it.setEqAppComponent(app.componentName.key()) }
            }
            .setNeutralButton(R.string.not_set) { _, _ ->
                host?.onUpdate { it.setEqAppComponent(null) }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
