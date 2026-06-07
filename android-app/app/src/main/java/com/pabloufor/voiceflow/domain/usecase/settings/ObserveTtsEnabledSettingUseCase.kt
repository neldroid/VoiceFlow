package com.pabloufor.voiceflow.domain.usecase.settings

import com.pabloufor.voiceflow.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTtsEnabledSettingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        settingsRepository.ttsEnabled
}