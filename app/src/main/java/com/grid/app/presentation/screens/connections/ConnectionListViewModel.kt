package com.grid.app.presentation.screens.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Result
import com.grid.app.domain.usecase.connection.DeleteConnectionUseCase
import com.grid.app.domain.usecase.connection.GetAllConnectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionListViewModel @Inject constructor(
    private val getAllConnectionsUseCase: GetAllConnectionsUseCase,
    private val deleteConnectionUseCase: DeleteConnectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
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
}

data class ConnectionListUiState(
    val connections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val connectionToDelete: String? = null
)