package com.f3rno.pulsefiles.ui.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.f3rno.pulsefiles.model.FileCategory

/**
 * Maps a [FileCategory] to its Material icon.
 *
 * @param category The file category.
 * @return The icon representing the category.
 */
fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Outlined.Folder
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.VIDEO -> Icons.Outlined.VideoFile
    FileCategory.AUDIO -> Icons.Outlined.AudioFile
    FileCategory.DOCUMENT -> Icons.Outlined.Description
    FileCategory.ARCHIVE -> Icons.Outlined.Archive
    FileCategory.CODE -> Icons.Outlined.Code
    FileCategory.APK -> Icons.Outlined.Android
    FileCategory.OTHER -> Icons.Outlined.InsertDriveFile
}

/**
 * Picks a tint color for a [FileCategory] from the active color scheme.
 *
 * @param category The file category.
 * @return The tint color for the category icon.
 */
@Composable
fun colorFor(category: FileCategory): Color {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        FileCategory.FOLDER -> scheme.primary
        FileCategory.IMAGE -> scheme.tertiary
        FileCategory.VIDEO -> scheme.error
        FileCategory.AUDIO -> scheme.secondary
        else -> scheme.onSurfaceVariant
    }
}
