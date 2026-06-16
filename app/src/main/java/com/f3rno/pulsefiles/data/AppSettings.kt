package com.f3rno.pulsefiles.data

import com.f3rno.pulsefiles.model.SortOrder

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class StartupTab {
    CLEAN, BROWSE, SETTINGS
}

/**
 * User-configurable app preferences with defaults matching current hardcoded behavior.
 */
data class AppSettings(
    val defaultSortOrder: SortOrder = SortOrder.NAME_ASC,
    val showHiddenByDefault: Boolean = false,
    val rememberLastFolder: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val recursiveSearch: Boolean = true,
    val largeFileThresholdMb: Int = 10,
    val scanDepth: Int = 5,
    val scanJunk: Boolean = true,
    val scanDuplicates: Boolean = true,
    val scanLargeFiles: Boolean = true,
    val scanDownloads: Boolean = true,
    val scanApks: Boolean = true,
    val autoScanOnLaunch: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val startFolderPath: String = "",
    val lastBrowsedPath: String = "",
    val defaultTab: StartupTab = StartupTab.BROWSE
) {
    val largeFileThresholdBytes: Long get() = largeFileThresholdMb.toLong() * 1024L * 1024L
}
