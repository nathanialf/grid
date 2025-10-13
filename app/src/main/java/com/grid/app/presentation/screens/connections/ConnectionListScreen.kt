@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.grid.app.presentation.screens.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Protocol
import com.grid.app.presentation.theme.GridTheme

@Composable
fun ConnectionListScreen(
    onNavigateToAddConnection: () -> Unit,
    onNavigateToEditConnection: (String) -> Unit,
    onNavigateToFileBrowser: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConnectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddConnection
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Connection"
                )
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            uiState.connections.isEmpty() -> {
                EmptyConnectionsView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onAddConnection = onNavigateToAddConnection
                )
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.connections) { connection ->
                        ConnectionCard(
                            connection = connection,
                            onConnect = { onNavigateToFileBrowser(connection.id) },
                            onEdit = { onNavigateToEditConnection(connection.id) },
                            onDelete = { viewModel.deleteConnection(connection.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConnectionsView(
    modifier: Modifier = Modifier,
    onAddConnection: () -> Unit
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onAddConnection) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Connection")
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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