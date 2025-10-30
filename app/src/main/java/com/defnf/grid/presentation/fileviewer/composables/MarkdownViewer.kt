package com.defnf.grid.presentation.fileviewer.composables

import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.defnf.grid.presentation.components.LoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    var markdownText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(file) {
        try {
            isLoading = true
            error = null
            markdownText = withContext(Dispatchers.IO) {
                file.readText()
            }
        } catch (e: Exception) {
            error = "Failed to load markdown file: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    when {
        isLoading -> {
            LoadingView(modifier = modifier)
        }
        error != null -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        else -> {
            // Markdown content without header
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        textSize = 16f
                        setTextColor(
                            if (context.resources.configuration.uiMode and 
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                                android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                                android.graphics.Color.WHITE
                            } else {
                                android.graphics.Color.BLACK
                            }
                        )
                        setPadding(32, 32, 32, 32)
                    }
                },
                update = { textView ->
                    val markwon = io.noties.markwon.Markwon.builder(textView.context)
                        .usePlugin(io.noties.markwon.html.HtmlPlugin.create())
                        .build()
                    
                    markwon.setMarkdown(textView, markdownText)
                },
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}