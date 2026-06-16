package com.f3rno.pulsefiles.ui.apps

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f3rno.pulsefiles.model.AppItem
import com.f3rno.pulsefiles.util.primaryStoragePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppSortOrder {
    NAME_ASC, NAME_DESC, SIZE_DESC, INSTALL_DATE_DESC
}

data class AppManagerUiState(
    val isLoading: Boolean = false,
    val apps: List<AppItem> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val searchQuery: String? = null,
    val showSystemApps: Boolean = false,
    val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    val uninstallQueue: List<String> = emptyList(),
    val currentlyUninstalling: String? = null,
    val isUninstalling: Boolean = false,
    val batchUninstallTotal: Int = 0,
    val batchUninstallProgress: Int = 0,
    val isExtracting: Boolean = false,
    val extractionProgress: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    private val pm: PackageManager = application.packageManager

    init {
        loadApps()
    }

    fun loadApps(silent: Boolean = false) {
        if (!silent) {
            _uiState.update { it.copy(isLoading = true) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installedPackages = pm.getInstalledPackages(0)
                val appItems = installedPackages.mapNotNull { packageInfo ->
                    val packageName = packageInfo.packageName
                    val appInfo = try {
                        pm.getApplicationInfo(packageName, 0)
                    } catch (e: Exception) {
                        packageInfo.applicationInfo ?: return@mapNotNull null
                    }
                    
                    val isLaunchable = pm.getLaunchIntentForPackage(packageName) != null
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val canUninstall = !isSystem ||
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    
                    val apkFile = File(appInfo.sourceDir)
                    val apkSize = if (apkFile.exists()) apkFile.length() else 0L
                    
                    val label = appInfo.loadLabel(pm).toString()
                    val versionName = packageInfo.versionName ?: "1.0"
                    val installTime = packageInfo.firstInstallTime

                    // Downscale icon to keep memory footprints exceptionally low in Compose
                    val iconBitmap: ImageBitmap? = try {
                        val drawable = appInfo.loadIcon(pm)
                        drawable.toBitmap(width = 120, height = 120, config = android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }

                    AppItem(
                        label = label,
                        packageName = packageName,
                        versionName = versionName,
                        installTime = installTime,
                        apkSize = apkSize,
                        isSystemApp = isSystem,
                        canUninstall = canUninstall,
                        isLaunchable = isLaunchable,
                        sourceDir = appInfo.sourceDir,
                        icon = iconBitmap
                    )
                }

                _uiState.update {
                    it.copy(
                        apps = appItems,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load apps: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun toggleSelectApp(packageName: String) {
        _uiState.update { state ->
            val selection = state.selectedPackages.toMutableSet()
            if (selection.contains(packageName)) {
                selection.remove(packageName)
            } else {
                selection.add(packageName)
            }
            state.copy(selectedPackages = selection)
        }
    }

    fun selectAll(filteredApps: List<AppItem>) {
        _uiState.update { state ->
            val selection = filteredApps.map { it.packageName }.toSet()
            state.copy(selectedPackages = selection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun setSortOrder(order: AppSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun setSearchQuery(query: String?) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleShowSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * Extracts selected applications' APKs to internal storage.
     */
    fun extractSelectedApps() {
        val selected = _uiState.value.selectedPackages
        if (selected.isEmpty()) return

        val appsToExtract = _uiState.value.apps.filter { it.packageName in selected }
        _uiState.update { it.copy(isExtracting = true, extractionProgress = "Starting extraction...") }

        viewModelScope.launch(Dispatchers.IO) {
            val primaryDir = primaryStoragePath(getApplication())
            val backupDir = File(primaryDir, "ExtractedAPKs")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            var successCount = 0
            var failCount = 0

            appsToExtract.forEachIndexed { index, app ->
                _uiState.update {
                    it.copy(extractionProgress = "Extracting ${app.label} (${index + 1}/${appsToExtract.size})...")
                }

                try {
                    val srcFile = File(app.sourceDir)
                    if (srcFile.exists()) {
                        val sanitizedLabel = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val destFile = File(backupDir, "${sanitizedLabel}_${app.versionName}.apk")
                        srcFile.copyTo(destFile, overwrite = true)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            _uiState.update {
                it.copy(
                    isExtracting = false,
                    selectedPackages = emptySet(),
                    successMessage = "Extracted $successCount APK(s) to ExtractedAPKs folder. " +
                            if (failCount > 0) "Failed to extract $failCount." else ""
                )
            }
        }
    }

    /**
     * Starts the batch uninstallation flow.
     */
    fun startBatchUninstall(packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        _uiState.update {
            it.copy(
                uninstallQueue = packageNames,
                currentlyUninstalling = null,
                isUninstalling = true,
                batchUninstallTotal = packageNames.size,
                batchUninstallProgress = 0
            )
        }
        processNextUninstall()
    }

    /**
     * Gets the next intent to perform uninstallation or disabling.
     */
    fun getNextUninstallIntent(): Intent? {
        val nextPkg = _uiState.value.currentlyUninstalling ?: return null
        val app = _uiState.value.apps.find { it.packageName == nextPkg }
        val canUninstall = app?.canUninstall ?: true

        return if (!canUninstall) {
            // Non-updated system apps can only be disabled via settings.
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$nextPkg")
            }
        } else {
            createUninstallIntent(nextPkg)
        }
    }

    /**
     * Advances to the next item or completes.
     */
    fun onUninstallIntentFinished(resultCode: Int) {
        val current = _uiState.value.currentlyUninstalling ?: return
        val app = _uiState.value.apps.find { it.packageName == current }
        val canUninstall = app?.canUninstall ?: true

        if (canUninstall) {
            val wasRemoved = !isPackageInstalled(current)
            if (!wasRemoved && resultCode != Activity.RESULT_OK) {
                abortUninstallBatch(
                    if (resultCode == Activity.RESULT_CANCELED) {
                        "Uninstall cancelled"
                    } else {
                        "Could not uninstall ${app?.label ?: current}"
                    }
                )
                return
            }
        }

        advanceUninstallQueue(current)
    }

    fun onUninstallIntentFailed(message: String) {
        abortUninstallBatch(message)
    }

    private fun createUninstallIntent(packageName: String): Intent? {
        val uri = Uri.parse("package:$packageName")
        val candidates = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri),
            Intent(Intent.ACTION_DELETE, uri)
        )
        return candidates.firstOrNull { intent ->
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.resolveActivity(pm) != null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun advanceUninstallQueue(current: String) {
        val queue = _uiState.value.uninstallQueue.toMutableList()
        queue.remove(current)
        _uiState.update { state ->
            state.copy(
                uninstallQueue = queue,
                currentlyUninstalling = null,
                batchUninstallProgress = state.batchUninstallTotal - queue.size
            )
        }
        processNextUninstall()
    }

    private fun abortUninstallBatch(message: String) {
        _uiState.update {
            it.copy(
                uninstallQueue = emptyList(),
                currentlyUninstalling = null,
                isUninstalling = false,
                errorMessage = message
            )
        }
    }

    private fun processNextUninstall() {
        val queue = _uiState.value.uninstallQueue
        if (queue.isEmpty()) {
            _uiState.update {
                it.copy(
                    currentlyUninstalling = null,
                    isUninstalling = false,
                    selectedPackages = emptySet(),
                    successMessage = null
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                // Give the system a brief moment to complete any asynchronous package removal
                kotlinx.coroutines.delay(1000)
                loadApps(silent = true)
            }
            return
        }

        val nextPkg = queue.first()
        _uiState.update {
            it.copy(
                currentlyUninstalling = nextPkg
            )
        }
    }
}
