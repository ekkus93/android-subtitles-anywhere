package com.ekkus93.silentcaption.ui.home

import com.ekkus93.silentcaption.session.CaptionSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenStateTest {
    @Test
    fun ready_requires_transport_route_model_and_ready_session() {
        val state = readyState()

        assertTrue(state.canStart)
        assertTrue(state.blockers.isEmpty())
    }

    @Test
    fun blockers_explain_every_missing_start_dependency() {
        val state =
            readyState().copy(
                dongle = DongleStatus(usbReady = false, bluetoothRouteReady = false),
                modelReady = false,
                sessionPhase = CaptionSessionPhase.Unavailable,
            )

        assertFalse(state.canStart)
        assertEquals(
            listOf(
                "USB dongle is not ready",
                "Bluetooth media route is not ready",
                "Speech model is not installed and verified",
            ),
            state.blockers,
        )
    }

    @Test
    fun bluetooth_connection_is_not_enough_without_media_route_readiness() {
        val state =
            readyState().copy(
                dongle = DongleStatus(usbReady = true, bluetoothRouteReady = false),
            )

        assertFalse(state.canStart)
        assertEquals(listOf("Bluetooth media route is not ready"), state.blockers)
    }

    private fun readyState() =
        HomeUiState(
            dongle = DongleStatus(true, true, "0.1.0", "v1"),
            modelReady = true,
            backendLabel = "Whisper Tiny multilingual",
            languageLabel = "Auto",
            sessionPhase = CaptionSessionPhase.Ready,
        )
}
