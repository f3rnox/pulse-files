package com.f3rno.pulsefiles.data

import android.content.Context
import com.f3rno.pulsefiles.util.normalizeStoragePath
import java.io.File

/**
 * Persists paths of large files the user chose to exclude from clean suggestions.
 */
class IgnoredLargeFilesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns normalized paths currently marked as ignored.
     */
    fun getIgnoredPaths(): Set<String> {
        val stored = prefs.getStringSet(KEY_IGNORED_PATHS, emptySet()) ?: emptySet()
        return stored.map { normalizeStoragePath(it) }.toSet()
    }

    /**
     * Marks the given paths as ignored.
     *
     * @param paths Absolute file paths to ignore.
     */
    fun add(paths: Set<String>) {
        if (paths.isEmpty()) return
        val updated = getIgnoredPaths().toMutableSet()
        updated.addAll(paths.map { normalizeStoragePath(it) })
        prefs.edit().putStringSet(KEY_IGNORED_PATHS, HashSet(updated)).apply()
    }

    /**
     * Drops ignored paths that no longer exist on disk.
     */
    fun pruneMissing() {
        val existing = getIgnoredPaths().filter { path -> File(path).exists() }.toSet()
        prefs.edit().putStringSet(KEY_IGNORED_PATHS, HashSet(existing)).apply()
    }

    companion object {
        private const val PREFS_NAME = "pulse_files_clean"
        private const val KEY_IGNORED_PATHS = "ignored_large_file_paths"
    }
}
