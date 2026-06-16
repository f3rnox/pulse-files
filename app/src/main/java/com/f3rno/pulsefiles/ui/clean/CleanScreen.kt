package com.f3rno.pulsefiles.ui.clean

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.util.formatDate
import com.f3rno.pulsefiles.util.formatSize
import com.f3rno.pulsefiles.util.normalizeStoragePath
import java.io.File

private fun fileSelectionPath(file: File): String = normalizeStoragePath(file.absolutePath)

private fun displayFolderPath(folderPath: String): String =
    folderPath.replace(Environment.getExternalStorageDirectory().absolutePath, "Storage")

private data class FolderFileGroup(
    val folderPath: String,
    val files: List<File>
)

private fun groupFilesByFolder(files: List<File>): List<FolderFileGroup> =
    files.groupBy { it.parent ?: "" }
        .map { (folder, folderFiles) ->
            FolderFileGroup(folder, folderFiles.sortedByDescending { it.length() })
        }
        .sortedByDescending { group -> group.files.sumOf { it.length() } }

enum class ReviewType {
    NONE, DUPLICATES, LARGE, DOWNLOADS, APKS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(
    viewModel: CleanViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeReview by remember { mutableStateOf(ReviewType.NONE) }

    BackHandler(enabled = activeReview != ReviewType.NONE) {
        activeReview = ReviewType.NONE
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (activeReview == ReviewType.NONE) {
                TopAppBar(
                    title = { Text("Clean") }
                )
            }
        }
    ) { padding ->
        if (activeReview != ReviewType.NONE) {
            ReviewFilesScreen(
                modifier = Modifier.fillMaxSize(),
                reviewType = activeReview,
                state = state,
                onClose = { activeReview = ReviewType.NONE },
                onDeleteFiles = { paths ->
                    viewModel.deleteFiles(paths)
                },
                onIgnoreFiles = { paths ->
                    viewModel.ignoreLargeFiles(paths)
                }
            )
        } else {
            PullToRefreshBox(
                isRefreshing = state.isScanning,
                onRefresh = { viewModel.startScan() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Storage Progress Card
                    item {
                        StorageInfoCard(
                            totalSpace = state.totalSpace,
                            freeSpace = state.freeSpace,
                            usedSpace = state.usedSpace
                        )
                    }

                    // 2. Scan Status or Scan Options
                    if (state.isScanning) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Scanning storage...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                    // 3. Junk Clean Card
                    if (state.junkFiles.isNotEmpty()) {
                        item {
                            CleanSuggestionCard(
                                title = "Junk files",
                                description = "Clean temporary app files and log files taking up unnecessary space.",
                                size = state.junkSize,
                                icon = Icons.Outlined.CleaningServices,
                                buttonLabel = "Clean up",
                                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                accentColor = MaterialTheme.colorScheme.error,
                                onClick = { viewModel.cleanJunk() }
                            )
                        }
                    }

                    // 4. Duplicate Files Card
                    if (state.duplicateFiles.isNotEmpty()) {
                        item {
                            CleanSuggestionCard(
                                title = "Duplicate files",
                                description = "Delete identical copies of files to save space.",
                                size = state.duplicatesSize,
                                icon = Icons.Outlined.ContentCopy,
                                buttonLabel = "Select and clean",
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                onClick = { activeReview = ReviewType.DUPLICATES }
                            )
                        }
                    }

                    // 5. Large Files Card
                    if (state.largeFiles.isNotEmpty()) {
                        item {
                            CleanSuggestionCard(
                                title = "Large files",
                                description = "Review and delete files that take up a lot of space.",
                                size = state.largeSize,
                                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                buttonLabel = "Review large files",
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                onClick = { activeReview = ReviewType.LARGE }
                            )
                        }
                    }

                    // 6. Downloads Card
                    if (state.downloadFiles.isNotEmpty()) {
                        item {
                            CleanSuggestionCard(
                                title = "Downloaded files",
                                description = "Review and delete files inside your Downloads folder.",
                                size = state.downloadSize,
                                icon = Icons.Outlined.Download,
                                buttonLabel = "Review downloads",
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { activeReview = ReviewType.DOWNLOADS }
                            )
                        }
                    }

                    // 7. APKs Card
                    if (state.apkFiles.isNotEmpty()) {
                        item {
                            CleanSuggestionCard(
                                title = "APK installer files",
                                description = "Delete APK files for apps you've already installed.",
                                size = state.apkSize,
                                icon = Icons.Outlined.FolderZip,
                                buttonLabel = "Review APKs",
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { activeReview = ReviewType.APKS }
                            )
                        }
                    }

                    // No suggestions fallback
                    if (state.junkFiles.isEmpty() &&
                        state.duplicateFiles.isEmpty() &&
                        state.largeFiles.isEmpty() &&
                        state.downloadFiles.isEmpty() &&
                        state.apkFiles.isEmpty()
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        "Your storage is clean!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "No unneeded files found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { viewModel.startScan() }) {
                                        Text("Scan Again")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun StorageInfoCard(totalSpace: Long, freeSpace: Long, usedSpace: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val ratio = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatSize(usedSpace)} used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatSize(totalSpace)} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CleanSuggestionCard(
    title: String,
    description: String,
    size: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonLabel: String,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatSize(size),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = backgroundColor
                    )
                ) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFilesScreen(
    modifier: Modifier = Modifier,
    reviewType: ReviewType,
    state: CleanUiState,
    onClose: () -> Unit,
    onDeleteFiles: (List<String>) -> Unit,
    onIgnoreFiles: ((List<String>) -> Unit)? = null
) {
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    BackHandler(onBack = onClose)

    val title = when (reviewType) {
        ReviewType.DUPLICATES -> "Duplicate files"
        ReviewType.LARGE -> "Large files"
        ReviewType.DOWNLOADS -> "Downloaded files"
        ReviewType.APKS -> "APK installer files"
        else -> ""
    }

    val allSelectableFiles = remember(reviewType, state) {
        when (reviewType) {
            ReviewType.DUPLICATES -> state.duplicateFiles.flatMap { it.duplicates }
            ReviewType.LARGE -> state.largeFiles
            ReviewType.DOWNLOADS -> state.downloadFiles
            ReviewType.APKS -> state.apkFiles
            else -> emptyList()
        }.filter { it.exists() }
    }

    val selectedFiles = remember(allSelectableFiles, selectedPaths) {
        allSelectableFiles.filter { fileSelectionPath(it) in selectedPaths }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (allSelectableFiles.isNotEmpty()) {
                            val isAllSelected = selectedPaths.containsAll(
                                allSelectableFiles.map { fileSelectionPath(it) }
                            )
                            IconButton(
                                onClick = {
                                    selectedPaths = if (isAllSelected) {
                                        emptySet()
                                    } else {
                                        allSelectableFiles.map { fileSelectionPath(it) }.toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.SelectAll,
                                    contentDescription = if (isAllSelected) "Deselect all" else "Select all"
                                )
                            }
                        }
                        if (selectedPaths.isNotEmpty()) {
                            if (reviewType == ReviewType.LARGE && onIgnoreFiles != null) {
                                IconButton(
                                    onClick = {
                                        onIgnoreFiles(selectedPaths.toList())
                                        selectedPaths = emptySet()
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.VisibilityOff,
                                        contentDescription = "Ignore selected files"
                                    )
                                }
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (selectedPaths.isNotEmpty()) {
                    val totalSelectedSize = selectedFiles.sumOf { it.length() }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedPaths.size} files selected",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = formatSize(totalSelectedSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (reviewType == ReviewType.LARGE && onIgnoreFiles != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onIgnoreFiles(selectedPaths.toList())
                                            selectedPaths = emptySet()
                                        }
                                    ) {
                                        Text("Ignore")
                                    }
                                }
                                Button(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            when (reviewType) {
                ReviewType.DUPLICATES -> {
                    val duplicates = state.duplicateFiles.filter { group ->
                        group.original.exists() || group.duplicates.any { it.exists() }
                    }
                    val duplicatePaths = allSelectableFiles.map { fileSelectionPath(it) }
                    val allDuplicatesSelected = duplicatePaths.isNotEmpty() &&
                        selectedPaths.containsAll(duplicatePaths)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (duplicatePaths.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${duplicatePaths.size} duplicate copies",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            selectedPaths = if (allDuplicatesSelected) {
                                                selectedPaths - duplicatePaths.toSet()
                                            } else {
                                                selectedPaths + duplicatePaths.toSet()
                                            }
                                        }
                                    ) {
                                        Text(if (allDuplicatesSelected) "Deselect all" else "Select all")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                        items(duplicates, key = { it.original.absolutePath }) { group ->
                            DuplicateGroupItem(
                                group = group,
                                selectedPaths = selectedPaths,
                                onToggleFileSelection = { file ->
                                    val path = fileSelectionPath(file)
                                    selectedPaths = if (path in selectedPaths) {
                                        selectedPaths - path
                                    } else {
                                        selectedPaths + path
                                    }
                                },
                                onSelectAllCopies = { copies ->
                                    val paths = copies.map { fileSelectionPath(it) }.toSet()
                                    val allSelected = paths.isNotEmpty() && paths.all { it in selectedPaths }
                                    selectedPaths = if (allSelected) {
                                        selectedPaths - paths
                                    } else {
                                        selectedPaths + paths
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                ReviewType.LARGE -> {
                    val filesList = state.largeFiles.filter { it.exists() }
                    val folderGroups = remember(filesList) { groupFilesByFolder(filesList) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        items(folderGroups, key = { it.folderPath }) { group ->
                            FolderFileGroupItem(
                                group = group,
                                expanded = expandedFolders[group.folderPath] ?: true,
                                onExpandedChange = { expandedFolders[group.folderPath] = it },
                                selectedPaths = selectedPaths,
                                onToggleFileSelection = { file ->
                                    val path = fileSelectionPath(file)
                                    selectedPaths = if (path in selectedPaths) {
                                        selectedPaths - path
                                    } else {
                                        selectedPaths + path
                                    }
                                },
                                onSelectAllInFolder = { files ->
                                    val paths = files.map { fileSelectionPath(it) }.toSet()
                                    val allSelected = paths.isNotEmpty() && paths.all { it in selectedPaths }
                                    selectedPaths = if (allSelected) {
                                        selectedPaths - paths
                                    } else {
                                        selectedPaths + paths
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                else -> {
                    val filesList = when (reviewType) {
                        ReviewType.DOWNLOADS -> state.downloadFiles
                        ReviewType.APKS -> state.apkFiles
                        else -> emptyList()
                    }.filter { it.exists() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        items(filesList, key = { it.absolutePath }) { file ->
                            FileListItem(
                                file = file,
                                isSelected = fileSelectionPath(file) in selectedPaths,
                                onToggleSelection = {
                                    val path = fileSelectionPath(file)
                                    selectedPaths = if (path in selectedPaths) {
                                        selectedPaths - path
                                    } else {
                                        selectedPaths + path
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete files?") },
            text = { Text("These files will be permanently deleted from your device storage. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteFiles(selectedPaths.toList())
                        selectedPaths = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderFileGroupItem(
    group: FolderFileGroup,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedPaths: Set<String>,
    onToggleFileSelection: (File) -> Unit,
    onSelectAllInFolder: (List<File>) -> Unit
) {
    val groupPaths = group.files.map { fileSelectionPath(it) }
    val allSelected = groupPaths.isNotEmpty() && groupPaths.all { it in selectedPaths }
    val selectedCount = groupPaths.count { it in selectedPaths }
    val totalSize = group.files.sumOf { it.length() }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse folder" else "Expand folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayFolderPath(group.folderPath),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("${group.files.size} file${if (group.files.size == 1) "" else "s"} • ${formatSize(totalSize)}")
                        if (selectedCount > 0) {
                            append(" • $selectedCount selected")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (group.files.isNotEmpty()) {
                TextButton(onClick = { onSelectAllInFolder(group.files) }) {
                    Text(if (allSelected) "Deselect all" else "Select all")
                }
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            group.files.forEach { file ->
                FileListItem(
                    file = file,
                    isSelected = fileSelectionPath(file) in selectedPaths,
                    showFolderPath = false,
                    onToggleSelection = { onToggleFileSelection(file) }
                )
            }
        }
    }
}

@Composable
fun DuplicateGroupItem(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    onToggleFileSelection: (File) -> Unit,
    onSelectAllCopies: (List<File>) -> Unit = {}
) {
    val existingCopies = group.duplicates.filter { it.exists() }
    val copyPaths = existingCopies.map { fileSelectionPath(it) }
    val allCopiesSelected = copyPaths.isNotEmpty() && copyPaths.all { it in selectedPaths }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.original.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (existingCopies.isNotEmpty()) {
                TextButton(onClick = { onSelectAllCopies(existingCopies) }) {
                    Text(if (allCopiesSelected) "Deselect copies" else "Select copies")
                }
            }
        }
        Text(
            text = "Size: ${formatSize(group.size)} each",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show the original (unselected by default)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = fileSelectionPath(group.original) in selectedPaths,
                onCheckedChange = { onToggleFileSelection(group.original) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Original file",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = (group.original.parent ?: "").let(::displayFolderPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Show duplicates (recommending deletion)
        group.duplicates.forEachIndexed { index, duplicate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = fileSelectionPath(duplicate) in selectedPaths,
                    onCheckedChange = { onToggleFileSelection(duplicate) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Duplicate copy ${if (group.duplicates.size > 1) index + 1 else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = (duplicate.parent ?: "").let(::displayFolderPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    file: File,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    showFolderPath: Boolean = true
) {
    ListItem(
        headlineContent = {
            Text(
                text = file.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (showFolderPath) {
                    Text(
                        text = displayFolderPath(file.parent ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "${formatSize(file.length())} • ${formatDate(file.lastModified())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        },
        modifier = Modifier.clickable { onToggleSelection() }
    )
}
