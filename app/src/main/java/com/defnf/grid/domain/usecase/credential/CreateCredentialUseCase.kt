package com.defnf.grid.domain.usecase.credential

import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.repository.CredentialRepository
import javax.inject.Inject

class CreateCredentialUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository
) {
    suspend operator fun invoke(credential: Credential) {
        credentialRepository.insertCredential(credential)
    }
}