package com.voca.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Pure Kotlin frontend: no Android, microphone, ONNX or network dependencies.
 * Matches torchaudio MelSpectrogram(power=2, normalized=false, center=true,
 * pad_mode="reflect", norm=null, mel_scale="htk") and the exported global stats.
 * Output layout is mel-major: out[mel * 101 + frame], NOT frame-major.
 */
class AudioPreprocessor(private val config: AppConfig) {
    private val fftSize = config.nFft
    private val bins = fftSize / 2 + 1
    private val window = DoubleArray(fftSize)
    private val filters = Array(config.nMels) { DoubleArray(bins) }
    private val reverse = IntArray(fftSize)
    private val twiddleReal = DoubleArray(fftSize / 2)
    private val twiddleImag = DoubleArray(fftSize / 2)

    init {
        config.validate()
        // torch.hann_window defaults to periodic=True; its 400 values sit in 512.
        val offset = (fftSize - config.winLength) / 2
        for (i in 0 until config.winLength) {
            window[offset + i] = (0.5 * (1.0 - cos(2.0 * PI * i / config.winLength))).toFloat().toDouble()
        }
        val minMel = 2595.0 * log10(1.0 + config.fMin / 700.0)
        val maxMel = 2595.0 * log10(1.0 + config.fMax / 700.0)
        val points = DoubleArray(config.nMels + 2) { i ->
            val mel = (minMel + (maxMel - minMel) * i / (config.nMels + 1)).toFloat()
            // Torchaudio builds its filterbank with float32 tensors.
            (700f * (10.0.pow((mel / 2595f).toDouble()).toFloat() - 1f)).toDouble()
        }
        for (m in filters.indices) {
            for (k in 0 until bins) {
                val hz = k.toDouble() * config.sampleRate / fftSize
                val ascending = (hz - points[m]) / (points[m + 1] - points[m])
                val descending = (points[m + 2] - hz) / (points[m + 2] - points[m + 1])
                filters[m][k] = max(0.0, min(ascending, descending)).toFloat().toDouble()
            }
        }
        val bits = Integer.numberOfTrailingZeros(fftSize)
        for (i in reverse.indices) reverse[i] = Integer.reverse(i) ushr (32 - bits)
        for (i in twiddleReal.indices) {
            twiddleReal[i] = cos(-2.0 * PI * i / fftSize)
            twiddleImag[i] = sin(-2.0 * PI * i / fftSize)
        }
    }

    /** Pad at the END with zeros or keep the FIRST 16000 samples, as load_wav. */
    fun process(samples: FloatArray): FloatArray = features(samples, normalized = true)

    /** Useful for comparing exported *_logmel.f32 vectors before normalization. */
    fun logMel(samples: FloatArray): FloatArray = features(samples, normalized = false)

    private fun features(samples: FloatArray, normalized: Boolean): FloatArray {
        require(samples.all { it.isFinite() }) { "El audio contiene valores inválidos." }
        val waveform = FloatArray(config.clipLength)
        samples.copyInto(waveform, endIndex = min(samples.size, waveform.size))
        val output = FloatArray(config.nMels * config.frames)
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        val power = DoubleArray(bins)
        for (frame in 0 until config.frames) {
            val start = frame * config.hopLength - fftSize / 2
            for (k in 0 until fftSize) {
                // Reflection excludes the edge itself: -1 -> 1; N -> N-2.
                val index = reflectIndex(start + k, waveform.size)
                real[k] = (waveform[index] * window[k]).toFloat().toDouble()
                imaginary[k] = 0.0
            }
            fft(real, imaginary)
            for (k in 0 until bins) power[k] = (real[k] * real[k] + imaginary[k] * imaginary[k]).toFloat().toDouble()
            for (mel in 0 until config.nMels) {
                var energy = 0.0
                for (k in 0 until bins) energy += power[k] * filters[mel][k]
                val logEnergy = ln(energy.toFloat() + config.epsilon).toFloat()
                output[mel * config.frames + frame] =
                    if (normalized) (logEnergy - config.mean) / config.std else logEnergy
            }
        }
        return output
    }

    private fun reflectIndex(index: Int, length: Int): Int {
        if (length <= 1) return 0
        var reflected = index
        while (reflected < 0 || reflected >= length) {
            reflected = if (reflected < 0) -reflected else 2 * length - 2 - reflected
        }
        return reflected
    }

    /** Forward, unnormalized, radix-2 FFT. Tables are shared, scratch is per call. */
    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        for (i in 0 until fftSize) {
            val j = reverse[i]
            if (j > i) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
            }
        }
        var size = 2
        while (size <= fftSize) {
            val half = size / 2
            val step = fftSize / size
            var base = 0
            while (base < fftSize) {
                for (j in 0 until half) {
                    val index = j * step
                    val other = base + j + half
                    val r = twiddleReal[index] * real[other] - twiddleImag[index] * imaginary[other]
                    val im = twiddleReal[index] * imaginary[other] + twiddleImag[index] * real[other]
                    real[other] = real[base + j] - r
                    imaginary[other] = imaginary[base + j] - im
                    real[base + j] += r
                    imaginary[base + j] += im
                }
                base += size
            }
            size *= 2
        }
    }
}
