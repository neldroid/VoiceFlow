package com.pabloufor.voiceflow.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    suspend fun setTtsEnabled(enabled: Boolean)

    val ttsEnabled: Flow<Boolean>

}
