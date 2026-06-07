package com.pabloufor.voiceflow.data.settings

import com.pabloufor.voiceflow.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: SettingsDataSource,
): SettingsRepository {
    override val ttsEnabled: Flow<Boolean> = dataSource.getTtsEnabled()

    override suspend fun setTtsEnabled(enabled: Boolean) {
        dataSource.setTtsEnabled(enabled)
    }
}
