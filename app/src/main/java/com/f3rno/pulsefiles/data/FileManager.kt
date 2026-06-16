package com.f3rno.pulsefiles.data

import android.content.Context
import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.deleteStorageFile
import com.f3rno.pulsefiles.util.listChildren
import java.io.File

/**
 * Result of listing a directory.
 *
 * @property items Sorted entries when access succeeded.
 * @property accessDenied True when the directory exists but cannot be read.
 */
data class DirectoryListing(
    val items: List<FileItem> = emptyList(),
    val accessDenied: Boolean = false
)

/**
 * Performs file system operations on the local device storage.
 *
 * All operations run synchronously and should be invoked from a background
 * dispatcher by the caller.
 */
class FileManager(private val context: Context) {

    /**
     * Lists the children of a directory, applying sorting and an optional
     * hidden-files filter.
     *
     * @param dir The directory to list.
     * @param sortOrder The ordering to apply to results.
     * @param showHidden Whether dot-files should be included.
     * @return Listing result with items or an access-denied flag.
     */
    fun list(dir: File, sortOrder: SortOrder, showHidden: Boolean): DirectoryListing {
        val children = listChildren(dir) ?: return DirectoryListing(accessDenied = dir.exists())
        val items = children
            .filter { showHidden || !it.name.startsWith(".") }
            .map { FileItem.from(it) }
        return DirectoryListing(items = sort(items, sortOrder))
    }

    /**
     * Searches for entries under [root] whose names contain [query].
     *
     * When [query] is blank, only the immediate children of [root] are returned
     * (same as [list]). Otherwise every descendant of [root] is searched.
     *
     * @param root The directory to search from.
     * @param query Case-insensitive name filter.
     * @param sortOrder The ordering to apply to results.
     * @param showHidden Whether dot-files should be included.
     * @return Matching entries or an access-denied flag.
     */
    fun search(root: File, query: String, sortOrder: SortOrder, showHidden: Boolean): DirectoryListing {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return list(root, sortOrder, showHidden)

        if (listChildren(root) == null) return DirectoryListing(accessDenied = root.exists())
        val matches = mutableListOf<FileItem>()
        collectMatches(root, trimmed, showHidden, matches)
        return DirectoryListing(items = sort(matches, sortOrder))
    }

    private fun collectMatches(dir: File, query: String, showHidden: Boolean, out: MutableList<FileItem>) {
        val children = listChildren(dir) ?: return
        for (child in children) {
            if (!showHidden && child.name.startsWith(".")) continue
            if (child.name.contains(query, ignoreCase = true)) {
                out.add(FileItem.from(child))
            }
            if (child.isDirectory) {
                collectMatches(child, query, showHidden, out)
            }
        }
    }

    /**
     * Sorts file items keeping directories grouped before files.
     *
     * @param items The items to sort.
     * @param order The ordering strategy.
     * @return A new sorted list.
     */
    fun sort(items: List<FileItem>, order: SortOrder): List<FileItem> {
        val comparator: Comparator<FileItem> = when (order) {
            SortOrder.NAME_ASC -> compareBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> compareBy { it.size }
            SortOrder.SIZE_DESC -> compareByDescending { it.size }
            SortOrder.DATE_ASC -> compareBy { it.lastModified }
            SortOrder.DATE_DESC -> compareByDescending { it.lastModified }
        }
        return items.sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(comparator))
    }

    /**
     * Creates a new directory inside the given parent.
     *
     * @param parent The parent directory.
     * @param name The new folder name.
     * @return True when the folder was created.
     */
    fun createFolder(parent: File, name: String): Boolean {
        val target = File(parent, name)
        return !target.exists() && target.mkdirs()
    }

    /**
     * Renames a file or directory.
     *
     * @param item The entry to rename.
     * @param newName The new name.
     * @return True when the rename succeeded.
     */
    fun rename(item: FileItem, newName: String): Boolean {
        val target = File(item.file.parentFile, newName)
        return !target.exists() && item.file.renameTo(target)
    }

    /**
     * Deletes a list of files, recursing into directories.
     *
     * @param items The entries to delete.
     * @return True when every entry was deleted.
     */
    fun delete(items: List<FileItem>): Boolean {
        return items.all { deleteStorageFile(context, it.file) }
    }

    /**
     * Copies entries into a destination directory.
     *
     * @param items The entries to copy.
     * @param destination The target directory.
     * @return True when every entry was copied.
     */
    fun copy(items: List<FileItem>, destination: File): Boolean {
        return items.all { item ->
            val target = uniqueTarget(destination, item.name)
            runCatching { item.file.copyRecursively(target, overwrite = false) }.getOrDefault(false)
        }
    }

    /**
     * Moves entries into a destination directory, falling back to copy+delete
     * when a simple rename is not possible (e.g. across mount points).
     *
     * @param items The entries to move.
     * @param destination The target directory.
     * @return True when every entry was moved.
     */
    fun move(items: List<FileItem>, destination: File): Boolean {
        return items.all { item ->
            val target = uniqueTarget(destination, item.name)
            if (item.file.renameTo(target)) return@all true
            val copied = runCatching { item.file.copyRecursively(target, overwrite = false) }.getOrDefault(false)
            copied && item.file.deleteRecursively()
        }
    }

    /**
     * Builds a non-colliding target file inside a directory by appending a
     * numeric suffix when a name already exists.
     *
     * @param destination The destination directory.
     * @param name The desired entry name.
     * @return A [File] guaranteed not to currently exist.
     */
    private fun uniqueTarget(destination: File, name: String): File {
        var candidate = File(destination, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast(".")
        val ext = name.substringAfterLast(".", "")
        var index = 1
        while (candidate.exists()) {
            val newName = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(destination, newName)
            index++
        }
        return candidate
    }
}
