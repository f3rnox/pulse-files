package com.f3rno.pulsefiles.util

import com.f3rno.pulsefiles.model.FileCategory
import com.f3rno.pulsefiles.model.FileItem
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg")
private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma")
private val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt")
private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
private val CODE_EXTS = setOf("kt", "java", "js", "ts", "py", "c", "cpp", "h", "json", "xml", "html", "css", "gradle", "sh")

/**
 * Resolves the [FileCategory] of a file based on its type and extension.
 *
 * @param item The file entry to categorize.
 * @return The matching [FileCategory].
 */
fun categoryOf(item: FileItem): FileCategory {
    if (item.isDirectory) return FileCategory.FOLDER
    return when (item.extension) {
        in IMAGE_EXTS -> FileCategory.IMAGE
        in VIDEO_EXTS -> FileCategory.VIDEO
        in AUDIO_EXTS -> FileCategory.AUDIO
        in DOC_EXTS -> FileCategory.DOCUMENT
        in ARCHIVE_EXTS -> FileCategory.ARCHIVE
        in CODE_EXTS -> FileCategory.CODE
        "apk" -> FileCategory.APK
        else -> FileCategory.OTHER
    }
}

/**
 * Formats a byte count into a human readable string (e.g. "1.4 MB").
 *
 * @param bytes The size in bytes.
 * @return A formatted size string.
 */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "${DecimalFormat("#,##0.#").format(value)} ${units[digitGroups]}"
}

/**
 * Formats an epoch millisecond timestamp into a short date-time string.
 *
 * @param millis The timestamp in epoch millis.
 * @return A formatted date string.
 */
fun formatDate(millis: Long): String {
    if (millis <= 0) return "-"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
}

/**
 * Resolves an Android MIME type for a file from its extension.
 *
 * @param item The file entry.
 * @return A best-effort MIME type, defaulting to "* / *".
 */
fun mimeTypeOf(item: FileItem): String {
    return when (categoryOf(item)) {
        FileCategory.IMAGE -> "image/*"
        FileCategory.VIDEO -> "video/*"
        FileCategory.AUDIO -> "audio/*"
        FileCategory.APK -> "application/vnd.android.package-archive"
        FileCategory.DOCUMENT -> when (item.extension) {
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> "*/*"
        }
        else -> "*/*"
    }
}
