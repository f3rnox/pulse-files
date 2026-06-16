package com.f3rno.pulsefiles.ui.viewer

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.f3rno.pulsefiles.model.FileItem

/**
 * An in-app full-screen video player.
 *
 * Plays video files using standard Android VideoView and MediaController inside
 * a clean, immersive dark background.
 *
 * @param item The video file item to play.
 * @param onClose Invoked when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(
    item: FileItem,
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mediaController = MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                        setVideoPath(item.path)
                        start()
                    }
                },
                update = { videoView ->
                    // Video path is already set in factory, no extra update needed.
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { videoView ->
                    videoView.stopPlayback()
                }
            )
        }
    }
}
