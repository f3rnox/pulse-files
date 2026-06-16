package com.f3rno.pulsefiles.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.f3rno.pulsefiles.model.FileItem
import java.util.ArrayList

/**
 * Returns the shareable content URI for a file via the app's FileProvider.
 *
 * @param context A context to resolve the authority.
 * @param item The file entry.
 * @return A content [Uri] usable across apps.
 */
private fun uriFor(context: Context, item: FileItem): Uri {
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, item.file)
}

/**
 * Opens a file in an external app using its resolved MIME type.
 *
 * @param context A context to start the activity.
 * @param item The file entry to open.
 */
fun openFile(context: Context, item: FileItem) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, item), mimeTypeOf(item))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }.onFailure {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shares one or more files through the Android share sheet.
 *
 * @param context A context to start the activity.
 * @param items The file entries to share (directories are ignored).
 */
fun shareFiles(context: Context, items: List<FileItem>) {
    val files = items.filter { !it.isDirectory }
    if (files.isEmpty()) {
        Toast.makeText(context, "Folders cannot be shared", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uris = ArrayList(files.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeOf(files.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }.onFailure {
        Toast.makeText(context, "Unable to share", Toast.LENGTH_SHORT).show()
    }
}
