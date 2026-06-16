package com.f3rno.pulsefiles.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.model.FileCategory
import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.ui.viewer.AudioPlayer
import com.f3rno.pulsefiles.ui.viewer.ImageViewer
import com.f3rno.pulsefiles.ui.viewer.TextEditor
import com.f3rno.pulsefiles.util.categoryOf
import com.f3rno.pulsefiles.util.openAllFilesAccessSettings
import com.f3rno.pulsefiles.util.openFile
import com.f3rno.pulsefiles.util.primaryStoragePath
import com.f3rno.pulsefiles.util.shareFiles
import kotlinx.coroutines.launch
import java.io.File

/**
 * Top-level file browser screen wiring the view model state to the Material 3
 * UI: app bars, breadcrumbs, listing, dialogs and per-item actions.
 *
 * @param viewModel The browser view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(viewModel: FileBrowserViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = state.selectionMode || state.searchQuery != null || state.canNavigateUp) {
        when {
            state.selectionMode -> viewModel.clearSelection()
            state.searchQuery != null -> viewModel.setSearchQuery(null)
            state.canNavigateUp -> viewModel.navigateUp()
        }
    }

    var showCreateFolder by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var sheetTarget by remember { mutableStateOf<FileItem?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    var activeViewerTarget by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    activeViewerTarget?.let { target ->
        val category = categoryOf(target)
        val ext = target.extension
        when {
            category == FileCategory.IMAGE -> {
                ImageViewer(item = target, onClose = { activeViewerTarget = null })
                return
            }
            category == FileCategory.AUDIO -> {
                AudioPlayer(item = target, onClose = { activeViewerTarget = null })
                return
            }
            category == FileCategory.CODE || (category == FileCategory.DOCUMENT && (ext == "txt" || ext == "md")) -> {
                TextEditor(item = target, onClose = { activeViewerTarget = null })
                return
            }
            else -> {
                openFile(context, target)
                activeViewerTarget = null
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                state.selectionMode -> SelectionTopBar(
                    count = state.selected.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onCopy = viewModel::copySelection,
                    onCut = viewModel::cutSelection,
                    onDelete = { showDeleteDialog = true },
                    onShare = { shareFiles(context, state.selectedItems) }
                )
                state.searchQuery != null -> SearchTopBar(
                    query = state.searchQuery.orEmpty(),
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { viewModel.setSearchQuery(null) }
                )
                else -> MainTopBar(
                    canNavigateUp = state.canNavigateUp,
                    overflowOpen = overflowOpen,
                    onNavigateUp = viewModel::navigateUp,
                    onSearch = { viewModel.setSearchQuery("") },
                    onSort = { showSortDialog = true },
                    onOverflowToggle = { overflowOpen = it },
                    showHidden = state.showHidden,
                    onToggleHidden = {
                        overflowOpen = false
                        viewModel.toggleHidden()
                    }
                )
            }
        },
        floatingActionButton = {
            if (state.clipboard != null) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::paste,
                    icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                    text = { Text("Paste (${state.clipboard?.items?.size ?: 0})") }
                )
            } else if (!state.selectionMode) {
                FloatingActionButton(onClick = { showCreateFolder = true }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Breadcrumbs(
                path = state.currentPath,
                storageRoot = primaryStoragePath(context),
                onNavigate = viewModel::navigateTo
            )
            HorizontalDivider()
            when {
                state.isLoading -> Unit
                state.accessDenied -> AccessDeniedState(
                    canNavigateUp = state.canNavigateUp,
                    onNavigateUp = viewModel::navigateUp,
                    onOpenSettings = { openAllFilesAccessSettings(context) }
                )
                state.items.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(state.items, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            selected = item.path in state.selected,
                            onClick = {
                                if (state.selectionMode || item.isDirectory) {
                                    viewModel.onItemClick(item)
                                } else {
                                    activeViewerTarget = item
                                }
                            },
                            onLongClick = { viewModel.onItemLongClick(item) },
                            onMenuClick = { sheetTarget = item }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        NameInputDialog(
            title = "New folder",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = {
                viewModel.createFolder(it)
                showCreateFolder = false
            },
            onDismiss = { showCreateFolder = false }
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "Rename",
            initialValue = target.name,
            confirmLabel = "Rename",
            onConfirm = {
                viewModel.rename(target, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    if (showSortDialog) {
        SortDialog(
            current = state.sortOrder,
            onSelect = {
                viewModel.setSortOrder(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            count = state.selected.size,
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    detailsTarget?.let { DetailsDialog(item = it, onDismiss = { detailsTarget = null }) }

    sheetTarget?.let { target ->
        ItemActionsSheet(
            item = target,
            onDismiss = { sheetTarget = null },
            onOpen = {
                sheetTarget = null
                if (target.isDirectory) {
                    viewModel.navigateTo(target.path)
                } else {
                    activeViewerTarget = target
                }
            },
            onRename = {
                sheetTarget = null
                renameTarget = target
            },
            onShare = {
                sheetTarget = null
                shareFiles(context, listOf(target))
            },
            onDelete = {
                sheetTarget = null
                viewModel.onItemLongClick(target)
                showDeleteDialog = true
            },
            onCopy = {
                sheetTarget = null
                viewModel.onItemLongClick(target)
                viewModel.copySelection()
                scope.launch { snackbarHostState.showSnackbar("Copied. Navigate and tap Paste.") }
            },
            onCut = {
                sheetTarget = null
                viewModel.onItemLongClick(target)
                viewModel.cutSelection()
                scope.launch { snackbarHostState.showSnackbar("Cut. Navigate and tap Paste.") }
            },
            onDetails = {
                sheetTarget = null
                detailsTarget = target
            }
        )
    }
}

/**
 * Default top app bar shown while browsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    canNavigateUp: Boolean,
    overflowOpen: Boolean,
    onNavigateUp: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onOverflowToggle: (Boolean) -> Unit,
    showHidden: Boolean,
    onToggleHidden: () -> Unit
) {
    TopAppBar(
        title = { Text("Pulse Files") },
        navigationIcon = {
            if (canNavigateUp) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Up")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
            IconButton(onClick = onSort) { Icon(Icons.Outlined.Sort, contentDescription = "Sort") }
            IconButton(onClick = { onOverflowToggle(true) }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { onOverflowToggle(false) }) {
                DropdownMenuItem(
                    text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                    onClick = onToggleHidden
                )
            }
        }
    )
}

/**
 * Contextual top app bar shown when entries are selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
        },
        actions = {
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, contentDescription = "Cut") }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, contentDescription = "Select all") }
        }
    )
}

/**
 * Search-mode top app bar with an inline text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search this folder") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close search") }
        }
    )
}

/**
 * Horizontally scrollable breadcrumb trail of the current path.
 *
 * @param path The absolute current path.
 * @param storageRoot The primary storage root used for the first breadcrumb.
 * @param onNavigate Invoked with the path to jump to.
 */
@Composable
private fun Breadcrumbs(path: String, storageRoot: String, onNavigate: (String) -> Unit) {
    if (path.isEmpty()) return
    val crumbs = remember(path, storageRoot) { buildCrumbs(path, storageRoot) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, crumb ->
            Text(
                text = crumb.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (index == crumbs.lastIndex) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onNavigate(crumb.path) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            if (index != crumbs.lastIndex) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Crumb(val label: String, val path: String)

/**
 * Builds breadcrumb segments from the current path relative to storage root.
 *
 * @param path The absolute current path.
 * @param root The storage root path.
 * @return Ordered breadcrumb segments.
 */
private fun buildCrumbs(path: String, root: String): List<Crumb> {
    val normalizedPath = runCatching { File(path).canonicalPath }.getOrDefault(path)
    val normalizedRoot = runCatching { File(root).canonicalPath }.getOrDefault(root)
    val result = mutableListOf(Crumb("Storage", normalizedRoot))
    if (normalizedPath.startsWith(normalizedRoot) && normalizedPath.length > normalizedRoot.length) {
        val relative = normalizedPath.removePrefix(normalizedRoot).trim('/')
        var acc = normalizedRoot
        relative.split("/").filter { it.isNotEmpty() }.forEach { part ->
            acc = "$acc/$part"
            result.add(Crumb(part, acc))
        }
    }
    return result
}

/**
 * Bottom sheet listing actions for a single entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemActionsSheet(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDetails: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        HorizontalDivider()
        SheetAction(Icons.AutoMirrored.Outlined.ArrowBack, "Open", onOpen)
        SheetAction(Icons.Outlined.ContentCopy, "Copy", onCopy)
        SheetAction(Icons.Outlined.ContentCut, "Move", onCut)
        SheetAction(Icons.Outlined.DriveFileRenameOutline, "Rename", onRename)
        if (!item.isDirectory) SheetAction(Icons.Outlined.Share, "Share", onShare)
        SheetAction(Icons.Outlined.Info, "Details", onDetails)
        SheetAction(Icons.Outlined.Delete, "Delete", onDelete)
        Box(modifier = Modifier.padding(bottom = 24.dp))
    }
}

/**
 * A single tappable row inside the actions bottom sheet.
 */
@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Placeholder shown when a directory could not be read.
 */
@Composable
private fun AccessDeniedState(
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Cannot read this folder",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Pulse Files does not have access or this folder is strictly restricted by the system.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (canNavigateUp) {
                    OutlinedButton(onClick = onNavigateUp) {
                        Text("Go Back")
                    }
                }
                Button(onClick = onOpenSettings) {
                    Text("Grant Access")
                }
            }
        }
    }
}

/**
 * Placeholder shown when a directory has no visible entries.
 */
@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
