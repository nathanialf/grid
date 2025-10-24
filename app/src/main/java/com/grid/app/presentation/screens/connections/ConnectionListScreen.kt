@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.grid.app.presentation.screens.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.style.TextAlign
import android.content.ContextWrapper
import androidx.hilt.navigation.compose.hiltViewModel
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Protocol
import com.grid.app.presentation.theme.GridTheme
import com.grid.app.presentation.components.WavyCircularProgressIndicator

@Composable
fun ConnectionListScreen(
    onNavigateToAddConnection: () -> Unit,
    onNavigateToEditConnection: (String) -> Unit,
    onNavigateToFileBrowser: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConnectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Cast context to FragmentActivity (MainActivity extends FragmentActivity)
    val activity = context as? FragmentActivity
    
    // Auto-trigger biometric authentication when required
    LaunchedEffect(uiState.requiresBiometric, uiState.isBiometricAuthenticated) {
        println("Grid: LaunchedEffect triggered")
        println("Grid: requiresBiometric = ${uiState.requiresBiometric}")
        println("Grid: isBiometricAuthenticated = ${uiState.isBiometricAuthenticated}")
        println("Grid: context type = ${context::class.java.name}")
        println("Grid: activity found = ${activity != null}")
        println("Grid: biometricError = ${uiState.biometricError}")
        
        if (uiState.requiresBiometric && !uiState.isBiometricAuthenticated && activity != null) {
            println("Grid: Conditions met, starting authentication after delay")
            // Add a small delay to ensure the UI is ready
            kotlinx.coroutines.delay(500)
            println("Grid: Calling authenticateWithBiometric")
            viewModel.authenticateWithBiometric(activity)
        } else {
            println("Grid: Conditions not met for auto-authentication")
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // Handle error display if needed
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Grid",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Only show settings when not requiring authentication or already authenticated
                    if (!uiState.requiresBiometric || uiState.isBiometricAuthenticated) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show add button when not requiring authentication or already authenticated
            if (!uiState.requiresBiometric || uiState.isBiometricAuthenticated) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToAddConnection,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Connection")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        when {
            uiState.requiresBiometric && !uiState.isBiometricAuthenticated -> {
                BiometricAuthenticationView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onAuthenticate = { 
                        activity?.let { fragmentActivity ->
                            viewModel.authenticateWithBiometric(fragmentActivity)
                        }
                    },
                    biometricError = uiState.biometricError,
                    onClearError = { viewModel.clearBiometricError() }
                )
            }
            
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    WavyCircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            uiState.connections.isEmpty() -> {
                EmptyConnectionsView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 96.dp // Extra padding for floating toolbar
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.connections) { index, connection ->
                        ConnectionCard(
                            connection = connection,
                            onConnect = { onNavigateToFileBrowser(connection.id) },
                            onEdit = { onNavigateToEditConnection(connection.id) },
                            onDelete = { viewModel.deleteConnection(connection.id) },
                            onMoveUp = if (index > 0) { 
                                { viewModel.reorderConnections(index, index - 1) } 
                            } else null,
                            onMoveDown = if (index < uiState.connections.size - 1) { 
                                { viewModel.reorderConnections(index, index + 1) } 
                            } else null
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        val connectionToDelete = uiState.connections.find { it.id == uiState.connectionToDelete }
        val connectionName = connectionToDelete?.name ?: "this connection"
        
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete \"$connectionName\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteConnection() }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteConfirmation() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyConnectionsView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No server connections",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add your first server connection to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showDropdownMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onConnect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (connection.protocol) {
                            Protocol.FTP -> Icons.Default.Folder
                            Protocol.SFTP -> Icons.Default.Security
                            Protocol.SMB -> Icons.Default.Computer
                        },
                        contentDescription = connection.protocol.name,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = connection.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${connection.hostname}:${connection.effectivePort}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Box {
                    IconButton(onClick = { showDropdownMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showDropdownMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        
                        onMoveUp?.let { moveUp ->
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                onClick = {
                                    showDropdownMenu = false
                                    moveUp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                                }
                            )
                        }
                        
                        onMoveDown?.let { moveDown ->
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                onClick = {
                                    showDropdownMenu = false
                                    moveDown()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showDropdownMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(connection.protocol.name) }
                )
                
                connection.lastConnectedAt?.let {
                    AssistChip(
                        onClick = { },
                        label = { Text("Last used") }
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometricAuthenticationView(
    modifier: Modifier = Modifier,
    onAuthenticate: () -> Unit,
    biometricError: String?,
    onClearError: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Biometric Authentication",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Authentication Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Use your fingerprint or face to access your connections",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = {
            println("Grid: Manual authenticate button clicked")
            onAuthenticate()
        }) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authenticate")
        }
        
        biometricError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear error",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionListScreenPreview() {
    GridTheme {
        ConnectionListScreen(
            onNavigateToAddConnection = {},
            onNavigateToEditConnection = {},
            onNavigateToFileBrowser = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyConnectionListPreview() {
    GridTheme {
        ConnectionListScreen(
            onNavigateToAddConnection = {},
            onNavigateToEditConnection = {},
            onNavigateToFileBrowser = {},
            onNavigateToSettings = {}
        )
    }
}