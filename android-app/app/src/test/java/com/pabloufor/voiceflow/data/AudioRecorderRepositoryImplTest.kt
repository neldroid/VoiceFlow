package com.pabloufor.voiceflow.data

import android.content.Context
import com.pabloufor.voiceflow.core.concurrency.DefaultDispatcherProvider
import com.pabloufor.voiceflow.data.audio.AudioRecorderRepositoryImpl
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for AudioRecorderRepositoryImpl.
 *
 * MediaRecorder is an Android framework class. Robolectric provides a fake Android
 * runtime — its MediaRecorder shadow records no real audio. We test the repository's
 * state machine and defensive error-handling contracts.
 *
 * JUnit 4 + Robolectric runner used here (JUnit Vintage engine bridges to JUnit Platform).
 *
 * C3 regressions: stop() throwing must not leave the repository in a broken state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AudioRecorderRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: AudioRecorderRepositoryImpl

    private val dispatcher = DefaultDispatcherProvider()
    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = AudioRecorderRepositoryImpl(context, dispatcher)
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val state = repository.recordingState.first()
        assertEquals(AudioRecordingState.Idle, state)
    }

    @Test
    fun `startRecording transitions to Recording or Error state`() = runTest {
        // Robolectric MediaRecorder shadow can't record; we verify state-machine behavior.
        try { repository.startRecording() } catch (_: Exception) {}

        val state = repository.recordingState.first()
        assertTrue(
            "Expected Recording or Error but got: $state",
            state == AudioRecordingState.Recording || state is AudioRecordingState.Error,
        )
    }

    /**
     * C3 regression: stop() on a minimum-duration recording must not throw unchecked.
     * The implementation wraps stop() in runCatching — this test proves that contract.
     */
    @Test
    fun `stop on minimum duration recording does not propagate unhandled exception`() = runTest {
        try { repository.startRecording() } catch (_: Exception) {}

        val thrown = runCatching { repository.stopRecording() }.exceptionOrNull()

        assertTrue("stopRecording must not throw: $thrown", thrown == null)
    }

    /**
     * C3 regression: after stop() throws, resetRecorder() must still reach Idle.
     */
    @Test
    fun `reset always reaches Idle state after stop throws`() = runTest {
        try { repository.startRecording() } catch (_: Exception) {}
        try { repository.stopRecording() } catch (_: Exception) {}

        repository.resetRecorder()

        assertEquals(AudioRecordingState.Idle, repository.recordingState.first())
    }

    @Test
    fun `cancelRecording always transitions state to Idle`() = runTest {
        try { repository.startRecording() } catch (_: Exception) {}

        repository.cancelRecording()

        assertEquals(AudioRecordingState.Idle, repository.recordingState.first())
    }

    @Test
    fun `resetRecorder transitions state to Idle from initial state`() = runTest {
        repository.resetRecorder()

        assertEquals(AudioRecordingState.Idle, repository.recordingState.first())
    }

    @Test
    fun `calling startRecording twice does not crash and ends in Recording or Error`() = runTest {
        try { repository.startRecording() } catch (_: Exception) {}
        try { repository.startRecording() } catch (_: Exception) {}

        val state = repository.recordingState.first()
        assertTrue(
            "Expected Recording or Error but got: $state",
            state == AudioRecordingState.Recording || state is AudioRecordingState.Error,
        )
    }
}

