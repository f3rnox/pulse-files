package com.f3rno.pulsefiles.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents an installed application with its metadata and launcher icon.
 */
data class AppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val installTime: Long,
    val apkSize: Long,
    val isSystemApp: Boolean,
    val canUninstall: Boolean,
    val isLaunchable: Boolean,
    val sourceDir: String,
    val icon: ImageBitmap? = null
)
