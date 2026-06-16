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

    fun isZipPath(path: String): Boolean {
        return path.contains(".zip::", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)
    }

    fun splitZipPath(path: String): Pair<String, String> {
        val index = path.indexOf(".zip::", ignoreCase = true)
        return if (index != -1) {
            val zipPath = path.substring(0, index + 4)
            val relativePath = path.substring(index + 6)
            zipPath to relativePath
        } else if (path.endsWith(".zip", ignoreCase = true)) {
            path to ""
        } else {
            path to ""
        }
    }

    fun getParentPath(path: String, rootPath: String): String? {
        if (path == rootPath) return null
        if (path.contains(".zip::", ignoreCase = true)) {
            val index = path.indexOf(".zip::", ignoreCase = true)
            val zipPath = path.substring(0, index + 4)
            val relativePath = path.substring(index + 6)
            if (relativePath.isEmpty()) {
                return File(zipPath).parent
            }
            val lastSlash = relativePath.lastIndexOf('/')
            return if (lastSlash == -1) {
                zipPath
            } else {
                zipPath + "::" + relativePath.substring(0, lastSlash)
            }
        }
        return File(path).parent
    }

    fun listZip(virtualPath: String, sortOrder: SortOrder): DirectoryListing {
        val (zipPath, relativeDir) = splitZipPath(virtualPath)
        val zipFile = File(zipPath)
        if (!zipFile.exists() || !zipFile.isFile) return DirectoryListing(accessDenied = true)

        val items = mutableListOf<FileItem>()
        return try {
            java.util.zip.ZipFile(zipFile).use { jZip ->
                val prefix = if (relativeDir.isEmpty()) "" else if (relativeDir.endsWith("/")) relativeDir else "$relativeDir/"
                val seenDirectories = mutableSetOf<String>()
                val entries = jZip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith(prefix) && name != prefix) {
                        val relativePart = name.substring(prefix.length)
                        if (relativePart.isEmpty()) continue
                        val slashIndex = relativePart.indexOf('/')
                        if (slashIndex != -1) {
                            val dirName = relativePart.substring(0, slashIndex)
                            val fullVirtualDirPath = if (prefix.isEmpty()) dirName else "$prefix$dirName"
                            if (seenDirectories.add(dirName)) {
                                items.add(
                                    FileItem(
                                        file = File("$zipPath::$fullVirtualDirPath"),
                                        name = dirName,
                                        isDirectory = true,
                                        size = 0L,
                                        lastModified = entry.time,
                                        childCount = null
                                    )
                                )
                            }
                        } else {
                            val fileName = relativePart
                            val fullVirtualFilePath = if (prefix.isEmpty()) fileName else "$prefix$fileName"
                            items.add(
                                FileItem(
                                    file = File("$zipPath::$fullVirtualFilePath"),
                                    name = fileName,
                                    isDirectory = false,
                                    size = entry.size,
                                    lastModified = entry.time,
                                    childCount = null
                                )
                            )
                        }
                    }
                }
            }
            DirectoryListing(items = sort(items, sortOrder))
        } catch (e: Exception) {
            e.printStackTrace()
            DirectoryListing(accessDenied = true)
        }
    }

    fun extractZipTo(zipFile: File, destinationDir: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) return false
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        return try {
            java.util.zip.ZipFile(zipFile).use { jZip ->
                val entries = jZip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val targetFile = File(destinationDir, entry.name)
                    val canonicalDest = destinationDir.canonicalPath
                    val canonicalTarget = targetFile.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDest)) {
                        continue
                    }
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        jZip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun compressToZip(items: List<File>, zipFile: File): Boolean {
        return try {
            java.io.FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    for (file in items) {
                        compressFileOrFolder(file, file.name, zos)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun compressFileOrFolder(file: File, pathInZip: String, zos: java.util.zip.ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children.isNullOrEmpty()) {
                val entry = java.util.zip.ZipEntry(if (pathInZip.endsWith("/")) pathInZip else "$pathInZip/")
                zos.putNextEntry(entry)
                zos.closeEntry()
            } else {
                for (child in children) {
                    compressFileOrFolder(child, "$pathInZip/${child.name}", zos)
                }
            }
        } else {
            val entry = java.util.zip.ZipEntry(pathInZip)
            zos.putNextEntry(entry)
            java.io.FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }
}
