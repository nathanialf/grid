package com.defnf.grid.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorView(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShapedIcon(
            imageVector = Icons.Default.ErrorOutline,
            shape = MaterialShapes.Cookie4Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(128.dp),
            iconSize = 56.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text("Retry")
        }
    }
}
