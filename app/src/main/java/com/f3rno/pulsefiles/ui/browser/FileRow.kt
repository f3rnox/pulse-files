package com.f3rno.pulsefiles.ui.browser

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.util.categoryOf
import com.f3rno.pulsefiles.util.formatDate
import com.f3rno.pulsefiles.util.formatSize

/**
 * Renders a single file or folder row with an icon, metadata, selection state
 * and an overflow action button.
 *
 * @param item The entry to display.
 * @param selected Whether the entry is selected.
 * @param onClick Invoked on tap.
 * @param onLongClick Invoked on long press.
 * @param subtitlePrefix Optional leading text for the subtitle (e.g. parent folder in search results).
 * @param onMenuClick Invoked when the overflow button is tapped.
 */
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    subtitlePrefix: String? = null
) {
    val category = categoryOf(item)
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Surface(color = container) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {}
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = iconFor(category),
                        contentDescription = null,
                        tint = colorFor(category)
                    )
                }
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle(item, subtitlePrefix),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
            }
        }
    }
}

/**
 * Builds the secondary line for a row from its metadata.
 *
 * @param item The entry.
 * @return The subtitle string.
 */
private fun subtitle(item: FileItem, prefix: String?): String {
    val meta = if (item.isDirectory) {
        item.childCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Folder"
    } else {
        formatSize(item.size)
    }
    val details = "$meta  •  ${formatDate(item.lastModified)}"
    return if (prefix != null) "$prefix  •  $details" else details
}
