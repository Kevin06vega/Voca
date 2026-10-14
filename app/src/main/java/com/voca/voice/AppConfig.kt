package com.voca.voice

import android.content.res.AssetManager
import org.json.JSONObject

/** The exported notebook contract. Never guess label order or normalization. */
data class AppConfig(
    val modelFile: String,
    val inputName: String,
    val outputName: String,
    val inputShape: LongArray,
    val classes: List<String>,
    val unknownIndex: Int,
    val mean: Float,
    val std: Float,
    val sampleRate: Int,
    val clipLength: Int,
    val nFft: Int,
    val winLength: Int,
    val hopLength: Int,
    val nMels: Int,
    val fMin: Double,
    val fMax: Double,
    val epsilon: Float,
    val confidenceThreshold: Float,
) {
    val frames: Int get() = clipLength / hopLength + 1

    fun validate() {
        require(sampleRate == 16000 && clipLength == 16000) { "El modelo requiere audio mono de 16 kHz y un segundo." }
        require(nFft == 512 && winLength == 400 && hopLength == 160 && nMels == 40) { "Configuración de espectrograma incompatible." }
        require(fMin == 20.0 && fMax == 7600.0 && epsilon > 0f) { "Filtros mel incompatibles." }
        require(mean.isFinite() && std.isFinite() && std > 0f) { "Normalización inválida." }
        require(inputShape.contentEquals(longArrayOf(1, 1, nMels.toLong(), frames.toLong()))) { "La entrada debe ser [1,1,40,101]." }
        require(classes == listOf("yes", "no", "up", "down", "left", "right", "on", "off", "stop", "go", "unknown")) { "Orden de clases incompatible." }
        require(unknownIndex == 10 && classes[unknownIndex] == "unknown")
        require(confidenceThreshold in 0f..1f)
        require(modelFile.isNotBlank() && !modelFile.contains("..") && !modelFile.startsWith("/"))
    }

    companion object {
        fun load(assets: AssetManager, assetName: String = "app_config.json"): AppConfig =
            assets.open(assetName).bufferedReader(Charsets.UTF_8).use { fromJson(it.readText()) }

        fun fromJson(text: String): AppConfig {
            val root = JSONObject(text)
            val audio = root.getJSONObject("audio")
            val mel = root.getJSONObject("logmel")
            require(mel.getBoolean("center") && mel.getString("pad_mode").startsWith("reflect"))
            require(mel.getString("mel_scale") == "htk" && mel.isNull("mel_norm"))
            require(mel.getDouble("power") == 2.0 && mel.getString("window").startsWith("hann"))
            val labels = root.getJSONArray("classes")
            val shape = root.getJSONArray("input_shape")
            return AppConfig(
                modelFile = root.getString("model_file"),
                inputName = root.getString("input_name"),
                outputName = root.getString("output_name"),
                inputShape = LongArray(shape.length()) { shape.getLong(it) },
                classes = List(labels.length()) { labels.getString(it) },
                unknownIndex = root.getInt("unknown_index"),
                mean = root.getDouble("mean").toFloat(),
                std = root.getDouble("std").toFloat(),
                sampleRate = audio.getInt("sample_rate"),
                clipLength = audio.getInt("clip_len"),
                nFft = mel.getInt("n_fft"),
                winLength = mel.getInt("win_length"),
                hopLength = mel.getInt("hop_length"),
                nMels = mel.getInt("n_mels"),
                fMin = mel.getDouble("f_min"),
                fMax = mel.getDouble("f_max"),
                epsilon = mel.getDouble("eps").toFloat(),
                confidenceThreshold = root.getJSONObject("inference").getDouble("softmax_threshold_suggested").toFloat(),
            ).also { it.validate() }
        }
    }
}
