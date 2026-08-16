package com.ekkus93.silentcaption.ui.home

import com.ekkus93.silentcaption.session.CaptionSessionPhase
import com.ekkus93.silentcaption.session.CaptionSessionState
import com.ekkus93.silentcaption.setup.SetupChecklist
import com.ekkus93.silentcaption.setup.SetupItem
import com.ekkus93.silentcaption.setup.SetupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateFactoryTest {
    @Test
    fun maps_setup_session_and_model_facts_into_ready_home_state() {
        val state =
            HomeStateFactory.create(
                facts = facts(),
                displayMode = CaptionDisplayMode.Reader,
            )

        assertTrue(state.dongle.usbReady)
        assertTrue(state.dongle.bluetoothRouteReady)
        assertTrue(state.modelReady)
        assertTrue(state.canStart)
        assertEquals("1.2.3", state.dongle.firmware)
        assertEquals("v1", state.dongle.protocol)
    }

    @Test
    fun missing_bluetooth_route_remains_a_truthful_start_blocker() {
        val checklist =
            checklist(
                usb = SetupStatus.Ready,
                bluetooth = SetupStatus.ActionRequired,
            )
        val state =
            HomeStateFactory.create(
                facts = facts().copy(setup = checklist),
                displayMode = CaptionDisplayMode.Floating,
            )

        assertFalse(state.canStart)
        assertEquals(listOf("Bluetooth media route is not ready"), state.blockers)
    }

    @Test
    fun session_error_detail_is_exposed_to_home_ui() {
        val state =
            HomeStateFactory.create(
                facts =
                    facts().copy(
                        session = CaptionSessionState(CaptionSessionPhase.Error, detail = "USB detached"),
                    ),
                displayMode = CaptionDisplayMode.Compact,
            )

        assertEquals(CaptionSessionPhase.Error, state.sessionPhase)
        assertEquals("USB detached", state.errorDetail)
    }

    private fun facts() =
        HomeRuntimeFacts(
            setup = checklist(SetupStatus.Ready, SetupStatus.Ready),
            modelReady = true,
            backendLabel = "Whisper Tiny multilingual",
            languageLabel = "Auto",
            session = CaptionSessionState(CaptionSessionPhase.Ready),
            firmware = "1.2.3",
            protocol = "v1",
        )

    private fun checklist(
        usb: SetupStatus,
        bluetooth: SetupStatus,
    ) =
        SetupChecklist(
            items =
                listOf(
                    SetupItem("USB dongle", "usb", usb),
                    SetupItem("Bluetooth media route", "bluetooth", bluetooth),
                    SetupItem("Speech model", "model", SetupStatus.Ready),
                ),
            ready = usb == SetupStatus.Ready && bluetooth == SetupStatus.Ready,
        )
}
