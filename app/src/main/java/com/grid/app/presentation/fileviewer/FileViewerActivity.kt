package com.grid.app.presentation.fileviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grid.app.presentation.theme.GridTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GridTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FileViewerContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun FileViewerContent(modifier: Modifier = Modifier) {
    Text(
        text = "File Viewer",
        modifier = modifier
    )
}