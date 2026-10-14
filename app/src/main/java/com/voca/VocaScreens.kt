package com.voca

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voca.ui.HomeDevice
import com.voca.ui.VocaReducer
import com.voca.ui.VocaScreen
import com.voca.ui.VocaUiState
import kotlin.math.roundToInt

private val Background = Color(0xFF0B161C)
private val CardColor = Color(0xFF182A31)
private val ControlColor = Color(0xFF20343D)
private val Emerald = Color(0xFF123F33)
private val Mint = Color(0xFFA5F0D3)
private val White = Color(0xFFE8F0F1)
private val Muted = Color(0xFF9FB1B9)
private val Amber = Color(0xFFFFC778)
private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun VocaApp(
    state: VocaUiState,
    onAction: (String) -> Unit,
    testStatus: String,
    testRunning: Boolean,
    onRunModelTest: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxHeight(),
            containerColor = Background,
            bottomBar = {
                if (state.screen != VocaScreen.WELCOME) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
                        MicrophonePanel(state, onAction)
                        if (state.screen != VocaScreen.CONFIRM) NavigationBar(state, onAction)
                        else Spacer(Modifier.height(16.dp))
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                if (state.screen == VocaScreen.WELCOME) {
                    Welcome(state, onAction)
                } else {
                    ScreenHeader(state, onAction)
                    key(state.screen) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                            when (state.screen) {
                                VocaScreen.HOME -> Home(state, onAction)
                                VocaScreen.DEVICE -> Device(state, onAction)
                                VocaScreen.ROUTINES -> Routines(state, onAction)
                                VocaScreen.CONFIRM -> Confirmation(state, onAction)
                                VocaScreen.HELP -> Help(state, testStatus, testRunning, onRunModelTest)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Welcome(state: VocaUiState, onAction: (String) -> Unit) {
    Row(Modifier.padding(top = 18.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        VocaIcon("mic", Mint, 26.dp)
        Spacer(Modifier.width(10.dp))
        Text("voca", color = White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text("TU HOGAR, A TU VOZ", color = Mint, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(18.dp))
        Surface(color = Emerald, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(vertical = 42.dp), contentAlignment = Alignment.Center) {
                VocaIcon("home", Mint, 88.dp)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("Menos toques.\nMás comodidad.", color = White, fontSize = 34.sp,
            lineHeight = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Navega, controla tus dispositivos y activa rutinas con una sola palabra.",
            color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VocaIcon("shield", Mint, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text("Sin internet. Tu voz se queda contigo.", color = Mint, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Los dispositivos de esta app son simulados.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
    VocaButton("Activar micrófono", "mic", state, onAction, primary = true)
    Spacer(Modifier.height(8.dp))
    VocaButton("Continuar con controles táctiles", "continue", state, onAction)
    Text("Te pediremos permiso para escuchar mientras usas la app. También puedes usar los controles.",
        color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun ScreenHeader(state: VocaUiState, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.screen != VocaScreen.HOME) {
            IconButton(onClick = { onAction("back") }, modifier = Modifier.size(48.dp)) {
                VocaIcon("back", White, 24.dp, "Volver al hogar o cancelar")
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(when (state.screen) {
            VocaScreen.HOME -> "Mi hogar"
            VocaScreen.DEVICE -> state.device.title
            VocaScreen.ROUTINES -> "Tus rutinas"
            VocaScreen.CONFIRM -> "Confirmar acción"
            VocaScreen.HELP -> "Guía de voz"
            else -> "Voca"
        }, color = White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Home(state: VocaUiState, onAction: (String) -> Unit) {
    Text("Todo bajo control, a tu manera.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    Surface(color = Emerald, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            VocaIcon("home", Mint, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("${state.onCount} ${if (state.onCount == 1) "dispositivo encendido" else "dispositivos encendidos"}",
                color = Mint, fontSize = 12.sp)
        }
    }
    Row(Modifier.padding(top = 28.dp, bottom = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dispositivos", color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("SIMULADOS", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DeviceCard("Luces", "Sala · ${if (state.lights) "Encendidas" else "Apagadas"}", "bulb", "device:lights", state, onAction, Modifier.weight(1f))
        DeviceCard("Ventilador", "Sala · ${if (state.fan) "Encendido" else "Apagado"}", "fan", "device:fan", state, onAction, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DeviceCard("Televisión", "Sala · ${if (state.television) "Encendida" else "Apagada"}", "tv", "device:tv", state, onAction, Modifier.weight(1f))
        DeviceCard("Rutinas", "3 escenas listas", "play", "routines", state, onAction, Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    Text("El borde verde indica la selección. Di yes para abrir.", color = Mint, fontSize = 12.sp, lineHeight = 19.sp)
}

@Composable
private fun DeviceCard(title: String, detail: String, icon: String, target: String,
    state: VocaUiState, onAction: (String) -> Unit, modifier: Modifier) {
    FocusSurface(target, state, onAction, modifier.heightIn(min = 138.dp)) {
        Column(Modifier.padding(16.dp)) {
            VocaIcon(icon, Mint, 28.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Device(state: VocaUiState, onAction: (String) -> Unit) {
    val on = state.isOn(state.device)
    Text("Dispositivo simulado · Sala", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))
    Surface(color = if (on) Emerald else CardColor, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VocaIcon(state.device.icon, if (on) Mint else Muted, 78.dp)
            Spacer(Modifier.height(20.dp))
            Text(when (state.device) {
                HomeDevice.LIGHTS -> if (on) "Encendidas" else "Apagadas"
                HomeDevice.FAN -> if (on) "Encendido" else "Apagado"
                HomeDevice.TELEVISION -> if (on) "Encendida" else "Apagada"
            }, color = Mint, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text(if (on) "Di off para apagar" else "Di on para encender", color = Muted, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    VocaButton("on · Encender", "on", state, onAction)
    Spacer(Modifier.height(10.dp))
    VocaButton("off · Apagar", "off", state, onAction)
    Text("Los comandos on y off actúan sobre este dispositivo.", color = Muted, fontSize = 12.sp,
        lineHeight = 19.sp, modifier = Modifier.padding(vertical = 18.dp))
    if (state.device == HomeDevice.LIGHTS) {
        VocaButton("Apagar todas las luces", "allOff", state, onAction)
        Text("Esta acción necesita confirmación.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Routines(state: VocaUiState, onAction: (String) -> Unit) {
    Text(if (state.activeRoutine != null) "${VocaReducer.routineNames[state.activeRoutine]} está activa." else "Una palabra cambia el ambiente.",
        color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    val descriptions = listOf("Luz encendida y ventilador activo.", "Apaga las luces y la televisión.", "Enciende las luces de la sala.")
    val icons = listOf("bulb", "moon", "home")
    for (index in 0..2) {
        FocusSurface("routine:$index", state, onAction, Modifier.fillMaxWidth(),
            color = if (state.selectedRoutine == index) ControlColor else CardColor) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                VocaIcon(icons[index], Mint, 28.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(VocaReducer.routineNames[index], color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(descriptions[index], color = Muted, fontSize = 12.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 5.dp))
                    Text(when {
                        state.activeRoutine == index -> "ACTIVA"
                        state.selectedRoutine == index -> "SELECCIONADA · LISTA"
                        else -> "LISTA"
                    }, color = Mint, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    VocaButton("go · Iniciar ${VocaReducer.routineNames[state.selectedRoutine].lowercase()}", "go", state, onAction, primary = true)
    Spacer(Modifier.height(10.dp))
    VocaButton("stop · Detener", "stop", state, onAction)
    Text("Al detener la rutina, se restaura el estado que tenían los dispositivos antes de iniciarla.",
        color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun Confirmation(state: VocaUiState, onAction: (String) -> Unit) {
    Text("Revisa antes de continuar.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    Surface(color = CardColor, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            VocaIcon("power", Amber, 42.dp)
            Spacer(Modifier.height(22.dp))
            Text("¿Apagar todas\nlas luces?", color = White, fontSize = 29.sp, lineHeight = 37.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Text("Se apagará el grupo de luces del hogar simulado.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(24.dp))
            Text("Todavía no se ha realizado ningún cambio.", color = Amber, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    VocaButton("yes · Sí, apagar", "confirm", state, onAction, primary = true)
    Spacer(Modifier.height(12.dp))
    VocaButton("no · Cancelar", "cancel", state, onAction)
}

@Composable
private fun Help(state: VocaUiState, testStatus: String, testRunning: Boolean, onRunModelTest: () -> Unit) {
    Text("10 palabras. Todo el control.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    val groups = listOf(
        Triple("Navegar", "up · down · left · right", "Mueve el borde de selección, también por las pestañas inferiores."),
        Triple("Elegir y volver", "yes · no", "yes abre o elige. no vuelve o cancela. En una confirmación, yes acepta."),
        Triple("Dispositivos", "on · off", "Enciende o apaga el dispositivo abierto."),
        Triple("Rutinas", "go · stop", "go inicia la rutina seleccionada. stop la detiene y restaura el estado previo.")
    )
    for ((title, commands, explanation) in groups) {
        Surface(color = CardColor, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(commands, color = Mint, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Text(explanation, color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Surface(color = ControlColor, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("No entendí el comando", color = Amber, fontWeight = FontWeight.SemiBold)
            Text("Acércate al micrófono y repite una sola palabra en inglés. Deja una pausa entre comandos. Los sonidos no reconocidos no cambian los dispositivos.",
                color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    Text("La escucha funciona mientras la app está abierta. Al salir se pausa; puedes activarla otra vez con el micrófono.",
        color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 18.dp))
    if (BuildConfig.DEBUG) {
        Spacer(Modifier.height(26.dp))
        Text("Prueba del modelo", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Comprueba audios guardados sin usar el micrófono. Esta prueba no ejecuta acciones del hogar.",
            color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Button(onClick = onRunModelTest, enabled = !testRunning,
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Background),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Text(if (testRunning) "Comprobando…" else "Probar modelo con audios")
        }
        if (testStatus.isNotBlank()) {
            Text(testStatus, color = Mint, fontSize = 11.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp))
        }
        if (state.lastPrediction.isNotBlank()) {
            Text("Último audio: ${state.lastPrediction} · ${(state.lastConfidence * 100).roundToInt()} % · ${state.inferenceMs} ms",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun MicrophonePanel(state: VocaUiState, onAction: (String) -> Unit) {
    val focused = VocaReducer.focused(state) == "mic"
    Surface(onClick = { onAction("mic") }, color = if (state.listening) Emerald else CardColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(if (focused) 1.5.dp else 0.dp,
            if (focused) Mint else Color.Transparent, RoundedCornerShape(16.dp)).semantics {
            contentDescription = if (state.listening) "Desactivar micrófono" else "Activar micrófono"
            selected = focused
        }) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VocaIcon("mic", if (state.listening) Mint else Muted, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(when {
                    state.microphoneStarting -> "Activando micrófono…"
                    state.listening -> "Te escucho"
                    else -> "Micrófono desactivado"
                }, color = if (state.listening) Mint else White, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (state.listening) {
                    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { index ->
                            val amplitude = (state.level * (1f - kotlin.math.abs(index - 2) * .18f)).coerceIn(0f, 1f)
                            Box(Modifier.width(3.dp).height((4f + 16f * amplitude).dp).background(Mint, RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
            Text(state.notice, color = if (state.notice.startsWith("No entendí")) Amber else Muted,
                fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun NavigationBar(state: VocaUiState, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val items = listOf(Triple("Hogar", "home", "nav:home"), Triple("Rutinas", "play", "nav:routines"), Triple("Ayuda", "help", "nav:help"))
        val active = when (state.screen) {
            VocaScreen.ROUTINES -> "nav:routines"
            VocaScreen.HELP -> "nav:help"
            else -> "nav:home"
        }
        for ((label, icon, target) in items) {
            FocusSurface(target, state, onAction, Modifier.weight(1f),
                color = if (target == active) ControlColor else Background, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    VocaIcon(icon, if (target == active) Mint else Muted, 22.dp)
                    Text(label, color = if (target == active) Mint else Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun VocaButton(label: String, target: String, state: VocaUiState, onAction: (String) -> Unit, primary: Boolean = false) {
    FocusSurface(target, state, onAction, Modifier.fillMaxWidth().heightIn(min = 50.dp),
        color = if (primary) Mint else ControlColor, shape = RoundedCornerShape(12.dp)) {
        Text(label, color = if (primary) Background else White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp).fillMaxWidth())
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FocusSurface(target: String, state: VocaUiState, onAction: (String) -> Unit,
    modifier: Modifier = Modifier, color: Color = CardColor, shape: RoundedCornerShape = CardShape,
    content: @Composable () -> Unit) {
    val focused = VocaReducer.focused(state) == target
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Surface(onClick = { onAction(target) }, color = color, shape = shape,
        modifier = modifier.bringIntoViewRequester(bringIntoView)
            .border(if (focused) 1.5.dp else 0.dp, if (focused && color == Mint) White else if (focused) Mint else Color.Transparent, shape)
            .semantics { selected = focused }) {
        content()
    }
}

/** Small vector icons, drawn locally so the UI does not depend on downloaded artwork. */
@Composable
private fun VocaIcon(name: String, color: Color, size: Dp, description: String? = null) {
    Canvas(Modifier.size(size).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        withTransform({ scale(this@Canvas.size.width / 24f, this@Canvas.size.height / 24f, Offset.Zero) }) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.7f, StrokeCap.Round)
            when (name) {
                "home" -> {
                    val path = Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(21f, 21f); lineTo(3f, 21f); close() }
                    drawPath(path, color, style = stroke)
                    line(9f, 21f, 9f, 13f); line(9f, 13f, 15f, 13f); line(15f, 13f, 15f, 21f)
                }
                "bulb" -> {
                    val path = Path().apply {
                        moveTo(8f, 17f); lineTo(8f, 15f); cubicTo(8f, 13f, 5f, 12f, 5f, 8f)
                        cubicTo(5f, 4f, 8f, 2f, 12f, 2f); cubicTo(16f, 2f, 19f, 4f, 19f, 8f)
                        cubicTo(19f, 12f, 16f, 13f, 16f, 15f); lineTo(16f, 17f); close()
                    }
                    drawPath(path, color, style = stroke); line(9f, 20f, 15f, 20f); line(10f, 23f, 14f, 23f)
                }
                "fan" -> {
                    drawCircle(color, 2f, Offset(12f, 12f), style = stroke)
                    repeat(3) { blade ->
                        rotate(blade * 120f, Offset(12f, 12f)) {
                            val path = Path().apply { moveTo(11f, 10f); cubicTo(4f, 9f, 7f, 1f, 11f, 3f); cubicTo(14f, 4f, 15f, 8f, 13f, 10f) }
                            drawPath(path, color, style = stroke)
                        }
                    }
                }
                "tv" -> {
                    drawRoundRect(color, Offset(2f, 3f), Size(20f, 14f), CornerRadius(2f), style = stroke)
                    line(12f, 17f, 12f, 22f); line(7f, 22f, 17f, 22f)
                }
                "play" -> drawPath(Path().apply { moveTo(8f, 4f); lineTo(20f, 12f); lineTo(8f, 20f); close() }, color, style = stroke)
                "mic" -> {
                    drawRoundRect(color, Offset(9f, 2f), Size(6f, 12f), CornerRadius(3f), style = stroke)
                    drawArc(color, 0f, 180f, false, Offset(6f, 6f), Size(12f, 12f), style = stroke)
                    line(12f, 18f, 12f, 22f); line(8f, 22f, 16f, 22f)
                }
                "back" -> { line(15f, 4f, 7f, 12f); line(7f, 12f, 15f, 20f) }
                "power" -> { drawArc(color, -45f, 270f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke); line(12f, 1f, 12f, 12f) }
                "moon" -> {
                    drawPath(Path().apply { moveTo(15f, 2f); cubicTo(3f, 0f, 0f, 18f, 11f, 21f); cubicTo(17f, 23f, 22f, 18f, 22f, 14f); cubicTo(11f, 18f, 7f, 7f, 15f, 2f) }, color, style = stroke)
                }
                "shield" -> {
                    drawPath(Path().apply { moveTo(12f, 2f); lineTo(21f, 6f); lineTo(20f, 15f); lineTo(12f, 22f); lineTo(4f, 15f); lineTo(3f, 6f); close() }, color, style = stroke)
                    line(7f, 11f, 11f, 15f); line(11f, 15f, 17f, 8f)
                }
                "help" -> {
                    drawCircle(color, 10f, Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply { moveTo(9f, 8f); cubicTo(10f, 5f, 16f, 5f, 16f, 9f); cubicTo(16f, 12f, 12f, 11f, 12f, 15f) }, color, style = stroke)
                    drawCircle(color, 1f, Offset(12f, 18f))
                }
            }
        }
    }
}
