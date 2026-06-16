package com.f3rno.pulsefiles.ui.apps

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.model.AppItem
import com.f3rno.pulsefiles.util.formatDate
import com.f3rno.pulsefiles.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onClose: () -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Automatically and silently refresh the app list when user resumes the screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadApps(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onUninstallIntentFinished(result.resultCode)
    }

    var showSortDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    // Intercept back button to either close search or close screen
    BackHandler {
        when {
            state.selectedPackages.isNotEmpty() -> viewModel.clearSelection()
            searchActive -> {
                searchActive = false
                viewModel.setSearchQuery(null)
            }
            else -> onClose()
        }
    }

    // Trigger next uninstall in the queue
    LaunchedEffect(state.currentlyUninstalling) {
        val currentPkg = state.currentlyUninstalling
        if (currentPkg != null) {
            val intent = viewModel.getNextUninstallIntent()
            if (intent != null) {
                try {
                    uninstallLauncher.launch(intent)
                } catch (e: Exception) {
                    viewModel.onUninstallIntentFailed(
                        "Could not start uninstall for $currentPkg: ${e.localizedMessage}"
                    )
                }
            } else {
                viewModel.onUninstallIntentFailed(
                    "No system handler available to uninstall $currentPkg"
                )
            }
        }
    }

    // Show Snackbars for success/error
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    // Filter and sort apps based on UI state
    val filteredApps = remember(state.apps, state.searchQuery, state.showSystemApps, state.sortOrder) {
        var list = state.apps

        // System app filter:
        // When showSystemApps is false, we show all user apps and only launchable system apps.
        // When showSystemApps is true, we show all packages (both user and system).
        if (!state.showSystemApps) {
            list = list.filter { !it.isSystemApp || it.isLaunchable }
        }

        // Search query filter
        val query = state.searchQuery
        if (!query.isNullOrBlank()) {
            list = list.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        // Sort order
        when (state.sortOrder) {
            AppSortOrder.NAME_ASC -> list.sortedBy { it.label.lowercase() }
            AppSortOrder.NAME_DESC -> list.sortedByDescending { it.label.lowercase() }
            AppSortOrder.SIZE_DESC -> list.sortedByDescending { it.apkSize }
            AppSortOrder.INSTALL_DATE_DESC -> list.sortedByDescending { it.installTime }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectedPackages.isNotEmpty()) {
                // Selection Top Bar
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    title = { Text("${state.selectedPackages.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.extractSelectedApps() }) {
                            Icon(Icons.Outlined.Backup, contentDescription = "Backup/Extract APK")
                        }
                        IconButton(onClick = {
                            viewModel.startBatchUninstall(state.selectedPackages.toList())
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Batch Uninstall")
                        }
                        IconButton(onClick = { viewModel.selectAll(filteredApps) }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select All")
                        }
                    }
                )
            } else if (searchActive) {
                // Search Top Bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery(null)
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        TextField(
                            value = state.searchQuery.orEmpty(),
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search apps...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    },
                    actions = {
                        if (!state.searchQuery.isNullOrEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear text")
                            }
                        }
                    }
                )
            } else {
                // Normal Top Bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("App Manager") },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                        }
                        var overflowExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.showSystemApps) "Hide system apps" else "Show system apps") },
                                leadingIcon = {
                                    Icon(
                                        if (state.showSystemApps) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.toggleShowSystemApps()
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Android,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "No applications found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = state.selectedPackages.contains(app.packageName)
                        AppRow(
                            app = app,
                            isSelected = isSelected,
                            onClick = {
                                if (state.selectedPackages.isNotEmpty()) {
                                    viewModel.toggleSelectApp(app.packageName)
                                } else {
                                    // Custom actions bottom sheet for single app
                                    // Or simply toggle selection to make multi-select easy
                                    viewModel.toggleSelectApp(app.packageName)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelectApp(app.packageName)
                            }
                        )
                    }
                }
            }

            // Overlay during extraction progress
            if (state.isExtracting) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Extracting APKs") },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(state.extractionProgress)
                        }
                    },
                    confirmButton = {}
                )
            }

            // Non-modal progress while the system uninstall UI is shown
            if (state.isUninstalling) {
                val currentPkgName = state.currentlyUninstalling
                val app = state.apps.find { it.packageName == currentPkgName }
                val appName = app?.label ?: currentPkgName ?: "..."
                val opensSettings = app?.canUninstall == false
                val actionText = if (opensSettings) {
                    "Opening settings to disable $appName..."
                } else {
                    "Confirm uninstall for $appName in the system dialog"
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (state.batchUninstallTotal > 0) {
                                state.batchUninstallProgress.toFloat() / state.batchUninstallTotal.toFloat()
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${state.batchUninstallProgress} of ${state.batchUninstallTotal} completed",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort Applications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        AppSortOrder.NAME_ASC to "Name (A-Z)",
                        AppSortOrder.NAME_DESC to "Name (Z-A)",
                        AppSortOrder.SIZE_DESC to "APK Size",
                        AppSortOrder.INSTALL_DATE_DESC to "Install Date"
                    )
                    options.forEach { (order, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSortOrder(order)
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            RadioButton(
                                selected = state.sortOrder == order,
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortDialog = false
                                }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystemApp && !app.isLaunchable) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SYSTEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Size: ${formatSize(app.apkSize)} • Updated: ${formatDate(app.installTime)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() }
        )
    }
}
