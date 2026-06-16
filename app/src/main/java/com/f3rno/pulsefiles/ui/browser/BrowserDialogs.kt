package com.f3rno.pulsefiles.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.categoryOf
import com.f3rno.pulsefiles.util.formatDate
import com.f3rno.pulsefiles.util.formatSize

/**
 * Dialog prompting for a name, reused for creating folders and renaming.
 *
 * @param title The dialog title.
 * @param initialValue Prefilled text value.
 * @param confirmLabel Label for the confirm button.
 * @param onConfirm Invoked with the trimmed entered name.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun NameInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Confirmation dialog before deleting one or more entries.
 *
 * @param count The number of entries to delete.
 * @param onConfirm Invoked when deletion is confirmed.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete") },
        text = { Text("Delete $count item${if (count == 1) "" else "s"}? This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Sort order picker dialog.
 *
 * @param current The active sort order.
 * @param onSelect Invoked with the chosen order.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun SortDialog(current: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
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
        title = { Text("Sort by") },
        text = {
            Column {
                options.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = order == current, onClick = { onSelect(order) })
                            .padding(vertical = 12.dp),
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

/**
 * Read-only details dialog for a single file entry.
 *
 * @param item The entry to describe.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun DetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Type", if (item.isDirectory) "Folder" else categoryOf(item).name.lowercase())
                if (!item.isDirectory) DetailRow("Size", formatSize(item.size))
                if (item.isDirectory && item.childCount != null) DetailRow("Items", item.childCount.toString())
                DetailRow("Modified", formatDate(item.lastModified))
                DetailRow("Path", item.path)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * A single label/value row used inside the details dialog.
 *
 * @param label The field label.
 * @param value The field value.
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}
