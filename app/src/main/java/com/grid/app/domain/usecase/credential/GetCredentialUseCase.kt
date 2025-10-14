package com.grid.app.domain.usecase.credential

import com.grid.app.domain.model.Credential
import com.grid.app.domain.repository.CredentialRepository
import javax.inject.Inject

class GetCredentialUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository
) {
    suspend operator fun invoke(credentialId: String): Credential {
        return credentialRepository.getCredentialById(credentialId)
    }
}