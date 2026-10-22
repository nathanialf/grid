package com.defnf.grid.presentation.screens.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.usecase.connection.DeleteConnectionUseCase
import com.defnf.grid.domain.usecase.connection.GetAllConnectionsUseCase
import com.defnf.grid.domain.usecase.connection.ReorderConnectionsUseCase
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
import com.defnf.grid.data.local.BiometricManager
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionListViewModel @Inject constructor(
    private val getAllConnectionsUseCase: GetAllConnectionsUseCase,
    private val deleteConnectionUseCase: DeleteConnectionUseCase,
    private val reorderConnectionsUseCase: ReorderConnectionsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    init {
        observeSettingsAndCheckBiometric()
    }
    
    private fun observeSettingsAndCheckBiometric() {
        viewModelScope.launch {
            // Settings share a DataStore with connections, so every connection write re-emits
            // them unchanged; without this each write restarted the load and flashed the spinner
            getSettingsUseCase.invoke().distinctUntilChanged().collect { settings ->
                try {
                    val biometricAvailable = biometricManager.isBiometricAvailable()
                    val requiresBiometric = settings.biometricEnabled && biometricAvailable
                    
                    println("Grid: Biometric enabled in settings: ${settings.biometricEnabled}")
                    println("Grid: Biometric available on device: $biometricAvailable")
                    println("Grid: Requires biometric: $requiresBiometric")
                    
                    val currentlyAuthenticated = _uiState.value.isBiometricAuthenticated
                    
                    // If biometric was just enabled and we weren't previously authenticated, reset auth state
                    val shouldResetAuth = requiresBiometric && (!_uiState.value.requiresBiometric || !currentlyAuthenticated)
                    
                    _uiState.value = _uiState.value.copy(
                        requiresBiometric = requiresBiometric,
                        isBiometricAuthenticated = if (shouldResetAuth) {
                            false
                        } else if (requiresBiometric) {
                            currentlyAuthenticated // Preserve current authentication state
                        } else {
                            true // If biometric not required, consider authenticated
                        }
                    )
                    
                    if (!requiresBiometric) {
                        loadConnections()
                    }
                } catch (e: Exception) {
                    println("Grid: Error checking biometric requirement: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to check biometric settings: ${e.message}"
                    )
                }
            }
        }
    }

    private var connectionsJob: Job? = null

    private fun loadConnections() {
        // Replace the previous collector instead of stacking another one on every reload
        connectionsJob?.cancel()
        connectionsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            getAllConnectionsUseCase()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Unknown error occurred"
                    )
                }
                .collect { connections ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        connections = connections,
                        error = null
                    )
                }
        }
    }

    fun deleteConnection(connectionId: String) {
        // Show confirmation dialog
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = true,
            connectionToDelete = connectionId
        )
    }
    
    fun confirmDeleteConnection() {
        val connectionId = _uiState.value.connectionToDelete ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showDeleteConfirmation = false,
                connectionToDelete = null
            )
            
            when (val result = deleteConnectionUseCase(connectionId)) {
                // The connections flow is live, so it drops the deleted card by itself; reloading
                // here only flashed the loading spinner
                is Result.Success -> Unit
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message ?: "Failed to delete connection"
                    )
                }
                is Result.Loading -> { /* Handle loading state if needed */ }
            }
        }
    }
    
    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = false,
            connectionToDelete = null
        )
    }

    fun refresh() {
        loadConnections()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun reorderConnections(fromIndex: Int, toIndex: Int) {
        val ids = _uiState.value.connections.map { it.id }
        if (fromIndex in ids.indices && toIndex in ids.indices) {
            saveConnectionOrder(ids.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
        }
    }
    
    /** Applies an order chosen by dragging, saving it once rather than after every step. */
    fun saveConnectionOrder(orderedIds: List<String>) {
        val byId = _uiState.value.connections.associateBy { it.id }
        // Ignore an order that no longer matches the list (a connection was added or removed mid-drag)
        if (orderedIds.size != byId.size || orderedIds.toSet() != byId.keys) return

        _uiState.value = _uiState.value.copy(connections = orderedIds.map { byId.getValue(it) })
        viewModelScope.launch {
            reorderConnectionsUseCase(orderedIds)
        }
    }

    private var isAuthenticating = false
    
    fun authenticateWithBiometric(activity: FragmentActivity) {
        if (!_uiState.value.requiresBiometric) {
            println("Grid: Biometric authentication not required")
            return
        }
        
        if (isAuthenticating) {
            println("Grid: Biometric authentication already in progress")
            return
        }
        
        println("Grid: Starting biometric authentication")
        isAuthenticating = true
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(biometricError = null)
            
            try {
                val result = biometricManager.authenticate(activity)
                result.fold(
                    onSuccess = {
                        println("Grid: Biometric authentication successful")
                        _uiState.value = _uiState.value.copy(
                            isBiometricAuthenticated = true,
                            biometricError = null
                        )
                        loadConnections()
                    },
                    onFailure = { exception ->
                        println("Grid: Biometric authentication failed: ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            biometricError = exception.message ?: "Authentication failed"
                        )
                    }
                )
            } catch (e: Exception) {
                println("Grid: Biometric authentication error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    biometricError = e.message ?: "Authentication error"
                )
            } finally {
                isAuthenticating = false
            }
        }
    }
    
    fun clearBiometricError() {
        _uiState.value = _uiState.value.copy(biometricError = null)
    }
}

data class ConnectionListUiState(
    val connections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val connectionToDelete: String? = null,
    val requiresBiometric: Boolean = false,
    val isBiometricAuthenticated: Boolean = false,
    val biometricError: String? = null
)