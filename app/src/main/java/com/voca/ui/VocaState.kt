package com.voca.ui

enum class VocaScreen { WELCOME, HOME, DEVICE, ROUTINES, CONFIRM, HELP }
enum class HomeDevice(val title: String, val icon: String) {
    LIGHTS("Luces de la sala", "bulb"), FAN("Ventilador", "fan"), TELEVISION("Televisión", "tv")
}

data class DeviceSnapshot(val lights: Boolean, val fan: Boolean, val television: Boolean)

data class VocaUiState(
    val screen: VocaScreen = VocaScreen.WELCOME,
    val device: HomeDevice = HomeDevice.LIGHTS,
    val lights: Boolean = true,
    val fan: Boolean = false,
    val television: Boolean = false,
    val focus: Int = 0,
    val selectedRoutine: Int = 0,
    val activeRoutine: Int? = null,
    val routineSnapshot: DeviceSnapshot? = null,
    val notice: String = "Tu voz controla este hogar simulado.",
    val listening: Boolean = false,
    val microphoneStarting: Boolean = false,
    val level: Float = 0f,
    val lastPrediction: String = "",
    val lastConfidence: Float = 0f,
    val inferenceMs: Long = 0L
) {
    fun isOn(target: HomeDevice): Boolean = when (target) {
        HomeDevice.LIGHTS -> lights
        HomeDevice.FAN -> fan
        HomeDevice.TELEVISION -> television
    }
    val onCount: Int get() = listOf(lights, fan, television).count { it }
}

data class VocaTransition(val state: VocaUiState, val toggleMicrophone: Boolean = false)

/** The touch controls and recognized commands use the same state transitions. */
object VocaReducer {
    val routineNames = listOf("Modo estudio", "Buenas noches", "Bienvenida")
    private val navigation = listOf("nav:home", "nav:routines", "nav:help", "mic")

    fun targets(state: VocaUiState): List<String> = when (state.screen) {
        VocaScreen.WELCOME -> listOf("mic", "continue")
        VocaScreen.HOME -> listOf("device:lights", "device:fan", "device:tv", "routines") + navigation
        VocaScreen.DEVICE -> listOf("on", "off") +
            (if (state.device == HomeDevice.LIGHTS) listOf("allOff") else emptyList()) + navigation
        VocaScreen.ROUTINES -> listOf("routine:0", "routine:1", "routine:2", "go", "stop") + navigation
        VocaScreen.CONFIRM -> listOf("confirm", "cancel", "mic")
        VocaScreen.HELP -> navigation
    }

    fun focused(state: VocaUiState): String = targets(state)[state.focus.coerceIn(0, targets(state).lastIndex)]

    fun command(state: VocaUiState, command: String): VocaTransition = when (command) {
        "up", "down", "left", "right" -> VocaTransition(moveFocus(state, command))
        "yes" -> action(state, if (state.screen == VocaScreen.CONFIRM) "confirm" else focused(state))
        "no" -> action(state, "back")
        "on", "off" -> if (state.screen == VocaScreen.DEVICE) action(state, command)
            else VocaTransition(state.copy(notice = "Abre un dispositivo para decir on u off."))
        "go", "stop" -> if (state.screen == VocaScreen.ROUTINES) action(state, command)
            else VocaTransition(state.copy(notice = "Abre Rutinas para decir go o stop."))
        else -> VocaTransition(state.copy(notice = "No entendí el comando. Acércate al micrófono y repite."))
    }

    fun action(state: VocaUiState, target: String): VocaTransition {
        fun next(screen: VocaScreen, notice: String) = VocaTransition(state.copy(screen = screen, focus = 0, notice = notice))
        return when {
            target == "mic" -> VocaTransition(
                if (state.screen == VocaScreen.WELCOME) state.copy(screen = VocaScreen.HOME, focus = 0) else state,
                toggleMicrophone = true
            )
            target == "continue" || target == "nav:home" -> next(VocaScreen.HOME, "Di yes para abrir la opción seleccionada.")
            target == "routines" || target == "nav:routines" -> next(VocaScreen.ROUTINES, "Elige una rutina y di go para iniciarla.")
            target == "nav:help" -> next(VocaScreen.HELP, "Los diez comandos se pronuncian en inglés.")
            target.startsWith("device:") -> {
                val device = when (target) {
                    "device:fan" -> HomeDevice.FAN
                    "device:tv" -> HomeDevice.TELEVISION
                    else -> HomeDevice.LIGHTS
                }
                VocaTransition(state.copy(screen = VocaScreen.DEVICE, device = device, focus = 0,
                    notice = "Di on para encender u off para apagar."))
            }
            target == "back" || target == "cancel" -> when (state.screen) {
                VocaScreen.CONFIRM -> next(VocaScreen.DEVICE, "Acción cancelada. No se cambió ningún dispositivo.")
                VocaScreen.HOME -> VocaTransition(state.copy(notice = "Ya estás en el hogar. Elige una opción."))
                else -> next(VocaScreen.HOME, "Di yes para abrir la opción seleccionada.")
            }
            target == "allOff" && state.screen == VocaScreen.DEVICE && state.device == HomeDevice.LIGHTS ->
                next(VocaScreen.CONFIRM, "Di yes para apagar todas las luces o no para cancelar.")
            target == "confirm" && state.screen == VocaScreen.CONFIRM ->
                VocaTransition(state.copy(screen = VocaScreen.DEVICE, focus = 0, lights = false,
                    notice = "Todas las luces se apagaron."))
            target == "on" || target == "off" -> {
                if (state.screen != VocaScreen.DEVICE) return VocaTransition(state)
                val on = target == "on"
                val changed = when (state.device) {
                    HomeDevice.LIGHTS -> state.copy(lights = on)
                    HomeDevice.FAN -> state.copy(fan = on)
                    HomeDevice.TELEVISION -> state.copy(television = on)
                }
                VocaTransition(changed.copy(notice = "${state.device.title}: ${if (on) "encendido" else "apagado"}."))
            }
            target.startsWith("routine:") -> {
                val selected = target.substringAfter(':').toIntOrNull()?.coerceIn(0, 2) ?: 0
                VocaTransition(state.copy(selectedRoutine = selected, focus = 3,
                    notice = "${routineNames[selected]} seleccionada. Di go para iniciar."))
            }
            target == "go" && state.screen == VocaScreen.ROUTINES -> {
                val snapshot = state.routineSnapshot ?: DeviceSnapshot(state.lights, state.fan, state.television)
                val changed = when (state.selectedRoutine) {
                    0 -> state.copy(lights = true, fan = true)
                    1 -> state.copy(lights = false, television = false)
                    else -> state.copy(lights = true)
                }
                VocaTransition(changed.copy(activeRoutine = state.selectedRoutine, routineSnapshot = snapshot,
                    notice = "${routineNames[state.selectedRoutine]} iniciada. Di stop para restaurar el estado anterior."))
            }
            target == "stop" && state.screen == VocaScreen.ROUTINES -> {
                val saved = state.routineSnapshot
                if (saved == null) VocaTransition(state.copy(notice = "No hay una rutina activa."))
                else VocaTransition(state.copy(lights = saved.lights, fan = saved.fan, television = saved.television,
                    activeRoutine = null, routineSnapshot = null, notice = "Rutina detenida. Se restauró el estado anterior."))
            }
            else -> VocaTransition(state)
        }
    }

    private fun moveFocus(state: VocaUiState, direction: String): VocaUiState {
        val count = targets(state).size
        val old = state.focus.coerceIn(0, count - 1)
        val delta = when (direction) {
            "up" -> if (state.screen == VocaScreen.HOME && old < 4) -2 else -1
            "down" -> if (state.screen == VocaScreen.HOME && old < 4) 2 else 1
            "left" -> -1
            else -> 1
        }
        val next = (old + delta + count) % count
        val target = targets(state)[next]
        val selected = if (target.startsWith("routine:")) target.substringAfter(':').toInt() else state.selectedRoutine
        return state.copy(focus = next, selectedRoutine = selected, notice = "Opción seleccionada. Di yes para elegir.")
    }
}
