package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.IconBitmapCache
import com.chaples55.wheelielauncher.data.LauncherApp
import com.chaples55.wheelielauncher.data.key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawerBindConfig(
    val iconSizePx: Int,
    val cellHeightPx: Int,
    val showLabels: Boolean,
    val customIcons: Map<String, String?>,
    val displayLabels: Map<String, String>,
)

class DrawerAppsAdapter(
    private val scope: CoroutineScope,
    private var peekIcon: (ComponentName, String?, Int) -> Bitmap?,
    private var loadIcon: suspend (ComponentName, String?, Int) -> Bitmap?,
    var onLaunch: (ComponentName) -> Unit,
    var onAddToDock: (ComponentName) -> Unit,
    var onChangeLabel: (ComponentName) -> Unit,
    var onChangeIcon: (ComponentName) -> Unit,
    var onAppInfo: (String) -> Unit,
    var onHide: (String) -> Unit,
    var onUninstall: (String) -> Unit,
) : ListAdapter<LauncherApp, DrawerAppsAdapter.Holder>(Diff) {

    var config: DrawerBindConfig = DrawerBindConfig(
        iconSizePx = 144,
        cellHeightPx = 200,
        showLabels = true,
        customIcons = emptyMap(),
        displayLabels = emptyMap(),
    )
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun updateIconLoaders(
        peek: (ComponentName, String?, Int) -> Bitmap?,
        load: suspend (ComponentName, String?, Int) -> Bitmap?,
    ) {
        peekIcon = peek
        loadIcon = load
    }

    private val placeholder = ColorDrawable(0xFF2A2A32.toInt())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.drawer_app_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)
        val key = app.componentName.key()
        val label = config.displayLabels[key] ?: app.label
        val customIcon = config.customIcons[key]
        val iconPx = config.iconSizePx

        holder.bindJob?.cancel()
        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            height = config.cellHeightPx
        }
        holder.icon.layoutParams = holder.icon.layoutParams.apply {
            width = iconPx
            height = iconPx
        }
        holder.label.isVisible = config.showLabels
        holder.label.text = label
        holder.icon.contentDescription = label
        holder.icon.setImageDrawable(placeholder)
        holder.boundKey = IconBitmapCache.key(app.componentName, customIcon, iconPx)

        val peeked = peekIcon(app.componentName, customIcon, iconPx)
        if (peeked != null && !peeked.isRecycled) {
            holder.icon.setImageBitmap(peeked)
        } else {
            val expectKey = holder.boundKey
            holder.bindJob = scope.launch {
                val bmp = withContext(Dispatchers.Default) {
                    loadIcon(app.componentName, customIcon, iconPx)
                }
                if (holder.boundKey == expectKey && bmp != null && !bmp.isRecycled) {
                    holder.icon.setImageBitmap(bmp)
                }
            }
        }

        holder.itemView.setOnClickListener { onLaunch(app.componentName) }
        holder.itemView.setOnLongClickListener {
            showMenu(it, app)
            true
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.bindJob?.cancel()
        holder.bindJob = null
        holder.icon.setImageDrawable(placeholder)
        super.onViewRecycled(holder)
    }

    private fun showMenu(anchor: View, app: LauncherApp) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add(0, 1, 0, R.string.add_to_dock)
            menu.add(0, 2, 1, R.string.change_label)
            menu.add(0, 3, 2, R.string.change_icon)
            menu.add(0, 4, 3, R.string.app_info)
            menu.add(0, 5, 4, R.string.hide_app)
            menu.add(0, 6, 5, R.string.uninstall)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onAddToDock(app.componentName)
                    2 -> onChangeLabel(app.componentName)
                    3 -> onChangeIcon(app.componentName)
                    4 -> onAppInfo(app.packageName)
                    5 -> onHide(app.packageName)
                    6 -> onUninstall(app.packageName)
                }
                true
            }
            show()
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view as LinearLayout
        val icon: ImageView = view.findViewById(R.id.drawer_item_icon)
        val label: TextView = view.findViewById(R.id.drawer_item_label)
        var bindJob: Job? = null
        var boundKey: String? = null
    }

    private object Diff : DiffUtil.ItemCallback<LauncherApp>() {
        override fun areItemsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem.componentName == newItem.componentName

        override fun areContentsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem == newItem
    }
}
