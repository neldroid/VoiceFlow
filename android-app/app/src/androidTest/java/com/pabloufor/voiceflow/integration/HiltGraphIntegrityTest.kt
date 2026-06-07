package com.pabloufor.voiceflow.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Verifies the full Hilt DI graph compiles correctly with fake modules installed.
 * Catches double-wiring regressions (two @Binds for the same interface).
 *
 * FakeNetworkAndRepositoryModule + FakeAudioModule are installed via
 * @TestInstallIn in FakeModules.kt, replacing all real modules.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltGraphIntegrityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var transcriptionRepository: TranscriptionRepository

    @Inject
    lateinit var audioRecorderRepository: AudioRecorderRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun fullHiltGraphCompilesAndAllRepositoriesAreInjected() {
        assertNotNull("ChatRepository must be injected", chatRepository)
        assertNotNull("TranscriptionRepository must be injected", transcriptionRepository)
        assertNotNull("AudioRecorderRepository must be injected", audioRecorderRepository)
    }
}
