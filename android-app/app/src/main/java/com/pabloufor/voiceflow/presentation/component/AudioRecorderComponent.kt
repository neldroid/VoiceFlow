package com.pabloufor.voiceflow.presentation.component

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pabloufor.voiceflow.R
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.presentation.theme.VoiceFlowTheme

@Composable
private fun AudioRecorderComponent(
    state: AudioRecordingState,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecordingIndicator(isRecording = state is AudioRecordingState.Recording)

        Spacer(modifier = Modifier.height(16.dp))

        RecordingStatusLabel(state = state)

        Spacer(modifier = Modifier.height(24.dp))

        RecordingControls(
            state = state,
            onStartRecording = onStartRecording,
            onCancelRecording = onCancelRecording,
            onSendRecording = onSendRecording,
        )
    }
}

@Composable
private fun RecordingIndicator(isRecording: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (isRecording) {
            val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseScale",
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = if (isRecording) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.mic),
                contentDescription = null,
                tint = if (isRecording) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun RecordingStatusLabel(state: AudioRecordingState) {
    val label = when (state) {
        is AudioRecordingState.Idle -> stringResource(R.string.recorder_idle)
        is AudioRecordingState.Recording -> stringResource(R.string.recorder_recording)
        is AudioRecordingState.Stopped -> stringResource(R.string.recorder_stopped)
        is AudioRecordingState.Error -> appErrorText(error = state.error)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun RecordingControls(
    state: AudioRecordingState,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is AudioRecordingState.Idle,
            is AudioRecordingState.Stopped,
            is AudioRecordingState.Error -> {
                RecorderIconButton(
                    onClick = onStartRecording,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mic),
                        contentDescription = stringResource(R.string.recorder_cd_start),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            is AudioRecordingState.Recording -> {
                RecorderIconButton(
                    onClick = onCancelRecording,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.recorder_cd_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RecorderIconButton(
                    onClick = onSendRecording,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = stringResource(R.string.recorder_cd_send),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecorderIconButton(
    onClick: () -> Unit,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = containerColor),
    ) {
        content()
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun AudioRecorderIdlePreview() {
    VoiceFlowTheme {
        AudioRecorderComponent(
            state = AudioRecordingState.Idle,
            onStartRecording = {},
            onCancelRecording = {},
            onSendRecording = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioRecorderRecordingPreview() {
    VoiceFlowTheme {
        AudioRecorderComponent(
            state = AudioRecordingState.Recording,
            onStartRecording = {},
            onCancelRecording = {},
            onSendRecording = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioRecorderStoppedPreview() {
    VoiceFlowTheme {
        AudioRecorderComponent(
            state = AudioRecordingState.Stopped(filePath = "/data/audio.mp4"),
            onStartRecording = {},
            onCancelRecording = {},
            onSendRecording = {},
        )
    }
}

// endregion

/**
 * Stateful entry point. Owns the ViewModel and the RECORD_AUDIO runtime permission.
 * [onAudioSent] is called once with the file path when the user taps Send.
 * [enabled] blocks all interactions while transcription or assistant reply is in progress.
 */
@Composable
fun AudioRecorder(
    onAudioSent: (filePath: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    viewModel: AudioRecorderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isPermissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            isPermissionDenied = !granted
            if (granted) viewModel.onStartRecording()
        },
    )

    LaunchedEffect(state) {
        if (state is AudioRecordingState.Stopped) {
            onAudioSent((state as AudioRecordingState.Stopped).filePath)
            viewModel.onResetRecorder()
        }
    }

    if (isPermissionDenied) {
        PermissionDeniedFallback(
            onRetry = {
                // Check if we should show rationale or if they need to go to settings
                val activity = context as? Activity
                val showRationale = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
                } ?: false

                if (showRationale) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    // Open App Settings as a last resort
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            },
            modifier = modifier
        )
    } else {
        AudioRecorderComponent(
            state = state,
            onStartRecording = {
                if (enabled) {
                    val permissionCheck = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    )
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        viewModel.onStartRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onCancelRecording = { if (enabled) viewModel.onCancelRecording() },
            onSendRecording = { if (enabled) viewModel.onSendRecording() },
            modifier = modifier,
        )
    }
}

@Composable
private fun PermissionDeniedFallback(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(16.dp)
    ) {
        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = stringResource(R.string.recorder_permission_required_message),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.recorder_permission_required_cta))
        }
    }
}