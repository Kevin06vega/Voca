package com.voca

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.Choreographer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real app and ONNX without requesting microphone permission. */
class VocaUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun touchNavigationConfirmationRoutineAndLocalModelWorkWithoutMicrophone() {
        compose.onNodeWithText("Menos toques.\nMás comodidad.").assertIsDisplayed()
        screenshot("01-welcome")
        click("Continuar con controles táctiles")
        compose.onNodeWithText("Mi hogar").assertIsDisplayed()
        screenshot("02-home")

        click("Luces")
        compose.onNodeWithText("Encendidas").assertIsDisplayed()
        scrollClick("off · Apagar")
        compose.onNodeWithText("Apagadas").assertIsDisplayed()
        screenshot("03-device")

        scrollClick("on · Encender")
        scrollClick("Apagar todas las luces")
        compose.onNodeWithText("Confirmar acción").assertIsDisplayed()
        compose.onNodeWithText("Todavía no se ha realizado ningún cambio.").assertIsDisplayed()
        screenshot("04-confirm")
        scrollClick("no · Cancelar")
        compose.onNodeWithText("Encendidas").assertIsDisplayed()

        scrollClick("Apagar todas las luces")
        scrollClick("yes · Sí, apagar")
        compose.onNodeWithText("Apagadas").assertIsDisplayed()

        click("Rutinas")
        scrollClick("go · Iniciar modo estudio")
        compose.onNodeWithText("Modo estudio está activa.").assertExists()
        screenshot("05-routines")
        scrollClick("stop · Detener")
        click("Hogar")
        compose.onNodeWithText("0 dispositivos encendidos").assertIsDisplayed()
        screenshot("06-home-restored")

        click("Ayuda")
        compose.onNodeWithText("10 palabras. Todo el control.").assertIsDisplayed()
        screenshot("07-help")
        scrollClick("Probar modelo con audios")
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("Modelo y audio verificados", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("11/11 predicciones coinciden con el notebook.", substring = true).assertExists()
        compose.onNodeWithText("Modelo y audio verificados", substring = true).performScrollTo()
        screenshot("08-model-check")
        compose.onNodeWithText("Micrófono desactivado").assertIsDisplayed()
    }

    private fun click(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performClick()
    }

    private fun scrollClick(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val framesReady = CountDownLatch(1)
        compose.activity.runOnUiThread {
            val choreographer = Choreographer.getInstance()
            val callback = object : Choreographer.FrameCallback {
                private var remaining = 3
                override fun doFrame(frameTimeNanos: Long) {
                    remaining--
                    if (remaining == 0) framesReady.countDown()
                    else choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
        }
        check(framesReady.await(3, TimeUnit.SECONDS)) { "El render no produjo tres frames para la captura." }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.filesDir, "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        // Keep test-created captures after UTP uninstalls the app at the end of a run.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = compose.activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Voca-verified")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "No se pudo crear la captura de la prueba."
            }
            checkNotNull(resolver.openOutputStream(uri)).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        }
    }
}
