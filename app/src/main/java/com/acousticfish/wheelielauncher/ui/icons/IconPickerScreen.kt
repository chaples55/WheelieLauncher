package com.acousticfish.wheelielauncher.ui.icons

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.acousticfish.wheelielauncher.R
import com.acousticfish.wheelielauncher.icons.IconPackDrawable
import com.acousticfish.wheelielauncher.icons.IconPackInfo
import com.acousticfish.wheelielauncher.ui.components.AppIconImage
import com.acousticfish.wheelielauncher.ui.components.rememberResolvedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    componentName: ComponentName,
    iconPacks: List<IconPackInfo>,
    selectedPack: String?,
    loadDrawables: suspend (String, String) -> List<IconPackDrawable>,
    loadDrawable: (String, String) -> Drawable?,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var mode by remember { mutableStateOf("menu") } // menu | packList | packBrowse
    var pack by remember { mutableStateOf(selectedPack) }
    var query by remember { mutableStateOf("") }
    var drawables by remember { mutableStateOf<List<IconPackDrawable>>(emptyList()) }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            onPick(uri.toString())
        }
    }

    LaunchedEffect(pack, query, mode) {
        if (mode == "packBrowse" && pack != null) {
            drawables = withContext(Dispatchers.IO) {
                loadDrawables(pack!!, query)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_icon)) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (mode) {
                            "packBrowse" -> mode = "packList"
                            "packList" -> mode = "menu"
                            else -> onBack()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (mode) {
            "menu" -> {
                Column(modifier = Modifier.padding(padding)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.from_icon_pack)) },
                        modifier = Modifier.clickable {
                            mode = if (pack != null) "packBrowse" else "packList"
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.from_gallery)) },
                        modifier = Modifier.clickable {
                            galleryPicker.launch(arrayOf("image/*"))
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.reset_icon)) },
                        modifier = Modifier.clickable { onPick(null) },
                    )
                }
            }
            "packList" -> {
                Column(modifier = Modifier.padding(padding)) {
                    iconPacks.forEach { info ->
                        ListItem(
                            headlineContent = { Text(info.label) },
                            modifier = Modifier.clickable {
                                pack = info.packageName
                                mode = "packBrowse"
                            },
                        )
                    }
                    if (iconPacks.isEmpty()) {
                        Text(
                            text = "No icon packs installed",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            "packBrowse" -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_icons)) },
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(drawables, key = { it.drawableName }) { item ->
                            val drawable = rememberResolvedIcon(pack to item.drawableName) {
                                pack?.let { loadDrawable(it, item.drawableName) }
                            }
                            AppIconImage(
                                drawable = drawable,
                                contentDescription = item.name,
                                size = 48.dp,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        val p = pack ?: return@clickable
                                        onPick("$p/${item.drawableName}")
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
