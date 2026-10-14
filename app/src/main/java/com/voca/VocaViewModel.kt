package com.voca

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.voca.ui.VocaReducer
import com.voca.ui.VocaUiState
import com.voca.voice.VoiceEngine
import com.voca.voice.VoicePrediction

class VocaViewModel(application: Application) : AndroidViewModel(application), VoiceEngine.Listener {
    var state by mutableStateOf(VocaUiState())
        private set
    var testRunning by mutableStateOf(false)
        private set
    var testStatus by mutableStateOf("")
        private set

    private val voice = VoiceEngine(application, this)

    /** Returns true when the Activity needs to handle runtime microphone permission. */
    fun performAction(target: String): Boolean {
        val transition = VocaReducer.action(state, target)
        state = transition.state
        return transition.toggleMicrophone
    }

    fun moveFocus(direction: String) {
        state = VocaReducer.command(state, direction).state
    }

    fun toggleListening() {
        if (voice.isListening) {
            voice.stop()
            state = state.copy(listening = false, microphoneStarting = false, level = 0f,
                notice = "Micrófono desactivado. Puedes seguir usando los controles táctiles.")
        } else {
            state = state.copy(microphoneStarting = true, notice = "Preparando el modelo y el micrófono…")
            voice.start()
        }
    }

    fun microphoneDenied() {
        state = state.copy(listening = false, microphoneStarting = false, level = 0f,
            notice = "Permiso de micrófono denegado. Los controles táctiles siguen disponibles.")
    }

    fun pauseListening() {
        if (voice.isListening) {
            voice.stop()
            state = state.copy(listening = false, microphoneStarting = false, level = 0f,
                notice = "Escucha pausada al salir de la app. Toca el micrófono para activarla.")
        }
    }

    fun runModelTest() {
        if (testRunning) return
        pauseListening()
        testRunning = true
        testStatus = "Comprobando once audios de referencia…"
        voice.runSelfTest { result ->
            testStatus = result
            testRunning = false
        }
    }

    override fun onListeningChanged(listening: Boolean) {
        state = state.copy(listening = listening, microphoneStarting = false,
            level = if (listening) state.level else 0f,
            notice = if (listening) "Te escucho. Di un comando en inglés y deja una pausa." else state.notice)
    }

    override fun onLevel(rms: Float) {
        state = state.copy(level = (rms * 12f).coerceIn(0f, 1f))
    }

    override fun onPrediction(prediction: VoicePrediction) {
        state = state.copy(lastPrediction = prediction.label, lastConfidence = prediction.confidence,
            inferenceMs = prediction.elapsedMs,
            notice = if (!prediction.accepted) "No entendí el comando. Acércate al micrófono y repite." else state.notice)
    }

    override fun onCommand(label: String, confidence: Float) {
        val transition = VocaReducer.command(state, label)
        state = transition.state
        if (transition.toggleMicrophone) toggleListening()
    }

    override fun onError(message: String) {
        state = state.copy(listening = false, microphoneStarting = false, level = 0f,
            notice = "No se pudo activar la voz: $message")
    }

    override fun onCleared() {
        voice.close()
        super.onCleared()
    }
}
