package com.pabloufor.voiceflow.di

import com.pabloufor.voiceflow.data.audio.AudioRecorderRepositoryImpl
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioRecorderRepository(
        impl: AudioRecorderRepositoryImpl,
    ): AudioRecorderRepository
}
