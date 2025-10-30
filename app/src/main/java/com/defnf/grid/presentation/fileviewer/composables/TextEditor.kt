package com.defnf.grid.presentation.fileviewer.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.EditText
import android.widget.ScrollView
import android.text.InputType
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import android.view.ViewTreeObserver
import android.text.Layout
import android.graphics.Rect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

data class TextEditorActions(
    val save: () -> Unit,
    val exit: () -> Unit,
    val hasUnsavedChanges: Boolean,
    val isSaving: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TextEditor(
    file: File,
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit = {},
    onExit: () -> Unit = {},
    onActionsChanged: (TextEditorActions) -> Unit = {}
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val hasUnsavedChanges = textFieldValue.text != originalContent
    
    val saveAction = {
        if (!isSaving && hasUnsavedChanges) {
            coroutineScope.launch {
                isSaving = true
                onSave(textFieldValue.text)
                isSaving = false
            }
        }
    }
    
    val exitAction = {
        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onExit()
        }
    }
    
    
    // Expose actions to parent
    LaunchedEffect(hasUnsavedChanges, isSaving) {
        onActionsChanged(
            TextEditorActions(
                save = saveAction,
                exit = exitAction,
                hasUnsavedChanges = hasUnsavedChanges,
                isSaving = isSaving
            )
        )
    }
    
    LaunchedEffect(file) {
        try {
            isLoading = true
            error = null
            val fileContent = withContext(Dispatchers.IO) {
                file.readText()
            }
            textFieldValue = TextFieldValue(fileContent)
            originalContent = fileContent
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    // Unsaved changes dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to exit without saving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExit()
                    }
                ) {
                    Text("Exit Without Saving")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
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
            // Text editor using ScrollView + EditText for smooth scrolling
            val textColor = MaterialTheme.colorScheme.onSurface
            val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            val backgroundColor = MaterialTheme.colorScheme.surface
            
            Surface(
                modifier = modifier.fillMaxSize(),
                color = backgroundColor
            ) {
                AndroidView(
                    factory = { context ->
                        ScrollView(context).apply {
                            // Configure ScrollView for smooth scrolling
                            isSmoothScrollingEnabled = true
                            isVerticalScrollBarEnabled = true
                            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            
                            val editText = EditText(context).apply {
                                // Set up the EditText for multiline text editing
                                inputType = InputType.TYPE_CLASS_TEXT or 
                                           InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                           InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                
                                // Set monospace font
                                typeface = Typeface.MONOSPACE
                                
                                // Remove background and borders
                                setBackgroundResource(android.R.color.transparent)
                                
                                // Set text size to match Material3 bodyMedium
                                textSize = 14f
                                
                                // Set padding
                                setPadding(48, 48, 48, 48)
                                
                                // Set text colors from theme
                                setTextColor(textColor.toArgb())
                                setHintTextColor(hintColor.toArgb())
                                
                                // Set hint
                                hint = "Start typing..."
                                
                                // Disable EditText's own scrolling - ScrollView handles it
                                isVerticalScrollBarEnabled = false
                                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                                
                                // Set initial text
                                setText(textFieldValue.text)
                                setSelection(textFieldValue.selection.start)
                                
                                // Set up text change listener
                                addTextChangedListener(object : android.text.TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                    override fun afterTextChanged(s: android.text.Editable?) {
                                        val newText = s.toString()
                                        val newSelection = selectionStart
                                        textFieldValue = TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(newSelection)
                                        )
                                    }
                                })
                                
                                // Set up focus change listener
                                setOnFocusChangeListener { _, hasFocusState ->
                                    hasFocus = hasFocusState
                                }
                            }
                            
                            // Add EditText to ScrollView
                            addView(editText)
                            
                            // Set up global layout listener for keyboard detection
                            var wasKeyboardOpen = false
                            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    val rect = Rect()
                                    getWindowVisibleDisplayFrame(rect)
                                    val screenHeight = rootView.height
                                    val keypadHeight = screenHeight - rect.bottom
                                    val isKeyboardOpen = keypadHeight > screenHeight * 0.15
                                    
                                    // If keyboard just opened and EditText has focus
                                    if (isKeyboardOpen && !wasKeyboardOpen && editText.hasFocus()) {
                                        // Calculate cursor position and scroll only if needed
                                        post {
                                            val layout = editText.layout
                                            if (layout != null) {
                                                val cursorLine = layout.getLineForOffset(editText.selectionStart)
                                                val cursorY = layout.getLineTop(cursorLine)
                                                
                                                // Calculate cursor position relative to current viewport
                                                val currentScroll = scrollY
                                                val cursorRelativeToViewport = cursorY - currentScroll
                                                
                                                // Calculate where keyboard will appear on screen
                                                val keyboardTop = rect.bottom
                                                val keyboardTopRelativeToViewport = keyboardTop
                                                
                                                // Only scroll if cursor is behind/below the keyboard in viewport
                                                if (cursorRelativeToViewport > keyboardTopRelativeToViewport - 100) {
                                                    // Minimal scroll: just move enough to put cursor above keyboard
                                                    val targetPosition = keyboardTopRelativeToViewport - 150 // 150px above keyboard
                                                    val scrollAdjustment = cursorRelativeToViewport - targetPosition
                                                    smoothScrollTo(0, currentScroll + scrollAdjustment)
                                                }
                                            }
                                        }
                                    }
                                    wasKeyboardOpen = isKeyboardOpen
                                }
                            })
                        }
                    },
                    update = { scrollView ->
                        val editText = scrollView.getChildAt(0) as EditText
                        
                        // Update colors in case theme changed
                        editText.setTextColor(textColor.toArgb())
                        editText.setHintTextColor(hintColor.toArgb())
                        
                        // Update text if it changed from outside
                        if (editText.text.toString() != textFieldValue.text) {
                            editText.setText(textFieldValue.text)
                            editText.setSelection(
                                minOf(textFieldValue.selection.start, editText.text.length)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}