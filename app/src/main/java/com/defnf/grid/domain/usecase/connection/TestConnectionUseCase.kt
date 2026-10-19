package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.HostKeyChangedException
import com.defnf.grid.domain.model.findHostKeyChange
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class TestConnectionUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, credential: Credential): ConnectionTestResult {
        return try {
            val result = fileRepository.testConnection(connection, credential)
            if (result.isSuccess) {
                ConnectionTestResult(isSuccess = true, error = null)
            } else {
                val exception = result.exceptionOrNull()
                ConnectionTestResult(
                    isSuccess = false,
                    error = exception?.message,
                    hostKeyChange = exception?.findHostKeyChange()
                )
            }
        } catch (exception: Exception) {
            ConnectionTestResult(isSuccess = false, error = exception.message)
        }
    }
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val error: String?,
    // Set when an SFTP server's host key differs from the trusted one
    val hostKeyChange: HostKeyChangedException? = null
)