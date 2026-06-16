package com.f3rno.pulsefiles.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f3rno.pulsefiles.data.SettingsStore
import com.f3rno.pulsefiles.data.FileManager
import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.model.SortOrder
import com.f3rno.pulsefiles.util.primaryStoragePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drives the file browser screen: navigation, selection, sorting, search and
 * file operations backed by [FileManager].
 */
class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager(application.applicationContext)

    private val settingsStore = SettingsStore.get(application)

    private val rootPath: String = primaryStoragePath(application)

    private val _uiState = MutableStateFlow(BrowserUiState())

    /** Observable UI state for the browser screen. */
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        val settings = settingsStore.snapshot()
        _uiState.update {
            it.copy(
                sortOrder = settings.defaultSortOrder,
                showHidden = settings.showHiddenByDefault,
                confirmBeforeDelete = settings.confirmBeforeDelete
            )
        }
    }

    /**
     * Loads the configured start directory. Call once permission is granted.
     */
    fun start() {
        if (_uiState.value.currentPath.isEmpty()) {
            navigateTo(settingsStore.resolveBrowserStartPath(rootPath))
        }
    }

    /**
     * Navigates to a directory and refreshes the listing.
     *
     * @param path Absolute path of the directory to open.
     */
    fun navigateTo(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            _uiState.update {
                it.copy(
                    errorMessage = "Folder not found",
                    isLoading = false,
                    accessDenied = false
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                currentPath = path,
                isLoading = true,
                selected = emptySet(),
                selectionMode = false,
                searchQuery = null,
                accessDenied = false,
                canNavigateUp = path != rootPath && dir.parentFile != null
            )
        }
        if (settingsStore.snapshot().rememberLastFolder) {
            settingsStore.setLastBrowsedPath(path)
        }
        refresh()
    }

    /**
     * Navigates to the parent of the current directory when possible.
     */
    fun navigateUp() {
        val parent = File(_uiState.value.currentPath).parentFile ?: return
        if (_uiState.value.currentPath == rootPath) return
        navigateTo(parent.absolutePath)
    }

    /**
     * Re-reads the current directory applying the active sort and filters.
     */
    fun refresh() {
        val state = _uiState.value
        if (state.currentPath.isEmpty()) return
        val query = state.searchQuery
        if (query != null) {
            runSearch(query)
            return
        }
        viewModelScope.launch {
            val listing = withContext(Dispatchers.IO) {
                fileManager.list(File(state.currentPath), state.sortOrder, state.showHidden)
            }
            _uiState.update {
                it.copy(
                    items = listing.items,
                    isLoading = false,
                    accessDenied = listing.accessDenied,
                    errorMessage = if (listing.accessDenied) {
                        "Cannot read this folder. Grant \"All files access\" in system settings."
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * Handles a tap on an entry: opens directories or toggles selection while
     * in selection mode.
     *
     * @param item The tapped entry.
     */
    fun onItemClick(item: FileItem) {
        if (_uiState.value.selectionMode) {
            toggleSelection(item)
        } else if (item.isDirectory) {
            navigateTo(item.path)
        }
    }

    /**
     * Enters selection mode for an entry via long press.
     *
     * @param item The long-pressed entry.
     */
    fun onItemLongClick(item: FileItem) {
        _uiState.update {
            it.copy(selectionMode = true, selected = it.selected + item.path)
        }
    }

    /**
     * Toggles whether an entry is selected.
     *
     * @param item The entry to toggle.
     */
    fun toggleSelection(item: FileItem) {
        _uiState.update {
            val selected = if (item.path in it.selected) it.selected - item.path else it.selected + item.path
            it.copy(selected = selected, selectionMode = selected.isNotEmpty())
        }
    }

    /** Selects every visible entry. */
    fun selectAll() {
        _uiState.update { it.copy(selected = it.items.map { item -> item.path }.toSet(), selectionMode = true) }
    }

    /** Clears the current selection and exits selection mode. */
    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet(), selectionMode = false) }
    }

    /**
     * Updates the sort order and refreshes.
     *
     * @param order The new sort order.
     */
    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        settingsStore.update { it.copy(defaultSortOrder = order) }
        refresh()
    }

    /** Toggles visibility of hidden files and refreshes. */
    fun toggleHidden() {
        val showHidden = !_uiState.value.showHidden
        _uiState.update { it.copy(showHidden = showHidden) }
        settingsStore.update { it.copy(showHiddenByDefault = showHidden) }
        refresh()
    }

    /**
     * Opens, closes or updates the search query, filtering the listing.
     *
     * @param query The query text, or null to close search.
     */
    fun setSearchQuery(query: String?) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query == null) {
            refresh()
            return
        }
        runSearch(query)
    }

    private fun runSearch(query: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val settings = settingsStore.snapshot()
            val listing = withContext(Dispatchers.IO) {
                if (settings.recursiveSearch) {
                    fileManager.search(File(state.currentPath), query, state.sortOrder, state.showHidden)
                } else {
                    val local = fileManager.list(File(state.currentPath), state.sortOrder, state.showHidden)
                    local.copy(
                        items = local.items.filter { it.name.contains(query, ignoreCase = true) }
                    )
                }
            }
            _uiState.update {
                it.copy(
                    items = listing.items,
                    isLoading = false,
                    accessDenied = listing.accessDenied,
                    errorMessage = if (listing.accessDenied) {
                        "Cannot read this folder. Grant \"All files access\" in system settings."
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * Creates a new folder in the current directory.
     *
     * @param name The folder name.
     */
    fun createFolder(name: String) = runOp {
        fileManager.createFolder(File(_uiState.value.currentPath), name)
    }

    /**
     * Renames the given entry.
     *
     * @param item The entry to rename.
     * @param newName The new name.
     */
    fun rename(item: FileItem, newName: String) = runOp {
        fileManager.rename(item, newName)
    }

    /** Deletes all currently selected entries. */
    fun deleteSelected() = runOp {
        fileManager.delete(_uiState.value.selectedItems)
    }

    /**
     * Stages the current selection for a copy operation.
     */
    fun copySelection() {
        _uiState.update {
            it.copy(clipboard = Clipboard(it.selectedItems, ClipboardMode.COPY), selected = emptySet(), selectionMode = false)
        }
    }

    /**
     * Stages the current selection for a move operation.
     */
    fun cutSelection() {
        _uiState.update {
            it.copy(clipboard = Clipboard(it.selectedItems, ClipboardMode.MOVE), selected = emptySet(), selectionMode = false)
        }
    }

    /** Clears the clipboard without pasting. */
    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    /**
     * Pastes the clipboard contents into the current directory.
     */
    fun paste() {
        pasteTo(_uiState.value.currentPath)
    }

    /**
     * Pastes the clipboard contents into the given directory.
     *
     * @param destinationPath Absolute path of the destination folder.
     */
    fun pasteTo(destinationPath: String) {
        val clipboard = _uiState.value.clipboard ?: return
        val destination = File(destinationPath)
        if (!destination.exists() || !destination.isDirectory) {
            _uiState.update { it.copy(errorMessage = "Folder not found") }
            return
        }
        if (clipboard.mode == ClipboardMode.MOVE && !canMoveTo(destinationPath, clipboard.items)) {
            _uiState.update { it.copy(errorMessage = "Cannot move items into this folder") }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (clipboard.mode) {
                    ClipboardMode.COPY -> fileManager.copy(clipboard.items, destination)
                    ClipboardMode.MOVE -> fileManager.move(clipboard.items, destination)
                }
            }
            _uiState.update {
                it.copy(clipboard = null, errorMessage = if (ok) null else "Some items could not be pasted")
            }
            if (ok && _uiState.value.currentPath != destinationPath) {
                navigateTo(destinationPath)
            } else {
                refresh()
            }
        }
    }

    /**
     * Returns whether [destinationPath] is a valid move target for [items].
     */
    fun canMoveTo(destinationPath: String, items: List<FileItem>): Boolean {
        if (items.isEmpty()) return true
        val dest = runCatching { File(destinationPath).canonicalPath }.getOrDefault(destinationPath)
        return items.none { item ->
            if (!item.isDirectory) return@none false
            val source = runCatching { item.file.canonicalPath }.getOrDefault(item.path)
            dest == source || dest.startsWith("$source/")
        }
    }

    /** Applies latest settings from the settings store. */
    fun applySettings() {
        val settings = settingsStore.snapshot()
        _uiState.update {
            it.copy(
                sortOrder = settings.defaultSortOrder,
                showHidden = settings.showHiddenByDefault,
                confirmBeforeDelete = settings.confirmBeforeDelete
            )
        }
        refresh()
    }

    /** Clears any transient error message. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Runs a blocking file operation off the main thread, then clears the
     * selection and refreshes the listing.
     *
     * @param block The operation returning success.
     */
    private fun runOp(block: () -> Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            _uiState.update {
                it.copy(
                    selected = emptySet(),
                    selectionMode = false,
                    errorMessage = if (ok) null else "Operation failed"
                )
            }
            refresh()
        }
    }
}
