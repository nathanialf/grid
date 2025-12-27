package com.defnf.grid.presentation.screens.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.usecase.connection.CreateConnectionUseCase
import com.defnf.grid.domain.usecase.connection.GetConnectionUseCase
import com.defnf.grid.domain.usecase.connection.TestConnectionUseCase
import com.defnf.grid.domain.usecase.connection.UpdateConnectionUseCase
import com.defnf.grid.domain.usecase.credential.CreateCredentialUseCase
import com.defnf.grid.domain.usecase.credential.GetCredentialUseCase
import com.defnf.grid.domain.usecase.credential.UpdateCredentialUseCase
import com.defnf.grid.data.local.SshKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddConnectionViewModel @Inject constructor(
    private val createConnectionUseCase: CreateConnectionUseCase,
    private val updateConnectionUseCase: UpdateConnectionUseCase,
    private val getConnectionUseCase: GetConnectionUseCase,
    private val createCredentialUseCase: CreateCredentialUseCase,
    private val updateCredentialUseCase: UpdateCredentialUseCase,
    private val getCredentialUseCase: GetCredentialUseCase,
    private val testConnectionUseCase: TestConnectionUseCase,
    private val sshKeyManager: SshKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConnectionUiState())
    val uiState: StateFlow<AddConnectionUiState> = _uiState.asStateFlow()

    companion object {
        const val DEFAULT_KEY_NAME = "default"
    }

    init {
        // Check if we already have a generated key
        checkExistingKey()
    }

    private fun checkExistingKey() {
        val hasKey = sshKeyManager.hasKeyPair(DEFAULT_KEY_NAME)
        val publicKey = if (hasKey) sshKeyManager.getPublicKey(DEFAULT_KEY_NAME) else null
        _uiState.value = _uiState.value.copy(
            hasGeneratedKey = hasKey,
            generatedPublicKey = publicKey
        )
    }

    fun generateSshKey() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingKey = true)
            try {
                val publicKey = sshKeyManager.generateKeyPair(DEFAULT_KEY_NAME, 4096)
                val privateKey = sshKeyManager.getPrivateKey(DEFAULT_KEY_NAME)

                _uiState.value = _uiState.value.copy(
                    isGeneratingKey = false,
                    hasGeneratedKey = true,
                    generatedPublicKey = publicKey,
                    formData = _uiState.value.formData.copy(
                        sshKey = privateKey ?: "",
                        useGeneratedKey = true
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingKey = false,
                    error = "Failed to generate SSH key: ${e.message}"
                )
            }
        }
    }

    fun useGeneratedKey() {
        val privateKey = sshKeyManager.getPrivateKey(DEFAULT_KEY_NAME)
        if (privateKey != null) {
            _uiState.value = _uiState.value.copy(
                formData = _uiState.value.formData.copy(
                    sshKey = privateKey,
                    useGeneratedKey = true
                )
            )
        }
    }

    fun clearSshKey() {
        _uiState.value = _uiState.value.copy(
            formData = _uiState.value.formData.copy(
                sshKey = "",
                useGeneratedKey = false
            )
        )
    }

    fun loadConnection(connectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val connection = getConnectionUseCase(connectionId)
                val credential = getCredentialUseCase(connection.credentialId)

                // Check if the stored private key matches the generated key
                val generatedPrivateKey = sshKeyManager.getPrivateKey(DEFAULT_KEY_NAME)
                val isUsingGeneratedKey = !credential.privateKey.isNullOrEmpty() &&
                    credential.privateKey == generatedPrivateKey

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    formData = ConnectionFormData(
                        name = connection.name,
                        protocol = connection.protocol,
                        hostname = connection.hostname,
                        port = if (connection.port != connection.protocol.defaultPort) connection.port.toString() else "",
                        username = credential.username,
                        password = credential.password,
                        sshKey = credential.privateKey ?: "",
                        shareName = connection.shareName ?: "",
                        startingDirectory = connection.startingDirectory ?: "",
                        useGeneratedKey = isUsingGeneratedKey
                    ),
                    isEditMode = true,
                    connectionId = connectionId,
                    credentialId = connection.credentialId
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Failed to load connection"
                )
            }
        }
    }

    fun updateFormData(formData: ConnectionFormData) {
        _uiState.value = _uiState.value.copy(
            formData = formData,
            error = null
        )
    }

    fun saveConnection() {
        val state = _uiState.value
        if (!state.formData.isValid()) {
            _uiState.value = state.copy(error = "Please fill in all required fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)

            try {
                val credentialId = state.credentialId ?: UUID.randomUUID().toString()
                val credential = Credential(
                    id = credentialId,
                    username = state.formData.username,
                    password = state.formData.password,
                    privateKey = if (state.formData.sshKey.isNotBlank()) state.formData.sshKey else null
                )

                if (state.isEditMode && state.connectionId != null) {
                    updateCredentialUseCase(credential)
                    
                    val connection = Connection(
                        id = state.connectionId,
                        name = state.formData.name,
                        hostname = state.formData.hostname,
                        protocol = state.formData.protocol,
                        port = state.formData.port.toIntOrNull() ?: state.formData.protocol.defaultPort,
                        credentialId = credentialId,
                        shareName = if (state.formData.shareName.isNotBlank()) state.formData.shareName else null,
                        startingDirectory = if (state.formData.startingDirectory.isNotBlank()) state.formData.startingDirectory else null
                    )
                    updateConnectionUseCase(connection)
                } else {
                    createCredentialUseCase(credential)
                    
                    val connection = Connection(
                        id = UUID.randomUUID().toString(),
                        name = state.formData.name,
                        hostname = state.formData.hostname,
                        protocol = state.formData.protocol,
                        port = state.formData.port.toIntOrNull() ?: state.formData.protocol.defaultPort,
                        credentialId = credentialId,
                        shareName = if (state.formData.shareName.isNotBlank()) state.formData.shareName else null,
                        startingDirectory = if (state.formData.startingDirectory.isNotBlank()) state.formData.startingDirectory else null
                    )
                    createConnectionUseCase(connection)
                }

                _uiState.value = state.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (exception: Exception) {
                _uiState.value = state.copy(
                    isSaving = false,
                    error = exception.message ?: "Failed to save connection"
                )
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (!state.formData.isValid()) {
            _uiState.value = state.copy(error = "Please fill in all required fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isTesting = true, testResult = null)

            try {
                val credential = Credential(
                    id = "temp",
                    username = state.formData.username,
                    password = state.formData.password,
                    privateKey = if (state.formData.sshKey.isNotBlank()) state.formData.sshKey else null
                )

                val connection = Connection(
                    id = "temp",
                    name = state.formData.name,
                    hostname = state.formData.hostname,
                    protocol = state.formData.protocol,
                    port = state.formData.port.toIntOrNull() ?: state.formData.protocol.defaultPort,
                    credentialId = "temp",
                    shareName = if (state.formData.shareName.isNotBlank()) state.formData.shareName else null,
                    startingDirectory = if (state.formData.startingDirectory.isNotBlank()) state.formData.startingDirectory else null
                )

                val result = testConnectionUseCase(connection, credential)
                
                _uiState.value = state.copy(
                    isTesting = false,
                    testResult = if (result.isSuccess) "Connection successful!" else "Connection failed: ${result.error}"
                )
            } catch (exception: Exception) {
                _uiState.value = state.copy(
                    isTesting = false,
                    testResult = "Connection failed: ${exception.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }
}

data class AddConnectionUiState(
    val formData: ConnectionFormData = ConnectionFormData(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isSaved: Boolean = false,
    val isEditMode: Boolean = false,
    val connectionId: String? = null,
    val credentialId: String? = null,
    val error: String? = null,
    val testResult: String? = null,
    val isGeneratingKey: Boolean = false,
    val hasGeneratedKey: Boolean = false,
    val generatedPublicKey: String? = null
)