@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.defnf.grid.presentation.screens.connections

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.defnf.grid.presentation.components.HostKeyChangedDialog
import com.defnf.grid.presentation.components.ShapedIcon
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.presentation.theme.GridTheme
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator

@Composable
fun AddConnectionScreen(
    onNavigateBack: () -> Unit,
    onConnectionSaved: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel()
) {
    ConnectionEditorScreen(
        title = "New connection",
        onNavigateBack = onNavigateBack,
        onConnectionSaved = onConnectionSaved,
        viewModel = viewModel
    )
}

@Composable
fun EditConnectionScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    onConnectionSaved: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel()
) {
    LaunchedEffect(connectionId) {
        viewModel.loadConnection(connectionId)
    }

    ConnectionEditorScreen(
        title = "Edit connection",
        onNavigateBack = onNavigateBack,
        onConnectionSaved = onConnectionSaved,
        viewModel = viewModel
    )
}

/** The add and edit screens differ only in their title and in loading an existing connection. */
@Composable
private fun ConnectionEditorScreen(
    title: String,
    onNavigateBack: () -> Unit,
    onConnectionSaved: () -> Unit,
    viewModel: AddConnectionViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onConnectionSaved()
        }
    }

    uiState.hostKeyChange?.let { change ->
        HostKeyChangedDialog(
            change = change,
            onTrust = viewModel::trustChangedHostKey,
            onDismiss = viewModel::dismissHostKeyChange
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = if (uiState.formData.name.isNotBlank()) {
                    { Text(uiState.formData.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    null
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveConnection() },
                        enabled = uiState.formData.isValid() && !uiState.isSaving,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (uiState.isSaving) {
                            WavyCircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Save")
                    }
                },
                scrollBehavior = scrollBehavior
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
                // The keyboard pushes the form up instead of covering the lower fields
                .consumeWindowInsets(paddingValues)
                .imePadding()
        )
    }
}

@Composable
private fun ConnectionForm(
    formData: ConnectionFormData,
    onFormDataChange: (ConnectionFormData) -> Unit,
    modifier: Modifier = Modifier,
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
    onClearKey: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val fieldShape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormSection(
            title = "Connection",
            icon = Icons.AutoMirrored.Filled.Label,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            OutlinedTextField(
                value = formData.name,
                onValueChange = { onFormDataChange(formData.copy(name = it)) },
                label = { Text("Connection name") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                singleLine = true
            )

            ProtocolPicker(
                selected = formData.protocol,
                onSelect = { onFormDataChange(formData.copy(protocol = it)) }
            )
        }

        FormSection(
            title = "Server",
            icon = Icons.Default.Dns,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            OutlinedTextField(
                value = formData.hostname,
                onValueChange = { onFormDataChange(formData.copy(hostname = it)) },
                label = { Text("Hostname or IP address") },
                leadingIcon = {
                    Icon(Icons.Default.Computer, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                singleLine = true,
                supportingText = supportingTextFor(formData.hostname)
            )

            OutlinedTextField(
                value = formData.port,
                onValueChange = { onFormDataChange(formData.copy(port = it)) },
                label = { Text("Port (optional)") },
                leadingIcon = {
                    Icon(Icons.Default.Tag, contentDescription = null)
                },
                placeholder = { Text(formData.protocol.defaultPort.toString()) },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // SMB Share Name field (only for SMB protocol)
            AnimatedVisibility(visible = formData.protocol == Protocol.SMB) {
                OutlinedTextField(
                    value = formData.shareName,
                    onValueChange = { onFormDataChange(formData.copy(shareName = it)) },
                    label = { Text("Share name (optional)") },
                    leadingIcon = {
                        Icon(Icons.Default.FolderShared, contentDescription = null)
                    },
                    placeholder = { Text("e.g., share, Users, Public") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    singleLine = true,
                    supportingText = supportingTextFor(formData.shareName, "Leave empty to auto-discover shares")
                )
            }

            // Starting Directory field (for SFTP only)
            AnimatedVisibility(visible = formData.protocol == Protocol.SFTP) {
                OutlinedTextField(
                    value = formData.startingDirectory,
                    onValueChange = { onFormDataChange(formData.copy(startingDirectory = it)) },
                    label = { Text("Starting directory (optional)") },
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                    },
                    placeholder = { Text("/path/to/folder") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    singleLine = true,
                    supportingText = supportingTextFor(
                        formData.startingDirectory,
                        "Directory to open when connecting (defaults to /). Browsing stays inside it."
                    )
                )
            }
        }

        FormSection(
            title = "Sign in",
            icon = Icons.Default.Person,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            OutlinedTextField(
                value = formData.username,
                onValueChange = { onFormDataChange(formData.copy(username = it)) },
                label = { Text("Username") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                singleLine = true,
                supportingText = supportingTextFor(formData.username)
            )

            OutlinedTextField(
                value = formData.password,
                onValueChange = { onFormDataChange(formData.copy(password = it)) },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                singleLine = true
            )
        }

        AnimatedVisibility(visible = formData.protocol == Protocol.SFTP) {
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

        // Connection Test
        val testButtonHeight = ButtonDefaults.MediumContainerHeight
        FilledTonalButton(
            onClick = onTestConnection,
            enabled = formData.isValid() && !isTestingConnection,
            shapes = ButtonDefaults.shapesFor(testButtonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(testButtonHeight, hasStartIcon = true),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(min = testButtonHeight)
        ) {
            if (isTestingConnection) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(testButtonHeight)),
                    color = LocalContentColor.current
                )
            } else {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(testButtonHeight))
                )
            }
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(testButtonHeight)))
            Text(
                text = if (isTestingConnection) "Testing…" else "Test connection",
                style = ButtonDefaults.textStyleFor(testButtonHeight)
            )
        }

        StatusBanner(
            message = testResult,
            isError = { !it.contains("successful", ignoreCase = true) },
            onDismiss = onClearTestResult
        )

        StatusBanner(
            message = error,
            isError = { true },
            onDismiss = onClearError
        )
    }
}

/** A titled group of fields on a tonal container, led by an icon on an expressive shape. */
@Composable
private fun FormSection(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    imageVector = icon,
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    containerColor = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier.size(40.dp),
                    iconSize = 22.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            content()
        }
    }
}

/** SFTP / SMB as a connected button group, with the selected protocol spelled out beneath. */
@Composable
private fun ProtocolPicker(
    selected: Protocol,
    onSelect: (Protocol) -> Unit
) {
    val protocols = Protocol.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            protocols.forEachIndexed { index, protocol ->
                ToggleButton(
                    checked = selected == protocol,
                    onCheckedChange = { onSelect(protocol) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        protocols.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    icon = {
                        Icon(
                            imageVector = protocol.icon,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton }
                ) {
                    Text(protocol.name)
                }
            }
        }

        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * A dismissible result banner that grows in when [message] arrives and shrinks away when it
 * clears, keeping the last text on screen while it leaves.
 */
@Composable
private fun StatusBanner(
    message: String?,
    isError: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    // A plain holder, not state: it only needs to outlive the message for the exit animation
    val lastMessage = remember { arrayOfNulls<String>(1) }
    if (message != null) lastMessage[0] = message

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        val shown = lastMessage[0] ?: return@AnimatedVisibility
        val error = isError(shown)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapedIcon(
                    imageVector = if (error) Icons.Default.Error else Icons.Default.CheckCircle,
                    shape = if (error) MaterialShapes.Cookie4Sided.toShape() else MaterialShapes.Sunny.toShape(),
                    containerColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (error) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                    iconSize = 22.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss"
                    )
                }
            }
        }
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showPublicKey by remember { mutableStateOf(false) }

    FormSection(
        title = "SSH key",
        icon = Icons.Default.Key,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        if (hasGeneratedKey) {
            val inUse = formData.useGeneratedKey

            // Key status: the shape fills with the primary color while the key is in use
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    imageVector = Icons.Default.Key,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    containerColor = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (inUse) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                    iconSize = 24.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RSA key available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (inUse) "Used to sign in to this server" else "Not used for this connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleButton(
                    checked = inUse,
                    onCheckedChange = { use -> if (use) onUseGeneratedKey() else onClearKey() },
                    icon = {
                        Icon(
                            imageVector = if (inUse) Icons.Default.Check else Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (inUse) "Using key" else "Use key")
                }

                OutlinedButton(
                    onClick = { showPublicKey = !showPublicKey },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = if (showPublicKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (showPublicKey) "Hide" else "Show")
                }
            }

            // Show public key if requested
            AnimatedVisibility(visible = showPublicKey && generatedPublicKey != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Public key (add to server)",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipData.newPlainText("SSH public key", generatedPublicKey.orEmpty()).toClipEntry()
                                        )
                                    }
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = generatedPublicKey.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add this to ~/.ssh/authorized_keys on your server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Regenerate option
            TextButton(
                onClick = onGenerateKey,
                enabled = !isGeneratingKey,
                shapes = ButtonDefaults.shapes()
            ) {
                if (isGeneratingKey) {
                    WavyCircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                }
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (isGeneratingKey) "Generating…" else "Generate new key")
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
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGeneratingKey) {
                    WavyCircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        color = LocalContentColor.current
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Generating…")
                } else {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Generate RSA key")
                }
            }
        }
    }
}

/** A field's hint, replaced by a non-blocking warning while the value has outer spaces. */
private fun supportingTextFor(value: String, hint: String? = null): (@Composable () -> Unit)? {
    val warning = outerSpaceWarning(value)
    return when {
        warning != null -> {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(warning, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        hint != null -> {
            { Text(hint) }
        }
        else -> null
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