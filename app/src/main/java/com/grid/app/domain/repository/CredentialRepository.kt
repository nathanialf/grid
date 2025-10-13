package com.grid.app.domain.repository

import com.grid.app.domain.model.Credential
import kotlinx.coroutines.flow.Flow

interface CredentialRepository {
    suspend fun insertCredential(credential: Credential)
    suspend fun updateCredential(credential: Credential)
    suspend fun deleteCredential(credentialId: String)
    suspend fun getCredentialById(credentialId: String): Credential
    suspend fun getAllCredentials(): Flow<List<Credential>>
}