package com.pabloufor.voiceflow.util

import com.pabloufor.voiceflow.di.AudioModule
import com.pabloufor.voiceflow.di.NetworkModule
import com.pabloufor.voiceflow.di.RepositoryModule
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Fake repository implementations for instrumented tests
// ---------------------------------------------------------------------------

class FakeChatRepository : ChatRepository {
    var replyFlow: Flow<Resource<String>> = flowOf()
    override fun streamAssistantReply(history: List<ChatMessage>): Flow<Resource<String>> = replyFlow
}

class FakeTranscriptionRepository : TranscriptionRepository {
    var result: Resource<String> = Resource.Success("fake transcription")
    override suspend fun transcribe(filePath: String): Resource<String> = result
}

class FakeAudioRecorderRepository : AudioRecorderRepository {
    private val _state = MutableStateFlow<AudioRecordingState>(AudioRecordingState.Idle)
    override val recordingState: StateFlow<AudioRecordingState> = _state.asStateFlow()
    fun setState(state: AudioRecordingState) { _state.value = state }
    override suspend fun startRecording() { _state.value = AudioRecordingState.Recording }
    override suspend fun cancelRecording() { _state.value = AudioRecordingState.Idle }
    override suspend fun stopRecording() { _state.value = AudioRecordingState.Stopped("fake/path.m4a") }
    override suspend fun resetRecorder() { _state.value = AudioRecordingState.Idle }
}

// ---------------------------------------------------------------------------
// Test DI modules that replace real modules with fakes
// ---------------------------------------------------------------------------

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class, RepositoryModule::class])
object FakeNetworkAndRepositoryModule {

    @Provides
    @Singleton
    fun provideFakeChatRepository(): FakeChatRepository = FakeChatRepository()

    @Provides
    @Singleton
    fun provideChatRepository(fake: FakeChatRepository): ChatRepository = fake

    @Provides
    @Singleton
    fun provideFakeTranscriptionRepository(): FakeTranscriptionRepository = FakeTranscriptionRepository()

    @Provides
    @Singleton
    fun provideTranscriptionRepository(fake: FakeTranscriptionRepository): TranscriptionRepository = fake
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AudioModule::class])
object FakeAudioModule {

    @Provides
    @Singleton
    fun provideFakeAudioRecorderRepository(): FakeAudioRecorderRepository = FakeAudioRecorderRepository()

    @Provides
    @Singleton
    fun provideAudioRecorderRepository(fake: FakeAudioRecorderRepository): AudioRecorderRepository = fake
}
