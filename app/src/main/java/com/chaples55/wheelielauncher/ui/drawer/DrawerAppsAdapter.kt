package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
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
import kotlin.math.abs

private const val TYPE_HEADER = 0
private const val TYPE_SEARCH = 1
private const val TYPE_APP = 2

sealed class DrawerListItem {
    data object Header : DrawerListItem()
    data object Search : DrawerListItem()
    data class App(val app: LauncherApp) : DrawerListItem()
}

data class DrawerBindConfig(
    val iconSizePx: Int,
    val cellHeightPx: Int,
    val showLabels: Boolean,
    val showSearch: Boolean,
    val columns: Int,
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
    var onOpenSettings: () -> Unit,
    var onQueryChanged: (String) -> Unit,
) : ListAdapter<DrawerListItem, RecyclerView.ViewHolder>(Diff) {

    var config: DrawerBindConfig = DrawerBindConfig(
        iconSizePx = 144,
        cellHeightPx = 200,
        showLabels = true,
        showSearch = true,
        columns = 4,
        customIcons = emptyMap(),
        displayLabels = emptyMap(),
    )
        set(value) {
            val sizeChanged = field.iconSizePx != value.iconSizePx ||
                field.cellHeightPx != value.cellHeightPx ||
                field.showLabels != value.showLabels
            field = value
            if (sizeChanged) notifyDataSetChanged()
        }

    var queryText: String = ""
        private set

    fun setQueryExternal(q: String) {
        if (queryText == q) return
        queryText = q
        val searchIndex = currentList.indexOfFirst { it is DrawerListItem.Search }
        if (searchIndex >= 0) notifyItemChanged(searchIndex)
    }

    fun updateIconLoaders(
        peek: (ComponentName, String?, Int) -> Bitmap?,
        load: suspend (ComponentName, String?, Int) -> Bitmap?,
    ) {
        peekIcon = peek
        loadIcon = load
    }

    fun spanSizeLookup(columns: Int) = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (getItem(position)) {
                is DrawerListItem.App -> 1
                else -> columns
            }
        }
    }

    private val placeholder = ColorDrawable(0xFF2A2A32.toInt())

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DrawerListItem.Header -> TYPE_HEADER
        is DrawerListItem.Search -> TYPE_SEARCH
        is DrawerListItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.drawer_header, parent, false))
            TYPE_SEARCH -> SearchHolder(inflater.inflate(R.layout.drawer_search, parent, false))
            else -> AppHolder(inflater.inflate(R.layout.drawer_app_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> {
                holder.settings.setOnClickListener { onOpenSettings() }
            }
            is SearchHolder -> holder.bind()
            is AppHolder -> {
                val item = getItem(position) as DrawerListItem.App
                holder.bind(item.app)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AppHolder) {
            holder.bindJob?.cancel()
            holder.bindJob = null
            holder.icon.setImageDrawable(placeholder)
        }
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

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val settings: ImageButton = view.findViewById(R.id.drawer_header_settings)
    }

    inner class SearchHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val input: EditText = view.findViewById(R.id.drawer_search_input)
        private val clear: ImageButton = view.findViewById(R.id.drawer_search_clear)
        private var watcher: TextWatcher? = null

        fun bind() {
            watcher?.let { input.removeTextChangedListener(it) }
            if (input.text.toString() != queryText) {
                input.setText(queryText)
                input.setSelection(queryText.length)
            }
            clear.isVisible = queryText.isNotEmpty()
            clear.setOnClickListener {
                input.setText("")
                queryText = ""
                onQueryChanged("")
            }
            val tw = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString().orEmpty()
                    if (q == queryText) return
                    queryText = q
                    clear.isVisible = q.isNotEmpty()
                    onQueryChanged(q)
                }
            }
            watcher = tw
            input.addTextChangedListener(tw)
        }
    }

    inner class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.drawer_item_icon)
        val label: TextView = view.findViewById(R.id.drawer_item_label)
        var bindJob: Job? = null
        var boundKey: String? = null

        fun bind(app: LauncherApp) {
            val key = app.componentName.key()
            val labelText = config.displayLabels[key] ?: app.label
            val customIcon = config.customIcons[key]
            val iconPx = config.iconSizePx.coerceAtLeast(1)

            bindJob?.cancel()
            itemView.layoutParams = itemView.layoutParams.apply {
                height = config.cellHeightPx
            }
            icon.layoutParams = icon.layoutParams.apply {
                width = iconPx
                height = iconPx
            }
            icon.requestLayout()
            label.isVisible = config.showLabels
            label.text = labelText
            icon.contentDescription = labelText
            icon.setImageDrawable(placeholder)
            boundKey = IconBitmapCache.key(app.componentName, customIcon, iconPx)

            val peeked = peekIcon(app.componentName, customIcon, iconPx)
            if (peeked != null && !peeked.isRecycled) {
                icon.setImageBitmap(peeked)
            } else {
                val expectKey = boundKey
                bindJob = scope.launch {
                    val bmp = withContext(Dispatchers.Default) {
                        loadIcon(app.componentName, customIcon, iconPx)
                    }
                    if (boundKey == expectKey && bmp != null && !bmp.isRecycled) {
                        icon.setImageBitmap(bmp)
                    }
                }
            }

            itemView.setOnClickListener { onLaunch(app.componentName) }
            itemView.setOnLongClickListener {
                showMenu(it, app)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DrawerListItem>() {
        override fun areItemsTheSame(oldItem: DrawerListItem, newItem: DrawerListItem): Boolean =
            when {
                oldItem is DrawerListItem.Header && newItem is DrawerListItem.Header -> true
                oldItem is DrawerListItem.Search && newItem is DrawerListItem.Search -> true
                oldItem is DrawerListItem.App && newItem is DrawerListItem.App ->
                    oldItem.app.componentName == newItem.app.componentName
                else -> false
            }

        override fun areContentsTheSame(oldItem: DrawerListItem, newItem: DrawerListItem): Boolean =
            oldItem == newItem
    }
}

/**
 * RecyclerView that reports downward overscroll at the top so the drawer can dismiss.
 */
class PullDismissRecyclerView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
) : RecyclerView(context, attrs) {
    var pullOffsetPx: Float = 0f
        private set
    var onPullChanged: ((Float) -> Unit)? = null
    var onPullEnd: ((Float, Float) -> Unit)? = null // amount, velocityY

    private var trackingPull = false
    private var lastY = 0f
    private var velocityTrackerY = 0f

    fun resetPull() {
        if (pullOffsetPx != 0f) {
            pullOffsetPx = 0f
            onPullChanged?.invoke(0f)
        }
        trackingPull = false
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                velocityTrackerY = 0f
                if (pullOffsetPx > 0f) {
                    trackingPull = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - lastY
                if (!trackingPull && !canScrollVertically(-1) && dy > 0f && abs(dy) > 4f) {
                    trackingPull = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (trackingPull || pullOffsetPx > 0f) {
                    val dy = e.y - lastY
                    lastY = e.y
                    velocityTrackerY = dy
                    if (!canScrollVertically(-1) || pullOffsetPx > 0f) {
                        trackingPull = true
                        pullOffsetPx = (pullOffsetPx + dy).coerceAtLeast(0f)
                        onPullChanged?.invoke(pullOffsetPx)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (trackingPull || pullOffsetPx > 0f) {
                    onPullEnd?.invoke(pullOffsetPx, velocityTrackerY)
                    trackingPull = false
                    lastY = e.y
                    return true
                }
            }
        }
        lastY = e.y
        return super.onTouchEvent(e)
    }
}

fun buildDrawerRows(apps: List<LauncherApp>, showSearch: Boolean): List<DrawerListItem> = buildList {
    add(DrawerListItem.Header)
    if (showSearch) add(DrawerListItem.Search)
    apps.forEach { add(DrawerListItem.App(it)) }
}
