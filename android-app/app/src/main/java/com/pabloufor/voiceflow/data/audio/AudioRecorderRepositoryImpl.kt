package com.pabloufor.voiceflow.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.pabloufor.voiceflow.core.concurrency.DispatcherProvider
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioRecorderRepo"

@Singleton
class AudioRecorderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider
) : AudioRecorderRepository {
    private val mutex = Mutex()
    private val _recordingState = MutableStateFlow<AudioRecordingState>(AudioRecordingState.Idle)
    override val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null

    override suspend fun startRecording() = withContext(dispatcher.io) {
        mutex.withLock {
            if (_recordingState.value is AudioRecordingState.Recording) return@withLock

            runCatching {
                val file = createOutputFile()
                currentFilePath = file.absolutePath
                recorder = buildRecorder(file.absolutePath).also { it.start() }
                _recordingState.value = AudioRecordingState.Recording
            }.onFailure { e ->
                handleFailure(e)
            }
        }
    }

    private fun handleFailure(e: Throwable) {
        Log.e(TAG, "Recording operation failed", e)
        releaseResources()
        _recordingState.value = AudioRecordingState.Error(AppError.RecordingFailed)
    }

    private fun releaseResources() {
        recorder?.runCatching { release() }
        recorder = null
        currentFilePath?.let { File(it).delete() }
        currentFilePath = null
    }

    override suspend fun cancelRecording() = withContext(dispatcher.io) {
        mutex.withLock {
            runCatching { recorder?.stop() }
                .onFailure { Log.w(TAG, "cancelRecording: stop() threw", it) }
            runCatching { recorder?.reset() }
                .onFailure { Log.w(TAG, "cancelRecording: reset() threw", it) }
            releaseRecorder()
            currentFilePath?.let { path ->
                runCatching { File(path).delete() }
                    .onFailure { Log.w(TAG, "cancelRecording: delete() threw", it) }
            }
            currentFilePath = null
            _recordingState.value = AudioRecordingState.Idle
        }
    }

    override suspend fun stopRecording() = withContext(dispatcher.io) {
        mutex.withLock {
            val stopResult = runCatching { recorder?.stop() }
                .onFailure { Log.w(TAG, "stopRecording: stop() threw", it) }
            runCatching { recorder?.reset() }
                .onFailure { Log.w(TAG, "stopRecording: reset() threw", it) }
            releaseRecorder()

            val stopError = stopResult.exceptionOrNull()
            val filePath = currentFilePath

            if (stopError != null || filePath == null) {
                if (stopError != null) Log.e(TAG, "stopRecording failed", stopError)
                filePath?.let { path ->
                    runCatching { File(path).delete() }
                        .onFailure { Log.w(TAG, "stopRecording: delete() threw", it) }
                }
                currentFilePath = null
                _recordingState.value = AudioRecordingState.Error(AppError.RecordingFailed)
                return@withContext
            }

            _recordingState.value = AudioRecordingState.Stopped(filePath)
        }
    }

    override suspend fun resetRecorder() = withContext(dispatcher.io) {
        mutex.withLock {
            releaseRecorder()
            currentFilePath = null
            _recordingState.value = AudioRecordingState.Idle
        }
    }

    private fun buildRecorder(outputPath: String): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare()
        }
    }

    private fun createOutputFile(): File {
        val dir = context.cacheDir
        return File.createTempFile("recording_", ".m4a", dir)
    }

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
            .onFailure { Log.w(TAG, "releaseRecorder: release() threw", it) }
        recorder = null
    }
}
