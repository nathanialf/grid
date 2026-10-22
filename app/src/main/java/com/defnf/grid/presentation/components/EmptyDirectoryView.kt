package com.defnf.grid.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyDirectoryView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShapedIcon(
            imageVector = Icons.Default.FolderOpen,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(128.dp),
            iconSize = 56.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "This folder is empty",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No files or folders to display",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
