package com.f3rno.pulsefiles.data

import android.content.Context
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.normalizeStoragePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Persists and exposes [AppSettings] via SharedPreferences.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun snapshot(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        persist(updated)
        _settings.value = updated
    }

    fun setLastBrowsedPath(path: String) {
        update { it.copy(lastBrowsedPath = normalizeStoragePath(path)) }
    }

    private fun readSettings(): AppSettings {
        return AppSettings(
            defaultSortOrder = SortOrder.entries.getOrElse(prefs.getInt(KEY_DEFAULT_SORT, 0)) { SortOrder.NAME_ASC },
            showHiddenByDefault = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
            rememberLastFolder = prefs.getBoolean(KEY_REMEMBER_LAST_FOLDER, true),
            confirmBeforeDelete = prefs.getBoolean(KEY_CONFIRM_DELETE, true),
            recursiveSearch = prefs.getBoolean(KEY_RECURSIVE_SEARCH, true),
            largeFileThresholdMb = prefs.getInt(KEY_LARGE_FILE_MB, 10).coerceIn(1, 500),
            scanDepth = prefs.getInt(KEY_SCAN_DEPTH, 5).coerceIn(1, 20),
            scanJunk = prefs.getBoolean(KEY_SCAN_JUNK, true),
            scanDuplicates = prefs.getBoolean(KEY_SCAN_DUPLICATES, true),
            scanLargeFiles = prefs.getBoolean(KEY_SCAN_LARGE, true),
            scanDownloads = prefs.getBoolean(KEY_SCAN_DOWNLOADS, true),
            scanApks = prefs.getBoolean(KEY_SCAN_APKS, true),
            autoScanOnLaunch = prefs.getBoolean(KEY_AUTO_SCAN, true),
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME_MODE, 0)) { ThemeMode.SYSTEM },
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            startFolderPath = prefs.getString(KEY_START_FOLDER, "").orEmpty(),
            lastBrowsedPath = prefs.getString(KEY_LAST_BROWSED, "").orEmpty(),
            defaultTab = StartupTab.entries.getOrElse(prefs.getInt(KEY_DEFAULT_TAB, 1)) { StartupTab.BROWSE }
        )
    }

    private fun persist(settings: AppSettings) {
        prefs.edit()
            .putInt(KEY_DEFAULT_SORT, settings.defaultSortOrder.ordinal)
            .putBoolean(KEY_SHOW_HIDDEN, settings.showHiddenByDefault)
            .putBoolean(KEY_REMEMBER_LAST_FOLDER, settings.rememberLastFolder)
            .putBoolean(KEY_CONFIRM_DELETE, settings.confirmBeforeDelete)
            .putBoolean(KEY_RECURSIVE_SEARCH, settings.recursiveSearch)
            .putInt(KEY_LARGE_FILE_MB, settings.largeFileThresholdMb)
            .putInt(KEY_SCAN_DEPTH, settings.scanDepth)
            .putBoolean(KEY_SCAN_JUNK, settings.scanJunk)
            .putBoolean(KEY_SCAN_DUPLICATES, settings.scanDuplicates)
            .putBoolean(KEY_SCAN_LARGE, settings.scanLargeFiles)
            .putBoolean(KEY_SCAN_DOWNLOADS, settings.scanDownloads)
            .putBoolean(KEY_SCAN_APKS, settings.scanApks)
            .putBoolean(KEY_AUTO_SCAN, settings.autoScanOnLaunch)
            .putInt(KEY_THEME_MODE, settings.themeMode.ordinal)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
            .putString(KEY_START_FOLDER, settings.startFolderPath)
            .putString(KEY_LAST_BROWSED, settings.lastBrowsedPath)
            .putInt(KEY_DEFAULT_TAB, settings.defaultTab.ordinal)
            .apply()
    }

    fun resolveBrowserStartPath(storageRoot: String): String {
        val settings = snapshot()
        if (settings.rememberLastFolder) {
            val last = settings.lastBrowsedPath
            if (last.isNotEmpty()) {
                val lastDir = File(last)
                if (lastDir.exists() && lastDir.isDirectory) return last
            }
        }
        val start = settings.startFolderPath
        if (start.isNotEmpty()) {
            val startDir = File(start)
            if (startDir.exists() && startDir.isDirectory) return start
        }
        return storageRoot
    }

    companion object {
        private const val PREFS_NAME = "pulse_files_settings"

        private const val KEY_DEFAULT_SORT = "default_sort"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_REMEMBER_LAST_FOLDER = "remember_last_folder"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"
        private const val KEY_RECURSIVE_SEARCH = "recursive_search"
        private const val KEY_LARGE_FILE_MB = "large_file_mb"
        private const val KEY_SCAN_DEPTH = "scan_depth"
        private const val KEY_SCAN_JUNK = "scan_junk"
        private const val KEY_SCAN_DUPLICATES = "scan_duplicates"
        private const val KEY_SCAN_LARGE = "scan_large"
        private const val KEY_SCAN_DOWNLOADS = "scan_downloads"
        private const val KEY_SCAN_APKS = "scan_apks"
        private const val KEY_AUTO_SCAN = "auto_scan"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_START_FOLDER = "start_folder"
        private const val KEY_LAST_BROWSED = "last_browsed"
        private const val KEY_DEFAULT_TAB = "default_tab"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
