package com.voca

import com.voca.ui.HomeDevice
import com.voca.ui.VocaReducer
import com.voca.ui.VocaScreen
import com.voca.ui.VocaUiState
import org.junit.Assert.*
import org.junit.Test

class VocaStateTest {
    @Test fun allLightsOffRequiresConfirmationAndCancelPreservesState() {
        val device = VocaUiState(screen = VocaScreen.DEVICE, device = HomeDevice.LIGHTS, lights = true)
        val confirmation = VocaReducer.action(device, "allOff").state
        assertEquals(VocaScreen.CONFIRM, confirmation.screen)
        assertTrue(confirmation.lights)
        assertTrue(VocaReducer.command(confirmation, "no").state.lights)
        assertFalse(VocaReducer.command(confirmation, "yes").state.lights)
    }

    @Test fun routineStopRestoresOriginalDeviceStateEvenAfterSwitchingRoutine() {
        val before = VocaUiState(screen = VocaScreen.ROUTINES, lights = false, fan = false, television = true)
        val study = VocaReducer.command(before, "go").state
        assertTrue(study.lights)
        assertTrue(study.fan)
        val night = VocaReducer.command(study.copy(selectedRoutine = 1), "go").state
        assertFalse(night.television)
        val stopped = VocaReducer.command(night, "stop").state
        assertEquals(before.lights, stopped.lights)
        assertEquals(before.fan, stopped.fan)
        assertEquals(before.television, stopped.television)
        assertNull(stopped.activeRoutine)
    }

    @Test fun unknownAndOutOfContextCommandsDoNotChangeDevices() {
        val before = VocaUiState(screen = VocaScreen.HOME, lights = false, fan = true)
        for (command in listOf("unknown", "on", "off", "go", "stop")) {
            val after = VocaReducer.command(before, command).state
            assertEquals(before.lights, after.lights)
            assertEquals(before.fan, after.fan)
            assertEquals(before.screen, after.screen)
        }
    }

    @Test fun deviceCommandsChangeOnlyTheOpenedDevice() {
        val state = VocaUiState(screen = VocaScreen.DEVICE, device = HomeDevice.FAN,
            lights = true, fan = false, television = true)
        val turnedOn = VocaReducer.command(state, "on").state
        assertTrue(turnedOn.fan)
        assertEquals(state.lights, turnedOn.lights)
        assertEquals(state.television, turnedOn.television)
        val returnedHome = VocaReducer.command(turnedOn, "no").state
        assertEquals(VocaScreen.HOME, returnedHome.screen)
        assertTrue(returnedHome.fan)
    }

    @Test fun allNavigationAndMicrophoneTargetsAreReachableByVoice() {
        val initial = VocaUiState(screen = VocaScreen.HOME)
        val visited = mutableSetOf<String>()
        var state = initial
        repeat(VocaReducer.targets(state).size) {
            visited.add(VocaReducer.focused(state))
            state = VocaReducer.command(state, "right").state
        }
        assertEquals(VocaReducer.targets(initial).toSet(), visited)
        val helpFocus = initial.copy(focus = VocaReducer.targets(initial).indexOf("nav:help"))
        assertEquals(VocaScreen.HELP, VocaReducer.command(helpFocus, "yes").state.screen)
    }
}
