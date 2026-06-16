package com.f3rno.pulsefiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.f3rno.pulsefiles.ui.browser.FileBrowserScreen
import com.f3rno.pulsefiles.ui.clean.CleanScreen

enum class AppTab {
    CLEAN, BROWSE
}

@Composable
fun MainScreen() {
    var currentTab by remember { mutableStateOf(AppTab.BROWSE) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.CLEAN,
                    onClick = { currentTab = AppTab.CLEAN },
                    icon = {
                        Icon(
                            Icons.Outlined.CleaningServices,
                            contentDescription = "Clean"
                        )
                    },
                    label = { Text("Clean") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.BROWSE,
                    onClick = { currentTab = AppTab.BROWSE },
                    icon = {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = "Browse"
                        )
                    },
                    label = { Text("Browse") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                AppTab.CLEAN -> CleanScreen()
                AppTab.BROWSE -> FileBrowserScreen()
            }
        }
    }
}
