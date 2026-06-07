package com.pabloufor.voiceflow.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SettingsDataSource {
    fun getTtsEnabled(): Flow<Boolean>
    suspend fun setTtsEnabled(enabled: Boolean)
}

private val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")

class SettingsLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsDataSource {

    override fun getTtsEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TTS_ENABLED] ?: true
    }

    override suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_TTS_ENABLED] = enabled }
    }
}
