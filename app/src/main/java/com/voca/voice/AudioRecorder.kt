package com.voca.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.max
import kotlin.math.sqrt

/** Captures bounded one-second windows every 250 ms on a dedicated thread.
 * RECORD_AUDIO permission must already have been granted by the Activity.
 * The consumer must return quickly; it should enqueue inference, not block capture.
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val windowSize: Int = 16000,
    private val hopSize: Int = 4000,
    private val onWindow: (FloatArray) -> Unit,
    private val onLevel: (Float) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) : AutoCloseable {
    @Volatile private var active: AudioRecord? = null
    val isRecording: Boolean get() = active != null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (active != null) return
        require(windowSize > 0 && hopSize > 0 && sampleRate == 16000)
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "El micrófono no admite audio mono de 16 kHz." }
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(minimum * 2, 6400),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("No se pudo abrir el micrófono.")
        }
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "El micrófono no comenzó a grabar." }
        } catch (error: Throwable) {
            record.release()
            throw error
        }
        active = record
        try {
            Thread({ capture(record) }, "Voca-AudioCapture").start()
        } catch (error: Throwable) {
            active = null
            runCatching { record.stop() }
            record.release()
            throw error
        }
    }

    private fun capture(record: AudioRecord) {
        val shorts = ShortArray(1600)
        val ring = FloatArray(windowSize)
        var writeIndex = 0
        var filled = 0
        var sinceEmission = 0
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (active === record) {
                val count = record.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                if (active !== record) break
                if (count < 0) error("Error de lectura del micrófono ($count).")
                if (count == 0) continue
                var energy = 0.0
                for (i in 0 until count) {
                    val value = shorts[i] / 32768f
                    energy += value * value
                    ring[writeIndex] = value
                    writeIndex = (writeIndex + 1) % windowSize
                    if (filled < windowSize) filled++
                    sinceEmission++
                    if (filled == windowSize && sinceEmission >= hopSize) {
                        val window = FloatArray(windowSize)
                        for (j in window.indices) window[j] = ring[(writeIndex + j) % windowSize]
                        sinceEmission = 0
                        onWindow(window)
                    }
                }
                onLevel(sqrt(energy / count).toFloat())
            }
        } catch (error: Throwable) {
            if (active === record) onError(error)
        } finally {
            synchronized(this) { if (active === record) active = null }
            runCatching { record.stop() }
            record.release()
        }
    }

    @Synchronized
    fun stop() {
        val record = active ?: return
        active = null
        // Unblock READ_BLOCKING; release belongs to its capture thread's finally.
        runCatching { record.stop() }
    }

    override fun close() = stop()
}
