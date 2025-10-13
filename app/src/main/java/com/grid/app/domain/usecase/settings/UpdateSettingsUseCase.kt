package com.grid.app.domain.usecase.settings

import com.grid.app.domain.model.UserSettings
import com.grid.app.domain.repository.SettingsRepository
import javax.inject.Inject

class UpdateSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(settings: UserSettings) {
        settingsRepository.updateSettings(settings)
    }
}