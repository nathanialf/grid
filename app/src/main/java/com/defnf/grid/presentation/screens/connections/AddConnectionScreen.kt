@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.defnf.grid.presentation.screens.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.presentation.theme.GridTheme
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator

@Composable
fun AddConnectionScreen(
    onNavigateBack: () -> Unit,
    onConnectionSaved: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onConnectionSaved()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Connection",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveConnection() },
                        enabled = uiState.formData.isValid() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            WavyCircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        ConnectionForm(
            formData = uiState.formData,
            onFormDataChange = viewModel::updateFormData,
            onTestConnection = viewModel::testConnection,
            isTestingConnection = uiState.isTesting,
            testResult = uiState.testResult,
            error = uiState.error,
            onClearError = viewModel::clearError,
            onClearTestResult = viewModel::clearTestResult,
            hasGeneratedKey = uiState.hasGeneratedKey,
            generatedPublicKey = uiState.generatedPublicKey,
            isGeneratingKey = uiState.isGeneratingKey,
            onGenerateKey = viewModel::generateSshKey,
            onUseGeneratedKey = viewModel::useGeneratedKey,
            onClearKey = viewModel::clearSshKey,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
fun EditConnectionScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    onConnectionSaved: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(connectionId) {
        viewModel.loadConnection(connectionId)
    }
    
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onConnectionSaved()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Connection",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveConnection() },
                        enabled = uiState.formData.isValid() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            WavyCircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        ConnectionForm(
            formData = uiState.formData,
            onFormDataChange = viewModel::updateFormData,
            onTestConnection = viewModel::testConnection,
            isTestingConnection = uiState.isTesting,
            testResult = uiState.testResult,
            error = uiState.error,
            onClearError = viewModel::clearError,
            onClearTestResult = viewModel::clearTestResult,
            hasGeneratedKey = uiState.hasGeneratedKey,
            generatedPublicKey = uiState.generatedPublicKey,
            isGeneratingKey = uiState.isGeneratingKey,
            onGenerateKey = viewModel::generateSshKey,
            onUseGeneratedKey = viewModel::useGeneratedKey,
            onClearKey = viewModel::clearSshKey,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun ConnectionForm(
    formData: ConnectionFormData,
    onFormDataChange: (ConnectionFormData) -> Unit,
    onTestConnection: () -> Unit = {},
    isTestingConnection: Boolean = false,
    testResult: String? = null,
    error: String? = null,
    onClearError: () -> Unit = {},
    onClearTestResult: () -> Unit = {},
    hasGeneratedKey: Boolean = false,
    generatedPublicKey: String? = null,
    isGeneratingKey: Boolean = false,
    onGenerateKey: () -> Unit = {},
    onUseGeneratedKey: () -> Unit = {},
    onClearKey: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Name
        OutlinedTextField(
            value = formData.name,
            onValueChange = { onFormDataChange(formData.copy(name = it)) },
            label = { Text("Connection Name") },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Protocol Selection
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Protocol.values().forEach { protocol ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formData.protocol == protocol,
                            onClick = { onFormDataChange(formData.copy(protocol = protocol)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = protocol.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (protocol) {
                                    Protocol.SFTP -> "SSH File Transfer Protocol"
                                    Protocol.SMB -> "Server Message Block"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // Server Details
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Server Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedTextField(
                    value = formData.hostname,
                    onValueChange = { onFormDataChange(formData.copy(hostname = it)) },
                    label = { Text("Hostname or IP Address") },
                    leadingIcon = {
                        Icon(Icons.Default.Computer, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = formData.port,
                    onValueChange = { onFormDataChange(formData.copy(port = it)) },
                    label = { Text("Port (optional)") },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    placeholder = { Text(formData.protocol.defaultPort.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                // SMB Share Name field (only for SMB protocol)
                if (formData.protocol == Protocol.SMB) {
                    OutlinedTextField(
                        value = formData.shareName,
                        onValueChange = { onFormDataChange(formData.copy(shareName = it)) },
                        label = { Text("Share Name (optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        placeholder = { Text("e.g., share, Users, Public") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Leave empty to auto-discover shares") }
                    )
                }
                
                // Starting Directory field (for SFTP only)
                if (formData.protocol == Protocol.SFTP) {
                    OutlinedTextField(
                        value = formData.startingDirectory,
                        onValueChange = { onFormDataChange(formData.copy(startingDirectory = it)) },
                        label = { Text("Starting Directory (optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        },
                        placeholder = { Text("/path/to/folder") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Directory to open when connecting (defaults to /)") }
                    )
                }
            }
        }
        
        // Credentials
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Credentials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedTextField(
                    value = formData.username,
                    onValueChange = { onFormDataChange(formData.copy(username = it)) },
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = formData.password,
                    onValueChange = { onFormDataChange(formData.copy(password = it)) },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (formData.protocol == Protocol.SFTP) {
                    SshKeySection(
                        formData = formData,
                        hasGeneratedKey = hasGeneratedKey,
                        generatedPublicKey = generatedPublicKey,
                        isGeneratingKey = isGeneratingKey,
                        onGenerateKey = onGenerateKey,
                        onUseGeneratedKey = onUseGeneratedKey,
                        onClearKey = onClearKey
                    )
                }
            }
        }
        
        // Connection Test
        Button(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = formData.isValid() && !isTestingConnection
        ) {
            if (isTestingConnection) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isTestingConnection) "Testing..." else "Test Connection")
        }
        
        testResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("successful", ignoreCase = true)) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (result.contains("successful", ignoreCase = true)) 
                            Icons.Default.CheckCircle 
                        else 
                            Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.contains("successful", ignoreCase = true))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = result,
                        modifier = Modifier.weight(1f),
                        color = if (result.contains("successful", ignoreCase = true))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    IconButton(onClick = onClearTestResult) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = if (result.contains("successful", ignoreCase = true))
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        error?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // Add some bottom padding for the FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SshKeySection(
    formData: ConnectionFormData,
    hasGeneratedKey: Boolean,
    generatedPublicKey: String?,
    isGeneratingKey: Boolean,
    onGenerateKey: () -> Unit,
    onUseGeneratedKey: () -> Unit,
    onClearKey: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showPublicKey by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SSH Key Authentication",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            if (hasGeneratedKey) {
                // Key exists - show status and options
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RSA Key Available",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (formData.useGeneratedKey) "Active" else "Not in use",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!formData.useGeneratedKey) {
                        Button(
                            onClick = onUseGeneratedKey,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use Key")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onClearKey,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Don't Use Key")
                        }
                    }

                    OutlinedButton(
                        onClick = { showPublicKey = !showPublicKey }
                    ) {
                        Icon(
                            imageVector = if (showPublicKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showPublicKey) "Hide" else "Show")
                    }
                }

                // Show public key if requested
                if (showPublicKey && generatedPublicKey != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Public Key (add to server)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedPublicKey))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = generatedPublicKey,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add this to ~/.ssh/authorized_keys on your server",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Regenerate option
                TextButton(
                    onClick = onGenerateKey,
                    enabled = !isGeneratingKey
                ) {
                    Text("Generate New Key")
                }
            } else {
                // No key exists - show generate option
                Text(
                    text = "Generate an RSA key pair for passwordless authentication",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onGenerateKey,
                    enabled = !isGeneratingKey,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGeneratingKey) {
                        WavyCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate RSA Key")
                    }
                }
            }
        }
    }
}

data class ConnectionFormData(
    val name: String = "",
    val protocol: Protocol = Protocol.SFTP,
    val hostname: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val sshKey: String = "",
    val shareName: String = "",
    val startingDirectory: String = "",
    val useGeneratedKey: Boolean = false
) {
    fun isValid(): Boolean {
        return name.isNotBlank() && 
               hostname.isNotBlank() && 
               username.isNotBlank() && 
               (password.isNotBlank() || (protocol == Protocol.SFTP && sshKey.isNotBlank()))
    }
}

@Preview(showBackground = true)
@Composable
private fun AddConnectionScreenPreview() {
    GridTheme {
        AddConnectionScreen(
            onNavigateBack = {},
            onConnectionSaved = {}
        )
    }
}