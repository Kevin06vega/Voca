package com.voca.voice

import android.content.res.AssetManager
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs

/** Local diagnostic with audio/features exported by the original notebook.
 * It never performs home actions and needs no microphone permission.
 */
object SampleTester {
    fun run(assets: AssetManager, classifier: VoiceClassifier): String {
        val expected = assets.open("test_vectors/vectors.json").bufferedReader().use { JSONArray(it.readText()) }
        val frontend = AudioPreprocessor(classifier.config)
        var correct = 0
        var maxFeatureError = 0.0f
        var maxLogitError = 0.0f
        val lines = mutableListOf<String>()
        for (i in 0 until expected.length()) {
            val sample = expected.getJSONObject(i)
            val label = sample.getString("class")
            val wav = readFloats(assets, "test_vectors/${label}_wav.f32")
            val expectedFeatures = readFloats(assets, "test_vectors/${label}_melnorm.f32")
            val features = frontend.process(wav)
            require(features.size == expectedFeatures.size) { "Vector de prueba incompatible: $label" }
            for (j in features.indices) maxFeatureError = maxOf(maxFeatureError, abs(features[j] - expectedFeatures[j]))
            val prediction = classifier.classify(wav)
            val referenceLogits = sample.getJSONArray("logits")
            require(referenceLogits.length() == prediction.logits.size)
            for (j in prediction.logits.indices) {
                maxLogitError = maxOf(maxLogitError, abs(prediction.logits[j] - referenceLogits.getDouble(j).toFloat()))
            }
            val matches = prediction.label == sample.getString("pred")
            if (matches) correct++
            lines += "${if (matches) "✓" else "✗"} $label → ${prediction.label} (${String.format(Locale.ROOT, "%.1f", prediction.confidence * 100)} %)"
        }
        val parityOk = correct == expected.length() && maxFeatureError <= 1e-3f && maxLogitError <= 1e-3f
        return buildString {
            appendLine(if (parityOk) "Modelo y audio verificados" else "Revisar diferencias del modelo")
            appendLine("$correct/${expected.length()} predicciones coinciden con el notebook.")
            appendLine("Error máximo espectrograma: ${String.format(Locale.ROOT, "%.2e", maxFeatureError)}")
            appendLine("Error máximo logits: ${String.format(Locale.ROOT, "%.2e", maxLogitError)}")
            appendLine()
            append(lines.joinToString("\n"))
            appendLine()
            append("Esta prueba usa audios guardados. La voz en vivo se comprueba en un teléfono.")
        }
    }

    private fun readFloats(assets: AssetManager, path: String): FloatArray {
        val bytes = assets.open(path).use { it.readBytes() }
        require(bytes.size % Float.SIZE_BYTES == 0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}
