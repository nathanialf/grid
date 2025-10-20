package com.grid.app.presentation.fileviewer.composables

import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.grid.app.presentation.fileviewer.ebook.EbookViewModel
import com.grid.app.presentation.fileviewer.ebook.EbookUiState
import java.io.File

@Composable
fun EbookViewer(
    file: File,
    modifier: Modifier = Modifier,
    viewModel: EbookViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isNavigationVisible by remember { mutableStateOf(true) }
    
    // Auto-hide navigation after 3 seconds
    LaunchedEffect(isNavigationVisible) {
        if (isNavigationVisible) {
            delay(3000)
            isNavigationVisible = false
        }
    }

    LaunchedEffect(file) {
        viewModel.loadEbook(file)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (uiState) {
            is EbookUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading ebook...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            is EbookUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Failed to load ebook",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = (uiState as EbookUiState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            is EbookUiState.Success -> {
                val successState = uiState as EbookUiState.Success
                
                // Ebook content with tap detection
                EbookContent(
                    bookInfo = successState.bookInfo,
                    currentChapterIndex = successState.currentChapterIndex,
                    onTap = { isNavigationVisible = !isNavigationVisible },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Navigation controls with slide animation
                AnimatedVisibility(
                    visible = isNavigationVisible,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    EbookNavigationControls(
                        currentChapter = successState.currentChapterIndex + 1,
                        totalChapters = successState.bookInfo.totalChapters,
                        chapterTitle = successState.bookInfo.chapters.getOrNull(successState.currentChapterIndex)?.title ?: "",
                        onPreviousChapter = {
                            viewModel.previousChapter()
                            isNavigationVisible = true
                        },
                        onNextChapter = {
                            viewModel.nextChapter()
                            isNavigationVisible = true
                        },
                        canNavigatePrevious = successState.currentChapterIndex > 0,
                        canNavigateNext = successState.currentChapterIndex < successState.bookInfo.totalChapters - 1
                    )
                }
            }
            
            is EbookUiState.ResumePrompt -> {
                val resumeState = uiState as EbookUiState.ResumePrompt
                ResumeReadingDialog(
                    bookTitle = resumeState.bookInfo.title,
                    chapterTitle = resumeState.savedPosition.chapterTitle ?: "Chapter ${resumeState.savedPosition.spineIndex + 1}",
                    onResume = { viewModel.resumeReading() },
                    onStartOver = { viewModel.startFromBeginning() }
                )
            }
        }
    }
}

@Composable
fun EbookContent(
    bookInfo: com.grid.app.presentation.fileviewer.ebook.EpubBookInfo,
    currentChapterIndex: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentChapter = bookInfo.chapters.getOrNull(currentChapterIndex)
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    
    if (currentChapter != null) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    
                    settings.apply {
                        javaScriptEnabled = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    
                    // Set background color to match theme
                    setBackgroundColor(backgroundColor.toArgb())
                    
                    // Handle touch events to detect taps without interfering with scrolling
                    var startY = 0f
                    var startTime = 0L
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startY = event.y
                                startTime = System.currentTimeMillis()
                                false // Let WebView handle the event
                            }
                            MotionEvent.ACTION_UP -> {
                                val endY = event.y
                                val endTime = System.currentTimeMillis()
                                val deltaY = kotlin.math.abs(endY - startY)
                                val deltaTime = endTime - startTime
                                
                                // Only trigger tap if it's a quick tap without much movement
                                if (deltaY < 50 && deltaTime < 300) {
                                    onTap()
                                }
                                false // Let WebView handle the event
                            }
                            else -> false // Let WebView handle all other events
                        }
                    }
                }
            },
            update = { webView ->
                // Update background color when theme changes
                webView.setBackgroundColor(backgroundColor.toArgb())
                
                // Process chapter content to replace image sources with data URLs
                var processedContent = currentChapter.content
                
                // Replace image sources with data URLs
                bookInfo.imagePaths.forEach { (imagePath, imageData) ->
                    val mimeType = when {
                        imagePath.endsWith(".png", ignoreCase = true) -> "image/png"
                        imagePath.endsWith(".jpg", ignoreCase = true) || imagePath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                        imagePath.endsWith(".gif", ignoreCase = true) -> "image/gif"
                        imagePath.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                        imagePath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                        else -> "image/png"
                    }
                    
                    val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
                    val dataUrl = "data:$mimeType;base64,$base64Image"
                    
                    // Replace various possible image source patterns
                    processedContent = processedContent.replace("src=\"$imagePath\"", "src=\"$dataUrl\"")
                    processedContent = processedContent.replace("src='$imagePath'", "src='$dataUrl'")
                    // Also handle relative paths that might include ../ or ./
                    val filename = imagePath.substringAfterLast('/')
                    processedContent = processedContent.replace("src=\"$filename\"", "src=\"$dataUrl\"")
                    processedContent = processedContent.replace("src='$filename'", "src='$dataUrl'")
                    // Handle paths that might have ../ prefixes
                    processedContent = processedContent.replace("src=\"../$imagePath\"", "src=\"$dataUrl\"")
                    processedContent = processedContent.replace("src='../$imagePath'", "src='$dataUrl'")
                    processedContent = processedContent.replace("src=\"./$imagePath\"", "src=\"$dataUrl\"")
                    processedContent = processedContent.replace("src='./$imagePath'", "src='$dataUrl'")
                }
                
                // Generate themed HTML content
                val styledHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: serif;
                                font-size: 18px;
                                line-height: 1.6;
                                margin: 16px;
                                color: ${String.format("#%06X", textColor.toArgb() and 0xFFFFFF)};
                                background-color: ${String.format("#%06X", backgroundColor.toArgb() and 0xFFFFFF)};
                            }
                            h1, h2, h3, h4, h5, h6 {
                                font-family: sans-serif;
                                margin-top: 24px;
                                margin-bottom: 16px;
                                color: ${String.format("#%06X", textColor.toArgb() and 0xFFFFFF)};
                            }
                            p {
                                margin-bottom: 16px;
                                text-align: justify;
                            }
                            img {
                                max-width: 100%;
                                height: auto;
                                display: block;
                                margin: 16px auto;
                            }
                            a {
                                color: ${String.format("#%06X", textColor.toArgb() and 0xFFFFFF)};
                            }
                        </style>
                    </head>
                    <body>
                        $processedContent
                    </body>
                    </html>
                """.trimIndent()
                
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        )
    }
}

@Composable
fun EbookNavigationControls(
    currentChapter: Int,
    totalChapters: Int,
    chapterTitle: String,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chapter info
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Chapter $currentChapter of $totalChapters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Navigation controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousChapter,
                    enabled = canNavigatePrevious
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous chapter"
                    )
                }
                
                // Progress indicator
                LinearProgressIndicator(
                    progress = { currentChapter.toFloat() / totalChapters },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                
                IconButton(
                    onClick = onNextChapter,
                    enabled = canNavigateNext
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next chapter"
                    )
                }
            }
        }
    }
}

@Composable
fun ResumeReadingDialog(
    bookTitle: String,
    chapterTitle: String,
    onResume: () -> Unit,
    onStartOver: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStartOver,
        icon = {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null
            )
        },
        title = {
            Text("Resume Reading?")
        },
        text = {
            Column {
                Text("You were reading:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Resume")
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text("Start Over")
            }
        }
    )
}