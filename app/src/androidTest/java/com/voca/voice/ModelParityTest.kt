package com.voca.voice

import android.content.res.AssetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Android audio frontend and local ONNX model without microphone/UI. */
@RunWith(AndroidJUnit4::class)
class ModelParityTest {
    @Test
    fun notebookVectorsMatchAndroidFrontendAndInference() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val config = AppConfig.load(assets)
        val preprocessor = AudioPreprocessor(config)
        val vectors = assets.open("test_vectors/vectors.json").bufferedReader().use {
            JSONArray(it.readText())
        }
        assertEquals("One notebook reference per output class", config.classes.size, vectors.length())
        val observedClasses = mutableSetOf<String>()
        VoiceClassifier(assets, config).use { classifier ->
            for (index in 0 until vectors.length()) {
                val reference = vectors.getJSONObject(index)
                val label = reference.getString("class")
                assertTrue("Duplicate vector $label", observedClasses.add(label))
                val waveform = readFloats(assets, "test_vectors/${label}_wav.f32")
                assertEquals("$label sample count", config.clipLength, waveform.size)
                val expectedMel = readFloats(assets, "test_vectors/${label}_melnorm.f32")
                val expectedLogMel = readFloats(assets, "test_vectors/${label}_logmel.f32")
                assertEquals("$label tensor size", config.nMels * config.frames, expectedMel.size)
                assertClose("$label normalized log-mel", expectedMel, preprocessor.process(waveform), 1e-3f)
                assertClose("$label log-mel", expectedLogMel, preprocessor.logMel(waveform), 1e-3f)

                val expected = reference.getJSONArray("logits")
                val expectedLogits = FloatArray(expected.length()) { expected.getDouble(it).toFloat() }
                val prediction = classifier.classify(waveform)
                assertClose("$label logits", expectedLogits, prediction.logits, 1e-3f)
                assertEquals("$label predicted class", reference.getString("pred"), prediction.label)
                assertEquals("$label confidence", reference.getDouble("prob").toFloat(), prediction.confidence, 1e-4f)
                if (prediction.label == config.classes[config.unknownIndex]) {
                    assertFalse("Unknown must not execute a command", prediction.accepted)
                }
            }
        }
        assertEquals("All eleven output classes tested", config.classes.toSet(), observedClasses)
    }

    private fun readFloats(assets: AssetManager, name: String): FloatArray {
        val bytes = assets.open(name).use { it.readBytes() }
        assertEquals("$name must contain float32 values", 0, bytes.size % Float.SIZE_BYTES)
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }

    private fun assertClose(name: String, expected: FloatArray, actual: FloatArray, tolerance: Float) {
        assertEquals("$name length", expected.size, actual.size)
        var maximum = 0f
        for (index in expected.indices) {
            assertTrue("$name expected[$index] is finite", expected[index].isFinite())
            assertTrue("$name actual[$index] is finite", actual[index].isFinite())
            maximum = maxOf(maximum, abs(expected[index] - actual[index]))
        }
        assertTrue("$name maximum absolute difference $maximum exceeds $tolerance", maximum <= tolerance)
    }
}
