package com.pabloufor.voiceflow.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.pabloufor.voiceflow.data.chat.ChatRepositoryImpl
import com.pabloufor.voiceflow.data.settings.SettingsDataSource
import com.pabloufor.voiceflow.data.settings.SettingsLocalDataSource
import com.pabloufor.voiceflow.data.settings.SettingsRepositoryImpl
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds
    @Singleton
    abstract fun bindSettingsDataSource(impl: SettingsLocalDataSource): SettingsDataSource
}
