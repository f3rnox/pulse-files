package com.f3rno.pulsefiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f3rno.pulsefiles.ui.MainScreen
import com.f3rno.pulsefiles.ui.permission.StoragePermissionGate
import com.f3rno.pulsefiles.ui.settings.SettingsViewModel
import com.f3rno.pulsefiles.ui.shouldUseDarkTheme
import com.f3rno.pulsefiles.ui.theme.PulseFilesTheme

/**
 * Single-activity host for the Pulse Files browser.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            PulseFilesTheme(
                darkTheme = shouldUseDarkTheme(settingsViewModel),
                dynamicColor = settings.dynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StoragePermissionGate {
                        MainScreen(settingsViewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}
