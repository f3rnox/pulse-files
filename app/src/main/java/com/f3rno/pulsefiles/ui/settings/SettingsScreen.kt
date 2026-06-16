package com.f3rno.pulsefiles.ui.settings

import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.data.AppSettings
import com.f3rno.pulsefiles.data.StartupTab
import com.f3rno.pulsefiles.data.ThemeMode
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.formatSize
import com.f3rno.pulsefiles.util.openAllFilesAccessSettings
import com.f3rno.pulsefiles.util.primaryStoragePath
import java.io.File

enum class SettingsDestination {
    ROOT, BROWSE, CLEAN, APPEARANCE, STORAGE, ABOUT, IGNORED_LARGE_FILES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onRequestFolderPicker: ((String) -> Unit) -> Unit = {}
) {
    var destination by remember { mutableStateOf(SettingsDestination.ROOT) }

    BackHandler(enabled = destination != SettingsDestination.ROOT) {
        destination = SettingsDestination.ROOT
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ignoredPaths by viewModel.ignoredPaths.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        if (destination == SettingsDestination.CLEAN || destination == SettingsDestination.IGNORED_LARGE_FILES) {
            viewModel.refreshIgnoredPaths()
        }
    }

    when (destination) {
        SettingsDestination.ROOT -> SettingsRootScreen(
            onNavigate = { destination = it }
        )
        SettingsDestination.BROWSE -> BrowseSettingsScreen(
            settings = settings,
            onBack = { destination = SettingsDestination.ROOT },
            onDefaultSort = viewModel::setDefaultSort,
            onShowHidden = viewModel::setShowHiddenByDefault,
            onRememberFolder = viewModel::setRememberLastFolder,
            onConfirmDelete = viewModel::setConfirmBeforeDelete,
            onRecursiveSearch = viewModel::setRecursiveSearch
        )
        SettingsDestination.CLEAN -> CleanSettingsScreen(
            settings = settings,
            ignoredCount = ignoredPaths.size,
            onBack = { destination = SettingsDestination.ROOT },
            onLargeThreshold = viewModel::setLargeFileThresholdMb,
            onScanDepth = viewModel::setScanDepth,
            onScanJunk = viewModel::setScanJunk,
            onScanDuplicates = viewModel::setScanDuplicates,
            onScanLarge = viewModel::setScanLargeFiles,
            onScanDownloads = viewModel::setScanDownloads,
            onScanApks = viewModel::setScanApks,
            onAutoScan = viewModel::setAutoScanOnLaunch,
            onManageIgnored = { destination = SettingsDestination.IGNORED_LARGE_FILES }
        )
        SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
            settings = settings,
            onBack = { destination = SettingsDestination.ROOT },
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor
        )
        SettingsDestination.STORAGE -> StorageSettingsScreen(
            settings = settings,
            onBack = { destination = SettingsDestination.ROOT },
            onDefaultTab = viewModel::setDefaultTab,
            onPickStartFolder = {
                onRequestFolderPicker { path ->
                    viewModel.setStartFolderPath(path)
                }
            },
            onClearStartFolder = viewModel::clearStartFolder
        )
        SettingsDestination.ABOUT -> AboutSettingsScreen(
            onBack = { destination = SettingsDestination.ROOT }
        )
        SettingsDestination.IGNORED_LARGE_FILES -> IgnoredLargeFilesScreen(
            paths = ignoredPaths,
            onBack = { destination = SettingsDestination.CLEAN },
            onUnignore = viewModel::unignoreLargeFiles,
            onClearAll = viewModel::clearIgnoredLargeFiles
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRootScreen(onNavigate: (SettingsDestination) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SettingsNavItem(
                    title = "Browse",
                    subtitle = "Sort, hidden files, search, delete",
                    icon = Icons.Outlined.Folder,
                    onClick = { onNavigate(SettingsDestination.BROWSE) }
                )
                HorizontalDivider()
            }
            item {
                SettingsNavItem(
                    title = "Clean",
                    subtitle = "Scan depth, thresholds, categories",
                    icon = Icons.Outlined.Search,
                    onClick = { onNavigate(SettingsDestination.CLEAN) }
                )
                HorizontalDivider()
            }
            item {
                SettingsNavItem(
                    title = "Appearance",
                    subtitle = "Theme and colors",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) }
                )
                HorizontalDivider()
            }
            item {
                SettingsNavItem(
                    title = "Storage & startup",
                    subtitle = "Permissions, start folder, default tab",
                    icon = Icons.Outlined.Storage,
                    onClick = { onNavigate(SettingsDestination.STORAGE) }
                )
                HorizontalDivider()
            }
            item {
                SettingsNavItem(
                    title = "About",
                    subtitle = "Version and app info",
                    icon = Icons.Outlined.Info,
                    onClick = { onNavigate(SettingsDestination.ABOUT) }
                )
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { content() }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onDefaultSort: (SortOrder) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onRememberFolder: (Boolean) -> Unit,
    onConfirmDelete: (Boolean) -> Unit,
    onRecursiveSearch: (Boolean) -> Unit
) {
    var showSortPicker by remember { mutableStateOf(false) }

    SettingsSubScreenScaffold(title = "Browse", onBack = onBack) {
        Column {
            ListItem(
                headlineContent = { Text("Default sort order") },
                supportingContent = { Text(sortLabel(settings.defaultSortOrder)) },
                modifier = Modifier.clickable { showSortPicker = true }
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Show hidden files",
                subtitle = "Include dot-files when browsing",
                checked = settings.showHiddenByDefault,
                onCheckedChange = onShowHidden
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Remember last folder",
                subtitle = "Reopen where you left off",
                checked = settings.rememberLastFolder,
                onCheckedChange = onRememberFolder
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Confirm before delete",
                subtitle = "Ask before deleting files in the browser",
                checked = settings.confirmBeforeDelete,
                onCheckedChange = onConfirmDelete
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Recursive search",
                subtitle = "Search subfolders from the current directory",
                checked = settings.recursiveSearch,
                onCheckedChange = onRecursiveSearch
            )
        }
    }

    if (showSortPicker) {
        SortPickerDialog(
            current = settings.defaultSortOrder,
            onSelect = {
                onDefaultSort(it)
                showSortPicker = false
            },
            onDismiss = { showSortPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanSettingsScreen(
    settings: AppSettings,
    ignoredCount: Int,
    onBack: () -> Unit,
    onLargeThreshold: (Int) -> Unit,
    onScanDepth: (Int) -> Unit,
    onScanJunk: (Boolean) -> Unit,
    onScanDuplicates: (Boolean) -> Unit,
    onScanLarge: (Boolean) -> Unit,
    onScanDownloads: (Boolean) -> Unit,
    onScanApks: (Boolean) -> Unit,
    onAutoScan: (Boolean) -> Unit,
    onManageIgnored: () -> Unit
) {
    SettingsSubScreenScaffold(title = "Clean", onBack = onBack) {
        Column {
            ListItem(
                headlineContent = { Text("Large file threshold") },
                supportingContent = { Text("${settings.largeFileThresholdMb} MB") }
            )
            Slider(
                value = settings.largeFileThresholdMb.toFloat(),
                onValueChange = { onLargeThreshold(it.toInt()) },
                valueRange = 5f..100f,
                steps = 18,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Scan depth") },
                supportingContent = { Text("${settings.scanDepth} folder levels") }
            )
            Slider(
                value = settings.scanDepth.toFloat(),
                onValueChange = { onScanDepth(it.toInt()) },
                valueRange = 1f..20f,
                steps = 18,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Scan for junk",
                subtitle = "Temporary, log, and cache files",
                checked = settings.scanJunk,
                onCheckedChange = onScanJunk
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Scan for duplicates",
                subtitle = "Find identical file copies",
                checked = settings.scanDuplicates,
                onCheckedChange = onScanDuplicates
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Scan for large files",
                subtitle = "Files above the size threshold",
                checked = settings.scanLargeFiles,
                onCheckedChange = onScanLarge
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Scan downloads",
                subtitle = "Files in the Downloads folder",
                checked = settings.scanDownloads,
                onCheckedChange = onScanDownloads
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Scan APK files",
                subtitle = "Installer packages on storage",
                checked = settings.scanApks,
                onCheckedChange = onScanApks
            )
            HorizontalDivider()
            SettingsSwitch(
                title = "Auto-scan on launch",
                subtitle = "Run a clean scan when the app opens",
                checked = settings.autoScanOnLaunch,
                onCheckedChange = onAutoScan
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Ignored large files") },
                supportingContent = {
                    Text(
                        if (ignoredCount == 0) "No files ignored" else "$ignoredCount file${if (ignoredCount == 1) "" else "s"} ignored"
                    )
                },
                leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onManageIgnored)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit
) {
    SettingsSubScreenScaffold(title = "Appearance", onBack = onBack) {
        Column {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(themeLabel(mode)) },
                    trailingContent = {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeMode(mode) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeMode(mode) }
                        )
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitch(
                title = "Dynamic color",
                subtitle = "Use Material You colors on Android 12+",
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onDefaultTab: (StartupTab) -> Unit,
    onPickStartFolder: () -> Unit,
    onClearStartFolder: () -> Unit
) {
    val context = LocalContext.current
    val storageRoot = primaryStoragePath(context)

    SettingsSubScreenScaffold(title = "Storage & startup", onBack = onBack) {
        Column {
            ListItem(
                headlineContent = { Text("All files access") },
                supportingContent = { Text("Required to browse and clean all storage") },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { openAllFilesAccessSettings(context) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Start folder") },
                supportingContent = {
                    Text(
                        text = when {
                            settings.startFolderPath.isEmpty() -> "Storage root"
                            else -> displayPath(settings.startFolderPath, storageRoot)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.clickable(onClick = onPickStartFolder)
            )
            if (settings.startFolderPath.isNotEmpty()) {
                TextButton(
                    onClick = onClearStartFolder,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Reset to storage root")
                }
            }
            HorizontalDivider()
            Text(
                text = "Default tab",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            StartupTab.entries.forEach { tab ->
                ListItem(
                    headlineContent = { Text(startupTabLabel(tab)) },
                    trailingContent = {
                        RadioButton(
                            selected = settings.defaultTab == tab,
                            onClick = { onDefaultTab(tab) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.defaultTab == tab,
                            onClick = { onDefaultTab(tab) }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    SettingsSubScreenScaffold(title = "About", onBack = onBack) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Pulse Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Version $versionName", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "A file browser and storage cleaner for your device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoredLargeFilesScreen(
    paths: List<String>,
    onBack: () -> Unit,
    onUnignore: (List<String>) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val storageRoot = primaryStoragePath(context)
    var selected by remember(paths) { mutableStateOf(setOf<String>()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ignored large files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { onUnignore(selected.toList()); selected = emptySet() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Unignore selected")
                        }
                    }
                    if (paths.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (paths.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No ignored files", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Files you ignore from the large files review appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(paths, key = { it }) { path ->
                    val file = File(path)
                    val isSelected = path in selected
                    ListItem(
                        headlineContent = {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    displayPath(path, storageRoot),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (file.exists()) {
                                    Text(
                                        formatSize(file.length()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = isSelected,
                                onCheckedChange = {
                                    selected = if (isSelected) selected - path else selected + path
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            selected = if (isSelected) selected - path else selected + path
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear ignored list?") },
            text = { Text("Ignored large files will appear in clean scans again.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    selected = emptySet()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SortPickerDialog(
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SortOrder.NAME_ASC to "Name (A-Z)",
        SortOrder.NAME_DESC to "Name (Z-A)",
        SortOrder.SIZE_DESC to "Size (largest)",
        SortOrder.SIZE_ASC to "Size (smallest)",
        SortOrder.DATE_DESC to "Date (newest)",
        SortOrder.DATE_ASC to "Date (oldest)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default sort order") },
        text = {
            Column {
                options.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = order == current, onClick = { onSelect(order) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = order == current, onClick = { onSelect(order) })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.NAME_ASC -> "Name (A-Z)"
    SortOrder.NAME_DESC -> "Name (Z-A)"
    SortOrder.SIZE_DESC -> "Size (largest)"
    SortOrder.SIZE_ASC -> "Size (smallest)"
    SortOrder.DATE_DESC -> "Date (newest)"
    SortOrder.DATE_ASC -> "Date (oldest)"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun startupTabLabel(tab: StartupTab): String = when (tab) {
    StartupTab.CLEAN -> "Clean"
    StartupTab.BROWSE -> "Browse"
    StartupTab.SETTINGS -> "Settings"
}

private fun displayPath(path: String, storageRoot: String): String =
    path.replace(storageRoot, "Storage")
        .replace(Environment.getExternalStorageDirectory().absolutePath, "Storage")
