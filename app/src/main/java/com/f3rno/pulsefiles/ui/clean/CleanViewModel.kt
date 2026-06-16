package com.f3rno.pulsefiles.ui.clean

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f3rno.pulsefiles.data.IgnoredLargeFilesStore
import com.f3rno.pulsefiles.data.SettingsStore
import com.f3rno.pulsefiles.util.deleteStorageFile
import com.f3rno.pulsefiles.util.hasWritableStorageAccess
import com.f3rno.pulsefiles.util.normalizeStoragePath
import com.f3rno.pulsefiles.util.primaryStoragePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Group of duplicate files.
 * [original] is the file we keep, [duplicates] are the matching copies that can be cleaned.
 */
data class DuplicateGroup(
    val size: Long,
    val original: File,
    val duplicates: List<File>
)

data class CleanUiState(
    val isScanning: Boolean = false,
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L,
    val junkFiles: List<File> = emptyList(),
    val duplicateFiles: List<DuplicateGroup> = emptyList(),
    val largeFiles: List<File> = emptyList(),
    val downloadFiles: List<File> = emptyList(),
    val apkFiles: List<File> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val usedSpace: Long get() = (totalSpace - freeSpace).coerceAtLeast(0L)
    val junkSize: Long get() = junkFiles.sumOf { it.length() }
    val duplicatesSize: Long get() = duplicateFiles.sumOf { group -> group.size * group.duplicates.size }
    val largeSize: Long get() = largeFiles.sumOf { it.length() }
    val downloadSize: Long get() = downloadFiles.sumOf { it.length() }
    val apkSize: Long get() = apkFiles.sumOf { it.length() }
}

class CleanViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CleanUiState())
    val uiState: StateFlow<CleanUiState> = _uiState.asStateFlow()

    private val rootPath: String = primaryStoragePath(application)
    private val ignoredLargeFilesStore = IgnoredLargeFilesStore(application)
    private val settingsStore = SettingsStore.get(application)
    private var scanJob: Job? = null

    init {
        refreshStorageInfo()
        if (settingsStore.snapshot().autoScanOnLaunch) {
            startScan()
        }
    }

    /**
     * Reads storage space info.
     */
    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = File(rootPath)
            if (root.exists()) {
                val total = root.totalSpace
                val free = root.freeSpace
                _uiState.update {
                    it.copy(totalSpace = total, freeSpace = free)
                }
            }
        }
    }

    /**
     * Starts scanning the file system.
     */
    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            rescan()
        }
    }

    private suspend fun rescan() {
        _uiState.update { it.copy(isScanning = true, errorMessage = null) }

        try {
            val scanResult = withContext(Dispatchers.IO) {
                ignoredLargeFilesStore.pruneMissing()
                performScan()
            }
            _uiState.update {
                it.copy(
                    isScanning = false,
                    junkFiles = scanResult.junk,
                    duplicateFiles = scanResult.duplicates,
                    largeFiles = withoutIgnoredLargeFiles(scanResult.large),
                    downloadFiles = scanResult.downloads,
                    apkFiles = scanResult.apks
                )
            }
            refreshStorageInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isScanning = false, errorMessage = "Scan failed: ${e.localizedMessage}")
            }
        }
    }

    private fun withoutIgnoredLargeFiles(files: List<File>): List<File> {
        val ignored = ignoredLargeFilesStore.getIgnoredPaths()
        if (ignored.isEmpty()) return files
        return files.filter { normalizeStoragePath(it.absolutePath) !in ignored }
    }

    /**
     * Marks large files as ignored so they are hidden from future large-file suggestions.
     *
     * @param paths Absolute paths of files to ignore.
     */
    fun ignoreLargeFiles(paths: List<String>) {
        val normalized = paths.map { normalizeStoragePath(it) }.toSet()
        if (normalized.isEmpty()) return

        ignoredLargeFilesStore.add(normalized)
        _uiState.update { state ->
            state.copy(
                largeFiles = state.largeFiles.filter { normalizeStoragePath(it.absolutePath) !in normalized },
                successMessage = if (normalized.size == 1) {
                    "Ignored 1 file"
                } else {
                    "Ignored ${normalized.size} files"
                }
            )
        }
    }

    private fun removePathsFromState(paths: Set<String>) {
        if (paths.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                junkFiles = state.junkFiles.filter { it.absolutePath !in paths },
                largeFiles = state.largeFiles.filter { it.absolutePath !in paths },
                downloadFiles = state.downloadFiles.filter { it.absolutePath !in paths },
                apkFiles = state.apkFiles.filter { it.absolutePath !in paths },
                duplicateFiles = state.duplicateFiles.mapNotNull { group ->
                    val remainingDuplicates = group.duplicates.filter { it.absolutePath !in paths }
                    when {
                        group.original.absolutePath in paths -> null
                        remainingDuplicates.isEmpty() -> null
                        else -> group.copy(duplicates = remainingDuplicates)
                    }
                }
            )
        }
    }

    private suspend fun deletePaths(paths: Set<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        if (!hasWritableStorageAccess(context)) {
            return@withContext 0 to paths.size
        }

        var deletedCount = 0
        var failedCount = 0
        paths.forEach { path ->
            val file = File(normalizeStoragePath(path))
            if (deleteStorageFile(context, file)) {
                deletedCount++
            } else {
                failedCount++
            }
        }
        deletedCount to failedCount
    }

    private class ScanResult(
        val junk: List<File>,
        val duplicates: List<DuplicateGroup>,
        val large: List<File>,
        val downloads: List<File>,
        val apks: List<File>
    )

    private fun performScan(): ScanResult {
        val settings = settingsStore.snapshot()
        val root = File(rootPath)
        val junk = mutableListOf<File>()
        val large = mutableListOf<File>()
        val downloads = mutableListOf<File>()
        val apks = mutableListOf<File>()

        // For duplicate detection: map size to files of that size
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        val downloadDir = File(root, "Download")

        root.walkTopDown()
            .maxDepth(settings.scanDepth)
            .onFail { _, _ -> /* ignore inaccessible folders */ }
            .forEach { file ->
                if (!file.isFile) return@forEach
                val resolved = runCatching { file.canonicalFile }.getOrDefault(file)
                val length = resolved.length()
                val name = resolved.name.lowercase()
                val path = resolved.absolutePath

                val isJunk = name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                    path.contains("/cache/", ignoreCase = true) || path.contains("/.cache/", ignoreCase = true)
                if (settings.scanJunk && isJunk) {
                    junk.add(resolved)
                }

                if (settings.scanLargeFiles && length > settings.largeFileThresholdBytes) {
                    large.add(resolved)
                }

                if (settings.scanDownloads && downloadDir.exists() && path.startsWith(downloadDir.absolutePath)) {
                    downloads.add(resolved)
                }

                if (settings.scanApks && name.endsWith(".apk")) {
                    apks.add(resolved)
                }

                if (settings.scanDuplicates && length > 100L && !isJunk) {
                    sizeMap.getOrPut(length) { mutableListOf() }.add(resolved)
                }
            }

        val duplicates = mutableListOf<DuplicateGroup>()
        if (settings.scanDuplicates) {
            for ((size, files) in sizeMap) {
                if (files.size > 1) {
                    val hashGroups = files.groupBy { file ->
                        val hash = quickHash(file)
                        if (hash.isEmpty()) file.name else hash
                    }
                    for (group in hashGroups.values) {
                        if (group.size > 1) {
                            duplicates.add(DuplicateGroup(size = size, original = group[0], duplicates = group.drop(1)))
                        }
                    }
                }
            }
        }

        // Sort descending by size
        large.sortByDescending { it.length() }
        downloads.sortByDescending { it.length() }
        apks.sortByDescending { it.length() }
        duplicates.sortByDescending { it.size * it.duplicates.size }

        return ScanResult(
            junk = junk,
            duplicates = duplicates,
            large = large,
            downloads = downloads,
            apks = apks
        )
    }

    private fun quickHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Cleans all junk files at once.
     */
    fun cleanJunk() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            if (!hasWritableStorageAccess(context)) {
                _uiState.update {
                    it.copy(errorMessage = "Grant \"All files access\" in settings to delete files")
                }
                return@launch
            }

            val paths = _uiState.value.junkFiles.map { normalizeStoragePath(it.absolutePath) }.toSet()
            val (deletedCount, failedCount) = deletePaths(paths)
            removePathsFromState(paths.filter { path -> !File(path).exists() }.toSet())
            _uiState.update {
                it.copy(
                    successMessage = deletionMessage(deletedCount, failedCount, "junk"),
                    errorMessage = if (failedCount > 0 && deletedCount == 0) {
                        "Could not delete junk files"
                    } else {
                        null
                    }
                )
            }
            rescan()
        }
    }

    /**
     * Deletes a specific list of files by path.
     */
    fun deleteFiles(paths: List<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            if (!hasWritableStorageAccess(context)) {
                _uiState.update {
                    it.copy(errorMessage = "Grant \"All files access\" in settings to delete files")
                }
                return@launch
            }

            val normalizedPaths = paths.map { normalizeStoragePath(it) }.toSet()
            val (deletedCount, failedCount) = deletePaths(normalizedPaths)
            removePathsFromState(
                normalizedPaths.filter { path -> !File(path).exists() }.toSet()
            )
            _uiState.update {
                it.copy(
                    successMessage = deletionMessage(deletedCount, failedCount, "file"),
                    errorMessage = when {
                        failedCount > 0 && deletedCount == 0 ->
                            "Could not delete selected files. Check that Pulse Files has all files access."
                        else -> null
                    }
                )
            }
            rescan()
            onComplete()
        }
    }

    private fun deletionMessage(deletedCount: Int, failedCount: Int, noun: String): String {
        val label = if (deletedCount == 1) noun else "${noun}s"
        return when {
            failedCount > 0 && deletedCount > 0 ->
                "Deleted $deletedCount $label, $failedCount could not be deleted"
            failedCount > 0 -> "Could not delete selected $label"
            else -> "Successfully deleted $deletedCount $label"
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
