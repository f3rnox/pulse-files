package com.f3rno.pulsefiles.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.data.StartupTab
import com.f3rno.pulsefiles.ui.apps.AppManagerScreen
import com.f3rno.pulsefiles.ui.browser.FileBrowserScreen
import com.f3rno.pulsefiles.ui.browser.FolderPickerDialog
import com.f3rno.pulsefiles.ui.clean.CleanScreen
import com.f3rno.pulsefiles.util.primaryStoragePath
import com.f3rno.pulsefiles.ui.settings.SettingsScreen
import com.f3rno.pulsefiles.ui.settings.SettingsViewModel

enum class AppTab {
    CLEAN, BROWSE, APPS, SETTINGS
}

private fun StartupTab.toAppTab(): AppTab = when (this) {
    StartupTab.CLEAN -> AppTab.CLEAN
    StartupTab.BROWSE -> AppTab.BROWSE
    StartupTab.SETTINGS -> AppTab.SETTINGS
}

@Composable
fun MainScreen(settingsViewModel: SettingsViewModel = viewModel()) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val defaultTab = remember(settings.defaultTab) { settings.defaultTab.toAppTab() }
    var currentTab by remember(defaultTab) { mutableStateOf(defaultTab) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.CLEAN,
                    onClick = { currentTab = AppTab.CLEAN },
                    icon = {
                        Icon(
                            Icons.Outlined.CleaningServices,
                            contentDescription = "Clean"
                        )
                    },
                    label = { Text("Clean") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.BROWSE,
                    onClick = { currentTab = AppTab.BROWSE },
                    icon = {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = "Browse"
                        )
                    },
                    label = { Text("Browse") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.APPS,
                    onClick = { currentTab = AppTab.APPS },
                    icon = {
                        Icon(
                            Icons.Outlined.Apps,
                            contentDescription = "Apps"
                        )
                    },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                AppTab.CLEAN -> CleanScreen()
                AppTab.BROWSE -> FileBrowserScreen()
                AppTab.APPS -> AppManagerScreen(onClose = { currentTab = AppTab.BROWSE })
                AppTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onRequestFolderPicker = { onSelected ->
                        folderPickerCallback = onSelected
                        showFolderPicker = true
                    }
                )
            }
        }
    }

    if (showFolderPicker) {
        val context = LocalContext.current
        FolderPickerDialog(
            title = "Choose start folder",
            confirmLabel = "Select",
            initialPath = primaryStoragePath(context),
            onSelect = { path ->
                folderPickerCallback?.invoke(path)
                folderPickerCallback = null
                showFolderPicker = false
            },
            onDismiss = {
                folderPickerCallback = null
                showFolderPicker = false
            }
        )
    }
}

/**
 * Resolves whether dark theme should be used from user settings.
 */
@Composable
fun shouldUseDarkTheme(settingsViewModel: SettingsViewModel): Boolean {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    return when (settings.themeMode) {
        com.f3rno.pulsefiles.data.ThemeMode.LIGHT -> false
        com.f3rno.pulsefiles.data.ThemeMode.DARK -> true
        com.f3rno.pulsefiles.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}
