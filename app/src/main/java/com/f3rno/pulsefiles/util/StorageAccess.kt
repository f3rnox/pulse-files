package com.f3rno.pulsefiles.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Resolves the primary shared storage path for browsing.
 *
 * @param context Context reference.
 * @return Absolute path to internal shared storage.
 */
fun primaryStoragePath(context: Context): String {
    return Environment.getExternalStorageDirectory().absolutePath
}

/**
 * Launches the system settings screen for full files access permission,
 * falling back gracefully if needed.
 *
 * @param context The context used to start the settings activity.
 */
fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val packageIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(packageIntent)
        } catch (_: ActivityNotFoundException) {
            val genericIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Returns whether the app holds the storage permission needed to browse files.
 *
 * On Android 11+ full browsing requires "All files access"
 * ([Environment.isExternalStorageManager]).
 *
 * @param context Used for permission checks.
 * @return True when the permission gate should allow browsing.
 */
fun hasBrowsableStorageAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return hasReadExternalStorage(context)
}

private fun hasReadExternalStorage(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Returns whether a directory can be enumerated with the current permissions.
 *
 * @param dir The directory to probe.
 * @return True when [listChildren] would not return null due to access denial.
 */
fun canListDirectory(dir: File): Boolean {
    if (!dir.exists() || !dir.isDirectory) return false
    return dir.listFiles() != null || dir.list() != null
}

/**
 * Lists the children of a directory, falling back when [File.listFiles] returns
 * null but [File.list] still works.
 *
 * @param dir The directory to enumerate.
 * @return Child files, or null when the directory cannot be read.
 */
fun listChildren(dir: File): List<File>? {
    if (!dir.exists() || !dir.isDirectory) return null

    val listed = dir.listFiles()
    if (listed != null) return listed.toList()

    val names = dir.list() ?: return null
    return names.map { File(dir, it) }.filter { it.exists() }
}
