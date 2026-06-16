package com.f3rno.pulsefiles.ui.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.f3rno.pulsefiles.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An in-app full-screen plain text editor.
 *
 * Reads file contents, allows editing, and saves the modifications back to disk.
 *
 * @param item The text file item to view/edit.
 * @param onClose Invoked when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditor(
    item: FileItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var originalText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(item.path) {
        isLoading = true
        loadError = false
        val fileContent = withContext(Dispatchers.IO) {
            runCatching {
                File(item.path).readText(Charsets.UTF_8)
            }.getOrNull()
        }
        if (fileContent != null) {
            text = fileContent
            originalText = fileContent
        } else {
            loadError = true
        }
        isLoading = false
    }

    val isModified = text != originalText

    BackHandler {
        onClose()
    }

    fun saveContent() {
        isSaving = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    File(item.path).writeText(text, Charsets.UTF_8)
                    true
                }.getOrDefault(false)
            }
            if (success) {
                originalText = text
                Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
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
                actions = {
                    IconButton(
                        onClick = { saveContent() },
                        enabled = isModified && !isSaving && !isLoading && !loadError
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save file")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading file...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                loadError -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Failed to read text file.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        placeholder = { Text("Start typing...") }
                    )
                }
            }
        }
    }
}
