package com.f3rno.pulsefiles.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.f3rno.pulsefiles.util.listChildren
import com.f3rno.pulsefiles.util.primaryStoragePath
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Full-screen folder browser for choosing a move/copy destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDestinationSheet(
    title: String,
    confirmLabel: String,
    initialPath: String,
    isDestinationValid: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rootPath = primaryStoragePath(context)
    var currentPath by remember(initialPath) { mutableStateOf(initialPath) }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val currentDir = File(currentPath)
            val folders = remember(currentPath) {
                (listChildren(currentDir) ?: emptyList())
                    .filter { it.isDirectory && !it.name.startsWith(".") }
                    .sortedBy { it.name.lowercase() }
            }
            val destinationValid = isDestinationValid(currentPath)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close")
                            }
                        }
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = displayFolderPath(currentPath, rootPath),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (!destinationValid) {
                            Text(
                                text = "Cannot move items into this folder",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = { onConfirm(currentPath) },
                            enabled = destinationValid,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            ) { padding ->
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (currentPath != rootPath) {
                        item {
                            ListItem(
                                headlineContent = { Text("..") },
                                supportingContent = { Text("Parent folder") },
                                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    currentPath = currentDir.parentFile?.absolutePath ?: rootPath
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    items(folders, key = { it.absolutePath }) { folder ->
                        val folderPath = folder.absolutePath
                        val canSelect = isDestinationValid(folderPath)
                        ListItem(
                            headlineContent = {
                                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                if (!canSelect) {
                                    Text(
                                        "Invalid destination",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { currentPath = folderPath }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Compact folder picker used in settings dialogs.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    initialPath: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    FolderDestinationSheet(
        title = title,
        confirmLabel = confirmLabel,
        initialPath = initialPath,
        isDestinationValid = { true },
        onConfirm = onSelect,
        onDismiss = onDismiss
    )
}

private fun displayFolderPath(path: String, rootPath: String): String =
    path.removePrefix(rootPath).trimStart('/').ifEmpty { "Storage" }
