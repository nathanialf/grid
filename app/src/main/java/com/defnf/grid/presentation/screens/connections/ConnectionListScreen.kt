@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.defnf.grid.presentation.screens.connections

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.presentation.components.ShapedIcon
import com.defnf.grid.presentation.theme.GridTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ConnectionListScreen(
    onNavigateToAddConnection: () -> Unit,
    onNavigateToEditConnection: (String) -> Unit,
    onNavigateToFileBrowser: (String) -> Unit,
    onNavigateToCachedFiles: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConnectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val isUnlocked = !uiState.requiresBiometric || uiState.isBiometricAuthenticated
    // The FAB keeps its label at the top of the list and shrinks to its icon once scrolled
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val haptics = LocalHapticFeedback.current

    // The order while a card is being dragged; null otherwise, so the saved order shows. It's
    // saved once on drop instead of after every step.
    var dragOrder by remember { mutableStateOf<List<Connection>?>(null) }
    val shownConnections = dragOrder ?: uiState.connections
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = dragOrder ?: uiState.connections
        val fromIndex = current.indexOfFirst { it.id == from.key }
        val toIndex = current.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val onDragStarted: (Offset) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val onDragStopped: () -> Unit = {
        dragOrder?.let { order -> viewModel.saveConnectionOrder(order.map { it.id }) }
        dragOrder = null
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }


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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Expressive flexible app bar: a large title that collapses as the list scrolls
            LargeFlexibleTopAppBar(
                title = { Text("Grid") },
                subtitle = if (isUnlocked && uiState.connections.isNotEmpty()) {
                    {
                        val count = uiState.connections.size
                        Text("$count ${if (count == 1) "connection" else "connections"}")
                    }
                } else {
                    null
                },
                actions = {
                    // Only show settings when not requiring authentication or already authenticated
                    if (isUnlocked) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            // Only show add button when not requiring authentication or already authenticated
            MediumExtendedFloatingActionButton(
                onClick = onNavigateToAddConnection,
                expanded = fabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("Add connection") },
                modifier = Modifier.animateFloatingActionButton(
                    visible = isUnlocked,
                    alignment = Alignment.BottomEnd
                )
            )
        }
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
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                }
            }

            uiState.connections.isEmpty() -> {
                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) +
                        scaleIn(
                            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            initialScale = 0.85f
                        )
                ) {
                    EmptyConnectionsView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 112.dp // Clear the medium FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = shownConnections,
                        key = { _, connection -> connection.id }
                    ) { index, connection ->
                        ReorderableItem(
                            state = reorderState,
                            key = connection.id,
                            // Reordering slides the other cards into place instead of jumping
                            animateItemModifier = Modifier.animateItem(
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            )
                        ) { isDragging ->
                            ConnectionCard(
                                connection = connection,
                                onConnect = { onNavigateToFileBrowser(connection.id) },
                                onEdit = { onNavigateToEditConnection(connection.id) },
                                onViewCachedFiles = { onNavigateToCachedFiles(connection.id) },
                                onDelete = { viewModel.deleteConnection(connection.id) },
                                // Kept for screen readers and anyone who prefers not to drag
                                onMoveUp = if (index > 0) {
                                    { viewModel.reorderConnections(index, index - 1) }
                                } else null,
                                onMoveDown = if (index < shownConnections.size - 1) {
                                    { viewModel.reorderConnections(index, index + 1) }
                                } else null,
                                isDragging = isDragging,
                                canReorder = shownConnections.size > 1,
                                // Drag at once from the handle, or long-press anywhere on the card
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = onDragStarted,
                                    onDragStopped = onDragStopped
                                ),
                                longPressDragModifier = Modifier.longPressDraggableHandle(
                                    enabled = shownConnections.size > 1,
                                    onDragStarted = onDragStarted,
                                    onDragStopped = onDragStopped
                                )
                            )
                        }
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
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete connection?") },
            text = { Text("Are you sure you want to delete \"$connectionName\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteConnection() },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteConfirmation() },
                    shapes = ButtonDefaults.shapes()
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
    // The cookie slowly turns behind a still icon
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStateAnimation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing)
        ),
        label = "cookieRotation"
    )

    // Centred when it fits, scrollable when it doesn't (landscape, large font)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(144.dp),
                contentAlignment = Alignment.Center
            ) {
                val cookie = MaterialShapes.Cookie9Sided.toShape()
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = rotation }
                        .clip(cookie)
                        .background(containerColor)
                )
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No connections yet",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add an SFTP or SMB server to start browsing its files",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The protocol's shape and tone, so SFTP and SMB connections tell apart at a glance. */
@Composable
private fun protocolShape(protocol: Protocol): Shape = when (protocol) {
    Protocol.SFTP -> MaterialShapes.Cookie9Sided.toShape()
    Protocol.SMB -> MaterialShapes.Clover4Leaf.toShape()
}

@Composable
private fun protocolContainerColor(protocol: Protocol): Color = when (protocol) {
    Protocol.SFTP -> MaterialTheme.colorScheme.primaryContainer
    Protocol.SMB -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun protocolContentColor(protocol: Protocol): Color = when (protocol) {
    Protocol.SFTP -> MaterialTheme.colorScheme.onPrimaryContainer
    Protocol.SMB -> MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onViewCachedFiles: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isDragging: Boolean = false,
    canReorder: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    longPressDragModifier: Modifier = Modifier
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Expressive press feedback: the card's corners tighten while held and spring back.
    // Picked up to reorder, it lifts instead: rounder, raised, slightly larger and brighter.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            isDragging -> 40.dp
            pressed -> 16.dp
            else -> 28.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ConnectionCardCorner"
    )
    val lift by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "ConnectionCardLift"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ConnectionCardScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "ConnectionCardColor"
    )

    val location = when (connection.protocol) {
        Protocol.SMB -> connection.shareName?.takeIf { it.isNotBlank() }
        Protocol.SFTP -> connection.startingDirectory?.takeIf { it.isNotBlank() }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(longPressDragModifier),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = lift, pressedElevation = lift),
        onClick = onConnect,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapedIcon(
                    imageVector = connection.protocol.icon,
                    shape = protocolShape(connection.protocol),
                    containerColor = protocolContainerColor(connection.protocol),
                    contentColor = protocolContentColor(connection.protocol),
                    modifier = Modifier.size(56.dp),
                    iconSize = 28.dp
                    // No description: the protocol chip below already names it for screen readers
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${connection.hostname}:${connection.effectivePort}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showDropdownMenu = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    ConnectionMenu(
                        expanded = showDropdownMenu,
                        onDismiss = { showDropdownMenu = false },
                        onEdit = onEdit,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDelete = onDelete
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Protocol badge, the share or starting folder when one is set, and the drag handle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The chips take whatever the handle leaves, so a long folder path keeps its room
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = protocolContainerColor(connection.protocol),
                        contentColor = protocolContentColor(connection.protocol)
                    ) {
                        Text(
                            text = connection.protocol.name,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    location?.let {
                        Surface(
                            modifier = Modifier.weight(1f, fill = false),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (connection.protocol == Protocol.SMB) {
                                        Icons.Default.FolderShared
                                    } else {
                                        Icons.Default.FolderOpen
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (canReorder) {
                    // Hidden from screen readers, which reorder through the menu's Move up/down
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(dragHandleModifier)
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expressive tonal button: a pill at rest that squares off while pressed
            FilledTonalButton(
                onClick = onViewCachedFiles,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text("Offline files")
            }
        }
    }
}

/** The card's overflow menu: an expressive grouped menu, with the destructive action set apart. */
@Composable
private fun ConnectionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val actions = buildList {
        add(Triple("Edit", Icons.Default.Edit, onEdit))
        onMoveUp?.let { add(Triple("Move up", Icons.Default.KeyboardArrowUp, it)) }
        onMoveDown?.let { add(Triple("Move down", Icons.Default.KeyboardArrowDown, it)) }
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
            actions.forEachIndexed { index, (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    shape = MenuDefaults.itemShape(index = index, count = actions.size).shape,
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        action()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
            DropdownMenuItem(
                text = { Text("Delete") },
                shape = MenuDefaults.itemShape(index = 0, count = 1).shape,
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShapedIcon(
                imageVector = Icons.Default.Fingerprint,
                shape = MaterialShapes.Cookie12Sided.toShape(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(128.dp),
                iconSize = 64.dp,
                contentDescription = "Biometric Authentication"
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Grid is locked",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use your fingerprint or face to access your connections",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            val buttonHeight = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = {
                    println("Grid: Manual authenticate button clicked")
                    onAuthenticate()
                },
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                modifier = Modifier.heightIn(min = buttonHeight)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight))
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                Text(
                    text = "Unlock",
                    style = ButtonDefaults.textStyleFor(buttonHeight)
                )
            }

            AnimatedVisibility(visible = biometricError != null) {
                Surface(
                    modifier = Modifier.padding(top = 24.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = biometricError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = onClearError,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear error"
                            )
                        }
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
            onNavigateToCachedFiles = {},
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
            onNavigateToCachedFiles = {},
            onNavigateToSettings = {}
        )
    }
}
