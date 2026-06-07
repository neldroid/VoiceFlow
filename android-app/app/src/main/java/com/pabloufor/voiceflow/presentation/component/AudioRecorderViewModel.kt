package com.pabloufor.voiceflow.presentation.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.usecase.recording.CancelRecordingUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.ObserveRecordingStateUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.ResetRecorderUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.StartRecordingUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.StopRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioRecorderViewModel @Inject constructor(
    observeRecordingState: ObserveRecordingStateUseCase,
    private val startRecording: StartRecordingUseCase,
    private val cancelRecording: CancelRecordingUseCase,
    private val stopRecording: StopRecordingUseCase,
    private val resetRecorder: ResetRecorderUseCase,
) : ViewModel() {

    val state: StateFlow<AudioRecordingState> = observeRecordingState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AudioRecordingState.Idle,
        )

    fun onStartRecording() {
        viewModelScope.launch { startRecording() }
    }

    fun onCancelRecording() {
        viewModelScope.launch { cancelRecording() }
    }

    fun onSendRecording() {
        viewModelScope.launch { stopRecording() }
    }

    fun onResetRecorder() {
        viewModelScope.launch { resetRecorder() }
    }
}
