package com.voca

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.voca.ui.VocaScreen

class MainActivity : ComponentActivity() {
    private lateinit var model: VocaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[VocaViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFA5F0D3), onPrimary = Color(0xFF0B2820),
                background = Color(0xFF0B161C), onBackground = Color(0xFFE8F0F1),
                surface = Color(0xFF182A31), onSurface = Color(0xFFE8F0F1),
                secondary = Color(0xFFA5F0D3)
            )) {
                VocaApplication()
            }
        }
    }

    @Composable
    private fun VocaApplication() {
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) model.toggleListening() else model.microphoneDenied()
        }
        val requestMicrophone: () -> Unit = {
            if (model.state.listening || model.state.microphoneStarting ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                model.toggleListening()
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        val action: (String) -> Unit = { target ->
            if (model.performAction(target)) requestMicrophone()
        }
        BackHandler(enabled = model.state.screen != VocaScreen.WELCOME && model.state.screen != VocaScreen.HOME) {
            action("back")
        }
        VocaApp(
            state = model.state,
            onAction = action,
            testStatus = model.testStatus,
            testRunning = model.testRunning,
            onRunModelTest = model::runModelTest
        )
    }

    override fun onStop() {
        model.pauseListening()
        super.onStop()
    }
}
