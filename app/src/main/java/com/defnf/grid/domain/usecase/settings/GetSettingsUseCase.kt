package com.defnf.grid.domain.usecase.settings

import com.defnf.grid.domain.model.UserSettings
import com.defnf.grid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<UserSettings> {
        return settingsRepository.getSettings()
    }
}