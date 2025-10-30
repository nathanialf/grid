package com.defnf.grid.domain.usecase.settings

import com.defnf.grid.domain.model.UserSettings
import com.defnf.grid.domain.repository.SettingsRepository
import javax.inject.Inject

class UpdateSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(settings: UserSettings) {
        settingsRepository.updateSettings(settings)
    }
}