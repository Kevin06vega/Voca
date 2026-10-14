package com.voca.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/** Activity-owned listening engine. Call stop() in onStop and close() in onDestroy.
 * Microphone capture and inference use separate workers; inference never queues
 * old audio. Commands such as "stop" are dispatched to the routine controller
 * and never stop the microphone. Only the listening toggle calls engine.stop().
 */
class VoiceEngine(
    context: Context,
    private val listener: Listener,
    private val minimumRms: Float = 0.004f,
    private val commandCooldownMs: Long = 1000L,
) : AutoCloseable {
    interface Listener {
        fun onListeningChanged(listening: Boolean) {}
        fun onLevel(rms: Float) {}
        /** All scored windows, including unknown/low confidence. */
        fun onPrediction(prediction: VoicePrediction) {}
        /** Accepted commands after two agreeing windows and cooldown. */
        fun onCommand(label: String, confidence: Float) {}
        fun onError(message: String) {}
    }

    private val assets = context.applicationContext.assets
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "Voca-VoiceInference") }
    private val generation = AtomicInteger(0)
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val testing = AtomicBoolean(false)
    @Volatile private var listeningRequested = false
    @Volatile private var recorder: AudioRecorder? = null
    private var classifier: VoiceClassifier? = null
    private var candidate: String? = null
    private var candidateCount = 0
    private var latchedCommand: String? = null
    private var silenceWindows = 0
    private var lastCommandTime = 0L

    val isListening: Boolean get() = listeningRequested && !closed.get()

    /** Stops listening and checks bundled notebook vectors on the same worker.
     * No microphone permission is needed. The report callback is on the main thread.
     */
    @Synchronized
    fun runSelfTest(callback: (String) -> Unit) {
        if (closed.get()) {
            main.post { callback("El motor de voz está cerrado.") }
            return
        }
        if (!testing.compareAndSet(false, true)) {
            main.post { callback("La autoprueba ya está en curso.") }
            return
        }
        stop()
        worker.execute {
            val report = try {
                if (classifier == null) classifier = VoiceClassifier(assets, AppConfig.load(assets))
                SampleTester.run(assets, checkNotNull(classifier))
            } catch (error: Throwable) {
                "No se pudo completar la autoprueba: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                testing.set(false)
            }
            main.post { if (!closed.get()) callback(report) }
        }
    }

    @Synchronized
    fun start() {
        if (closed.get() || listeningRequested) return
        listeningRequested = true
        val token = generation.incrementAndGet()
        worker.execute {
            if (!current(token)) return@execute
            try {
                if (classifier == null) {
                    val config = AppConfig.load(assets)
                    classifier = VoiceClassifier(assets, config)
                }
                candidate = null; candidateCount = 0; latchedCommand = null
                silenceWindows = 0; lastCommandTime = 0L
                val config = checkNotNull(classifier).config
                val capture = AudioRecorder(
                    sampleRate = config.sampleRate, windowSize = config.clipLength,
                    onWindow = { audio -> submit(audio, token) },
                    onLevel = { rms -> deliver(token) { listener.onLevel(rms) } },
                    onError = { error -> fail(token, error) },
                )
                // Starting/stopping quickly cannot leave a newly opened mic alive.
                synchronized(this) {
                    if (!current(token)) return@execute
                    recorder = capture
                    capture.start()
                }
                deliver(token) { listener.onListeningChanged(true) }
            } catch (error: Throwable) {
                fail(token, error)
            }
        }
    }

    private fun submit(audio: FloatArray, token: Int) {
        if (!current(token) || !busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                if (!current(token)) return@execute
                var energy = 0.0
                for (value in audio) energy += value * value
                val rms = sqrt(energy / audio.size).toFloat()
                if (rms < minimumRms) {
                    silenceWindows++
                    candidate = null; candidateCount = 0
                    if (silenceWindows >= 2) latchedCommand = null
                    return@execute
                }
                silenceWindows = 0
                val prediction = checkNotNull(classifier).classify(audio)
                if (!current(token)) return@execute
                deliver(token) { listener.onPrediction(prediction) }
                if (!prediction.accepted) {
                    candidate = null; candidateCount = 0
                    return@execute
                }
                if (candidate == prediction.label) candidateCount++ else {
                    candidate = prediction.label; candidateCount = 1
                }
                val now = SystemClock.elapsedRealtime()
                if (candidateCount >= 2 && latchedCommand != prediction.label &&
                    (lastCommandTime == 0L || now - lastCommandTime >= commandCooldownMs)) {
                    latchedCommand = prediction.label
                    lastCommandTime = now
                    deliver(token) { listener.onCommand(prediction.label, prediction.confidence) }
                }
            } catch (error: Throwable) {
                fail(token, error)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun current(token: Int): Boolean = !closed.get() && listeningRequested && generation.get() == token

    private fun deliver(token: Int, callback: () -> Unit) {
        main.post { if (current(token)) callback() }
    }

    private fun fail(token: Int, error: Throwable) {
        synchronized(this) {
            if (!current(token)) return
            listeningRequested = false
            generation.incrementAndGet()
            recorder?.stop(); recorder = null
        }
        main.post {
            if (!closed.get() && !listeningRequested) {
                listener.onListeningChanged(false)
                listener.onError(error.message ?: "No se pudo reconocer la voz.")
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!listeningRequested && recorder == null) return
        listeningRequested = false
        generation.incrementAndGet()
        recorder?.stop(); recorder = null
        main.post { if (!closed.get() && !listeningRequested) listener.onListeningChanged(false) }
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listeningRequested = false
        generation.incrementAndGet()
        recorder?.stop(); recorder = null
        worker.execute { classifier?.close(); classifier = null }
        worker.shutdown()
    }
}
