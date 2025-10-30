package com.defnf.grid.presentation.fileviewer.composables

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PDFViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Zoom and pan state for PDF pages
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }
    
    // Reset zoom when page changes
    LaunchedEffect(currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    // Initialize PDF renderer
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                withContext(Dispatchers.Main) {
                    pdfRenderer = renderer
                    totalPages = renderer.pageCount
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Error opening PDF: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // Render current page
    LaunchedEffect(pdfRenderer, currentPage) {
        pdfRenderer?.let { renderer ->
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(currentPage)
                    
                    // Use higher resolution for better text rendering
                    val scaleFactor = 3f
                    val width = (page.width * scaleFactor).toInt()
                    val height = (page.height * scaleFactor).toInt()
                    
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    
                    // Set bitmap density for better text rendering
                    bitmap.density = android.util.DisplayMetrics.DENSITY_XHIGH
                    
                    // Fill bitmap with white background to avoid transparency issues
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    
                    // Create transform matrix for higher resolution
                    val matrix = android.graphics.Matrix()
                    matrix.setScale(scaleFactor, scaleFactor)
                    
                    // Try RENDER_MODE_FOR_PRINT first for better text quality
                    try {
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } catch (printException: Exception) {
                        // Fallback to display mode if print mode fails
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                    
                    page.close()
                    
                    withContext(Dispatchers.Main) {
                        pageBitmap = bitmap
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        error = "Error rendering page: ${e.message}"
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(file) {
        onDispose {
            pdfRenderer?.close()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // PDF Controls
        if (totalPages > 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous page"
                        )
                    }
                    
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next page"
                        )
                    }
                }
            }
        }

        // PDF Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Failed to load PDF",
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
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                pageBitmap != null -> {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(transformableState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Text("No content to display")
                }
            }
        }
    }
}