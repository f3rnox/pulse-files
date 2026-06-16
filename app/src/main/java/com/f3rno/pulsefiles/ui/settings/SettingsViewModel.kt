package com.f3rno.pulsefiles.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.f3rno.pulsefiles.data.AppSettings
import com.f3rno.pulsefiles.data.IgnoredLargeFilesStore
import com.f3rno.pulsefiles.data.SettingsStore
import com.f3rno.pulsefiles.data.StartupTab
import com.f3rno.pulsefiles.data.ThemeMode
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.normalizeStoragePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore.get(application)
    private val ignoredLargeFilesStore = IgnoredLargeFilesStore(application)

    private val _ignoredPaths = MutableStateFlow(ignoredLargeFilesStore.getIgnoredPaths().sorted())
    val ignoredPaths: StateFlow<List<String>> = _ignoredPaths.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings

    fun refreshIgnoredPaths() {
        _ignoredPaths.value = ignoredLargeFilesStore.getIgnoredPaths().sorted()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    fun ignoredLargeFilePaths(): List<String> = _ignoredPaths.value

    fun unignoreLargeFiles(paths: List<String>) {
        ignoredLargeFilesStore.remove(paths.map { normalizeStoragePath(it) }.toSet())
        refreshIgnoredPaths()
    }

    fun clearIgnoredLargeFiles() {
        ignoredLargeFilesStore.clear()
        refreshIgnoredPaths()
    }

    fun setDefaultSort(order: SortOrder) {
        updateSettings { it.copy(defaultSortOrder = order) }
    }

    fun setShowHiddenByDefault(enabled: Boolean) {
        updateSettings { it.copy(showHiddenByDefault = enabled) }
    }

    fun setRememberLastFolder(enabled: Boolean) {
        updateSettings { it.copy(rememberLastFolder = enabled) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        updateSettings { it.copy(confirmBeforeDelete = enabled) }
    }

    fun setRecursiveSearch(enabled: Boolean) {
        updateSettings { it.copy(recursiveSearch = enabled) }
    }

    fun setLargeFileThresholdMb(mb: Int) {
        updateSettings { it.copy(largeFileThresholdMb = mb.coerceIn(1, 500)) }
    }

    fun setScanDepth(depth: Int) {
        updateSettings { it.copy(scanDepth = depth.coerceIn(1, 20)) }
    }

    fun setScanJunk(enabled: Boolean) {
        updateSettings { it.copy(scanJunk = enabled) }
    }

    fun setScanDuplicates(enabled: Boolean) {
        updateSettings { it.copy(scanDuplicates = enabled) }
    }

    fun setScanLargeFiles(enabled: Boolean) {
        updateSettings { it.copy(scanLargeFiles = enabled) }
    }

    fun setScanDownloads(enabled: Boolean) {
        updateSettings { it.copy(scanDownloads = enabled) }
    }

    fun setScanApks(enabled: Boolean) {
        updateSettings { it.copy(scanApks = enabled) }
    }

    fun setAutoScanOnLaunch(enabled: Boolean) {
        updateSettings { it.copy(autoScanOnLaunch = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        updateSettings { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        updateSettings { it.copy(dynamicColor = enabled) }
    }

    fun setStartFolderPath(path: String) {
        updateSettings { it.copy(startFolderPath = normalizeStoragePath(path)) }
    }

    fun clearStartFolder() {
        updateSettings { it.copy(startFolderPath = "") }
    }

    fun setDefaultTab(tab: StartupTab) {
        updateSettings { it.copy(defaultTab = tab) }
    }
}
