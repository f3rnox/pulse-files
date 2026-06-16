package com.f3rno.pulsefiles.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files

/**
 * Normalizes a file path for consistent comparisons and deletion.
 *
 * @param path Absolute or canonical path string.
 * @return Canonical path when possible.
 */
fun normalizeStoragePath(path: String): String {
    return runCatching { File(path).canonicalFile.absolutePath }.getOrDefault(path)
}

/**
 * Deletes a file from shared storage, using MediaStore when direct file deletion fails.
 *
 * @param context Application context for MediaStore access.
 * @param file The file to delete.
 * @return True when the file no longer exists.
 */
fun deleteStorageFile(context: Context, file: File): Boolean {
    if (!hasWritableStorageAccess(context)) return false

    val target = runCatching { file.canonicalFile }.getOrDefault(file)
    if (!target.exists()) return true
    if (deleteDirectly(target)) return true

    val storageRoot = runCatching {
        Environment.getExternalStorageDirectory().canonicalFile.absolutePath
    }.getOrDefault(target.absolutePath.substringBeforeLast('/'))

    val uris = findMediaStoreUris(context, target, storageRoot)
    for (uri in uris) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    if (!target.exists()) return true
    return deleteDirectly(target)
}

private fun deleteDirectly(file: File): Boolean {
    if (file.delete()) return true
    if (!file.canWrite()) {
        file.setWritable(true, false)
    }
    if (file.delete()) return true
    if (file.deleteRecursively()) return true

    return runCatching {
        Files.delete(file.toPath())
        true
    }.getOrDefault(false)
}

private fun findMediaStoreUris(
    context: Context,
    file: File,
    storageRoot: String
): List<Uri> {
    val resolver = context.contentResolver
    val targetPath = normalizeStoragePath(file.absolutePath)
    val displayName = file.name
    val targetSize = file.length()
    val uris = mutableListOf<Uri>()

    val collections = buildList {
        add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        add(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
        }
    }

    for (collection in collections) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()

        resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(displayName),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val resolvedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = cursor.getString(pathColumn)
                    buildMediaStorePath(storageRoot, relativePath, name)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getString(pathColumn)
                }

                val pathMatches = resolvedPath == targetPath || resolvedPath == file.absolutePath
                val sizeMatches = size == targetSize && name == displayName
                if (pathMatches || sizeMatches) {
                    uris.add(ContentUris.withAppendedId(collection, id))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = mediaStoreRelativePath(file, storageRoot)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            resolver.query(collection, projection, selection, arrayOf(displayName, relativePath), null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    uris.add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                }
            }
        }

        for (path in listOf(targetPath, file.absolutePath)) {
            @Suppress("DEPRECATION")
            val legacySelection = "${MediaStore.MediaColumns.DATA}=?"
            resolver.query(collection, projection, legacySelection, arrayOf(path), null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    uris.add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                }
            }
        }
    }

    return uris.distinctBy { it.toString() }
}

private fun buildMediaStorePath(storageRoot: String, relativePath: String?, displayName: String): String {
    val root = storageRoot.trimEnd('/')
    val rel = relativePath.orEmpty().trimStart('/')
    val combined = if (rel.isEmpty()) {
        "$root/$displayName"
    } else {
        "$root/$rel$displayName"
    }
    return normalizeStoragePath(combined)
}

private fun mediaStoreRelativePath(file: File, storageRoot: String): String {
    val root = storageRoot.trimEnd('/') + '/'
    val path = normalizeStoragePath(file.absolutePath)
    if (!path.startsWith(root)) return ""
    val relative = path.removePrefix(root)
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return ""
    return relative.substring(0, slash + 1)
}
