package com.acousticfish.wheelielauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.acousticfish.wheelielauncher.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(
    hiddenPackages: Set<String>,
    packageLabels: Map<String, String>,
    onUnhide: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hidden_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (hiddenPackages.isEmpty()) {
                item {
                    Text("No hidden apps", modifier = Modifier.padding(16.dp))
                }
            }
            items(hiddenPackages.toList().sorted()) { pkg ->
                ListItem(
                    headlineContent = { Text(packageLabels[pkg] ?: pkg) },
                    supportingContent = { Text("Tap to unhide") },
                    modifier = Modifier.clickable { onUnhide(pkg) },
                )
            }
        }
    }
}
