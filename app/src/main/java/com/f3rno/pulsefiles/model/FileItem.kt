package com.f3rno.pulsefiles.model

import java.io.File

/**
 * Represents a single file or directory entry shown in the browser.
 *
 * @property file The backing [File] on disk.
 * @property name Display name of the entry.
 * @property isDirectory Whether the entry is a directory.
 * @property size Size of the file in bytes (0 for directories).
 * @property lastModified Last modification timestamp in epoch millis.
 * @property childCount Number of children for directories, or null when unknown.
 */
data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int?
) {
    val path: String get() = file.absolutePath

    val extension: String get() = if (isDirectory) "" else file.extension.lowercase()

    companion object {
        /**
         * Builds a [FileItem] from a raw [File], reading lightweight metadata.
         *
         * @param file The file to wrap.
         * @return The populated [FileItem].
         */
        fun from(file: File): FileItem {
            val isDir = file.isDirectory
            return FileItem(
                file = file,
                name = file.name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = if (isDir) file.list()?.size else null
            )
        }
    }
}

/**
 * Categories used to pick an icon and group/filter files.
 */
enum class FileCategory {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, CODE, APK, OTHER
}

/**
 * Available sorting strategies for the file listing.
 */
enum class SortOrder {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC
}
