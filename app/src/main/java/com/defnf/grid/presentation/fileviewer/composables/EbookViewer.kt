package com.defnf.grid.presentation.fileviewer.composables

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.defnf.grid.presentation.fileviewer.ebook.EbookViewModel
import com.defnf.grid.presentation.fileviewer.ebook.EbookUiState
import com.defnf.grid.presentation.fileviewer.ebook.HtmlToCompose
import android.util.Base64
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
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
                
                EbookPagerContent(
                    bookInfo = successState.bookInfo,
                    currentChapterIndex = successState.currentChapterIndex,
                    scrollOffset = successState.scrollOffset,
                    onChapterChanged = viewModel::navigateToChapter,
                    onScrollPositionChanged = viewModel::updateScrollPosition,
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
fun EbookPagerContent(
    bookInfo: com.defnf.grid.presentation.fileviewer.ebook.EpubBookInfo,
    currentChapterIndex: Int,
    scrollOffset: Float,
    onChapterChanged: (Int) -> Unit,
    onScrollPositionChanged: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = currentChapterIndex,
        pageCount = { bookInfo.totalChapters }
    )
    
    // Sync pager state with ViewModel
    LaunchedEffect(currentChapterIndex) {
        if (pagerState.currentPage != currentChapterIndex) {
            pagerState.animateScrollToPage(currentChapterIndex)
        }
    }
    
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentChapterIndex) {
            onChapterChanged(pagerState.currentPage)
        }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        pageSpacing = 16.dp
    ) { pageIndex ->
        val chapter = bookInfo.chapters.getOrNull(pageIndex)
        
        if (chapter != null) {
            ChapterContent(
                chapter = chapter,
                bookInfo = bookInfo,
                scrollOffset = if (pageIndex == currentChapterIndex) scrollOffset else 0f,
                onScrollPositionChanged = { offset ->
                    if (pageIndex == currentChapterIndex) {
                        onScrollPositionChanged(offset)
                    }
                },
                onTap = onTap,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun ChapterContent(
    chapter: com.defnf.grid.presentation.fileviewer.ebook.EpubChapter,
    bookInfo: com.defnf.grid.presentation.fileviewer.ebook.EpubBookInfo,
    scrollOffset: Float,
    onScrollPositionChanged: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val TAG = "ChapterContent"
    val lazyListState = rememberLazyListState()
    val htmlToCompose = com.defnf.grid.presentation.fileviewer.ebook.HtmlToCompose
    
    Log.d(TAG, "ChapterContent composing for chapter: '${chapter.title}' (${chapter.spineIndex})")
    Log.d(TAG, "Chapter content length: ${chapter.content.length} characters")
    
    // Parse HTML to get both text and images
    val parsedContent = htmlToCompose.parseHtmlToContent(chapter.content)
    
    Log.d(TAG, "Parsed content - text length: ${parsedContent.text.text.length}, images: ${parsedContent.images.size}")
    
    // Restore scroll position
    LaunchedEffect(scrollOffset) {
        if (scrollOffset > 0 && lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
            lazyListState.scrollToItem(0, scrollOffset.toInt())
        }
    }
    
    // Save scroll position
    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        val offset = if (lazyListState.firstVisibleItemIndex == 0) {
            lazyListState.firstVisibleItemScrollOffset.toFloat()
        } else {
            lazyListState.firstVisibleItemIndex * 1000f + lazyListState.firstVisibleItemScrollOffset
        }
        onScrollPositionChanged(offset)
    }
    
    // Log text parts for debugging
    val textParts = splitTextByImages(parsedContent)
    Log.d(TAG, "Split content into ${textParts.size} parts")
    
    // Log each part type and length for debugging
    textParts.forEachIndexed { index, part ->
        when (part) {
            is ContentPart.TextPart -> Log.d(TAG, "Part $index: Text (${part.text.text.length} chars)")
            is ContentPart.ImagePart -> Log.d(TAG, "Part $index: Image (${part.imageInfo.src})")
        }
    }
    
    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() }
                )
            },
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(textParts) { contentPart ->
            when (contentPart) {
                is ContentPart.TextPart -> {
                    // Log if text content is empty for debugging
                    if (contentPart.text.text.isBlank()) {
                        Log.w(TAG, "Rendering empty text part")
                    }
                    androidx.compose.material3.Text(
                        text = contentPart.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            lineHeight = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ContentPart.ImagePart -> {
                    Log.d(TAG, "Rendering image: ${contentPart.imageInfo.src}")
                    EbookImage(
                        imageInfo = contentPart.imageInfo,
                        bookInfo = bookInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// Data classes for content parts
sealed class ContentPart {
    data class TextPart(val text: AnnotatedString) : ContentPart()
    data class ImagePart(val imageInfo: com.defnf.grid.presentation.fileviewer.ebook.ImageInfo) : ContentPart()
}

// Function to split text content by images
private fun splitTextByImages(parsedContent: com.defnf.grid.presentation.fileviewer.ebook.ParsedContent): List<ContentPart> {
    val result = mutableListOf<ContentPart>()
    val text = parsedContent.text
    val images = parsedContent.images.sortedBy { it.positionInText }
    
    var lastPosition = 0
    
    for (image in images) {
        // Add text before image
        if (image.positionInText > lastPosition) {
            val textPart = text.subSequence(lastPosition, image.positionInText)
            if (textPart.isNotEmpty()) {
                result.add(ContentPart.TextPart(textPart))
            }
        }
        
        // Add image
        result.add(ContentPart.ImagePart(image))
        
        // Skip the placeholder text in the original string
        val placeholder = image.alt?.let { "[$it]" } ?: "[Image]"
        lastPosition = image.positionInText + placeholder.length
    }
    
    // Add remaining text
    if (lastPosition < text.length) {
        val textPart = text.subSequence(lastPosition, text.length)
        if (textPart.isNotEmpty()) {
            result.add(ContentPart.TextPart(textPart))
        }
    }
    
    return result
}

@Composable
fun EbookImage(
    imageInfo: com.defnf.grid.presentation.fileviewer.ebook.ImageInfo,
    bookInfo: com.defnf.grid.presentation.fileviewer.ebook.EpubBookInfo,
    modifier: Modifier = Modifier
) {
    val TAG = "EbookImage"
    Log.d(TAG, "Attempting to display image: ${imageInfo.src}")
    
    // Try to find the image in the book's image data
    val imageData = bookInfo.imagePaths[imageInfo.src] 
        ?: bookInfo.imagePaths[imageInfo.src.substringAfterLast('/')]
    
    if (imageData != null) {
        Log.d(TAG, "Found image data for: ${imageInfo.src} (${imageData.size} bytes)")
        val bitmap = remember(imageData) {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        }
        
        if (bitmap != null) {
            Log.d(TAG, "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = imageInfo.alt,
                modifier = modifier
                    .wrapContentHeight()
                    .padding(vertical = 8.dp)
            )
        } else {
            Log.w(TAG, "Failed to decode bitmap for image: ${imageInfo.src}")
            // Fallback text if bitmap decoding fails
            Text(
                text = imageInfo.alt ?: "[Image]",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(vertical = 8.dp)
            )
        }
    } else {
        Log.w(TAG, "Image data not found for: ${imageInfo.src}")
        Log.d(TAG, "Available image keys: ${bookInfo.imagePaths.keys.joinToString(", ")}")
        // Fallback text if image not found
        Text(
            text = imageInfo.alt ?: "[Image: ${imageInfo.src}]",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp)
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