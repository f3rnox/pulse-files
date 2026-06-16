package com.f3rno.pulsefiles.ui.permission

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.f3rno.pulsefiles.util.hasWritableStorageAccess
import com.f3rno.pulsefiles.util.openAllFilesAccessSettings

/**
 * Gates its content behind storage access, showing a rationale and request
 * flow until permission is granted. Re-checks access on resume so returning
 * from system settings updates the UI.
 *
 * @param content The protected content to display once access is granted.
 */
@Composable
fun StoragePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    fun checkAccess(): Boolean = hasWritableStorageAccess(appContext)

    var granted by remember { mutableStateOf(checkAccess()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = checkAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = checkAccess() }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            openAllFilesAccessSettings(context)
        }
    }

    if (granted) {
        key("storage-granted") {
            content()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = "Storage access required",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "Pulse Files needs \"All files access\" to browse and manage files on your device. " +
                    "Enable it on the next screen, then return here."
            } else {
                "Pulse Files needs storage access to browse, delete, and manage your files."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = { requestPermissions() },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Allow all files access" else "Grant access")
        }
    }
}
