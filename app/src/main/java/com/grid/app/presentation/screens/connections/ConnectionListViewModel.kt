package com.grid.app.presentation.screens.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Result
import com.grid.app.domain.usecase.connection.DeleteConnectionUseCase
import com.grid.app.domain.usecase.connection.GetAllConnectionsUseCase
import com.grid.app.domain.usecase.connection.ReorderConnectionsUseCase
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import com.grid.app.data.local.BiometricManager
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
            getSettingsUseCase.invoke().collect { settings ->
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

    private fun loadConnections() {
        viewModelScope.launch {
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
                is Result.Success -> loadConnections()
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
        val currentConnections = _uiState.value.connections.toMutableList()
        if (fromIndex >= 0 && fromIndex < currentConnections.size && 
            toIndex >= 0 && toIndex < currentConnections.size) {
            
            val connection = currentConnections.removeAt(fromIndex)
            currentConnections.add(toIndex, connection)
            
            // Update UI state immediately for smooth UX
            _uiState.value = _uiState.value.copy(connections = currentConnections)
            
            // Save new order to repository
            viewModelScope.launch {
                val connectionIds = currentConnections.map { it.id }
                reorderConnectionsUseCase(connectionIds)
            }
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