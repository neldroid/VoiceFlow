package com.pabloufor.voiceflow.presentation.component

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private val SENTENCE_BOUNDARY = Regex("""[.!?\n]+\s*""")

internal class TtsController (context: Context) {
    var isReady by mutableStateOf(false)
        private set

    private var isShutdown = false
    private var activeMessageId: String? = null
    private var spokenUpTo by mutableIntStateOf(0)
    private var pendingChunk = ""

    private val tts = TextToSpeech(context) { status ->
        if (!isShutdown && status == TextToSpeech.SUCCESS) {
            isReady = true
        }
    }

    fun speakChunk(messageId: String, fullText: String) {
        if (!isReady || isShutdown) return

        if (activeMessageId != messageId) {
            activeMessageId = messageId
            spokenUpTo = 0
            pendingChunk = ""
            tts.stop()
        }

        if (fullText.length <= spokenUpTo) return

        pendingChunk += fullText.substring(spokenUpTo)
        spokenUpTo = fullText.length

        val boundary = SENTENCE_BOUNDARY.find(pendingChunk)
        if (boundary != null) {
            val toSpeak = pendingChunk.substring(0, boundary.range.last + 1)
            pendingChunk = pendingChunk.substring(boundary.range.last + 1)
            tts.speak(toSpeak, TextToSpeech.QUEUE_ADD, null, messageId)
        }
    }

    fun flush() {
        if (!isReady || isShutdown || pendingChunk.isBlank()) return
        tts.speak(pendingChunk, TextToSpeech.QUEUE_ADD, null, activeMessageId)
        pendingChunk = ""
    }

    fun stop() {
        pendingChunk = ""
        tts.stop()
    }

    fun shutdown() {
        isShutdown = true
        tts.stop()
        tts.shutdown()
    }
}

@Composable
internal fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) {
        TtsController(context.applicationContext)
    }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}
