package com.voca.voice

import android.content.res.AssetManager
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

data class VoicePrediction(
    val label: String,
    val confidence: Float,
    val logits: FloatArray,
    val accepted: Boolean,
    /** End-to-end frontend + model time, excluding microphone capture. */
    val elapsedMs: Long,
)

/** Owns one local CPU ONNX session. Call from a worker, never from the UI thread. */
class VoiceClassifier(assets: AssetManager, val config: AppConfig) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val preprocessor = AudioPreprocessor(config)
    private val session: OrtSession
    private var closed = false

    init {
        config.validate()
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val model = assets.open(config.modelFile).use { it.readBytes() }
            environment.createSession(model, options)
        }
        try {
            val input = session.inputInfo[config.inputName]?.info as? TensorInfo
                ?: error("Entrada ONNX no encontrada: ${config.inputName}")
            val output = session.outputInfo[config.outputName]?.info as? TensorInfo
                ?: error("Salida ONNX no encontrada: ${config.outputName}")
            require(input.type == OnnxJavaType.FLOAT && input.shape.size == 4)
            require(input.shape.drop(1) == config.inputShape.drop(1)) { "Dimensiones ONNX incompatibles." }
            require(output.type == OnnxJavaType.FLOAT && output.shape.size == 2 && output.shape[1] == config.classes.size.toLong())
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    @Synchronized
    fun classify(samples: FloatArray): VoicePrediction {
        check(!closed) { "El clasificador ya está cerrado." }
        val begin = System.nanoTime()
        val features = preprocessor.process(samples)
        val buffer = ByteBuffer.allocateDirect(features.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(features).rewind()
        val logits = OnnxTensor.createTensor(environment, buffer, config.inputShape).use { input ->
            session.run(mapOf(config.inputName to input)).use { result ->
                val tensor = result.get(config.outputName).orElse(null) as? OnnxTensor
                    ?: error("El modelo no devolvió logits.")
                val values = tensor.floatBuffer ?: error("Logits ONNX incompatibles.")
                require(values.remaining() == config.classes.size)
                FloatArray(config.classes.size).also { values.get(it) }
            }
        }
        require(logits.all { it.isFinite() }) { "El modelo devolvió logits inválidos." }
        val best = logits.indices.maxByOrNull { logits[it] } ?: error("Salida vacía.")
        val maximum = logits[best]
        var sum = 0.0
        for (logit in logits) sum += exp((logit - maximum).toDouble())
        val confidence = (1.0 / sum).toFloat()
        return VoicePrediction(
            label = config.classes[best], confidence = confidence, logits = logits,
            accepted = best != config.unknownIndex && confidence >= config.confidenceThreshold,
            elapsedMs = (System.nanoTime() - begin) / 1_000_000,
        )
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            session.close()
        }
        // OrtEnvironment is process-wide and may be used by another classifier.
    }
}
