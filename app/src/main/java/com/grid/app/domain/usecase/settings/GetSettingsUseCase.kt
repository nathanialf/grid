package com.grid.app.domain.usecase.settings

import com.grid.app.domain.model.UserSettings
import com.grid.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<UserSettings> {
        return settingsRepository.getSettings()
    }
}