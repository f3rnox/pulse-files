package com.f3rno.pulsefiles.ui.browser

import com.f3rno.pulsefiles.model.FileItem
import com.f3rno.pulsefiles.model.SortOrder

/**
 * Clipboard operation type used when copying or moving files.
 */
enum class ClipboardMode { COPY, MOVE }

/**
 * Holds files staged for a paste action and the operation to perform.
 *
 * @property items The staged file entries.
 * @property mode Whether the staged items will be copied or moved.
 */
data class Clipboard(
    val items: List<FileItem>,
    val mode: ClipboardMode
)

/**
 * Immutable UI state for the file browser screen.
 *
 * @property currentPath Absolute path of the directory being viewed.
 * @property items Visible file entries after filtering and sorting.
 * @property selected Paths of currently selected entries.
 * @property selectionMode Whether multi-select mode is active.
 * @property isLoading Whether a directory load is in progress.
 * @property sortOrder Active sort order.
 * @property showHidden Whether hidden files are shown.
 * @property confirmBeforeDelete Whether deletes require confirmation.
 * @property searchQuery Current search filter text, or null when search is closed.
 * @property clipboard Staged copy/move clipboard, or null when empty.
 * @property canNavigateUp Whether the parent directory is reachable.
 * @property accessDenied Whether the current directory could not be read.
 * @property errorMessage Transient error to surface, or null.
 */
data class BrowserUiState(
    val currentPath: String = "",
    val items: List<FileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val showHidden: Boolean = false,
    val confirmBeforeDelete: Boolean = true,
    val searchQuery: String? = null,
    val clipboard: Clipboard? = null,
    val canNavigateUp: Boolean = false,
    val accessDenied: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedItems: List<FileItem> get() = items.filter { it.path in selected }
}
