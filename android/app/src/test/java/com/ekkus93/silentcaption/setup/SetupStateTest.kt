package com.ekkus93.silentcaption.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {
    @Test
    fun unrelated_overlay_permission_is_not_required_for_reader_mode() {
        val result = SetupEvaluator.evaluate(readyInputs(floatingModeRequested = false, overlayGranted = false))
        assertTrue(result.ready)
        assertTrue(result.items.last().status == SetupStatus.Optional)
    }

    @Test
    fun floating_mode_requires_overlay_permission_and_recovers_after_settings_return() {
        val denied = SetupEvaluator.evaluate(readyInputs(floatingModeRequested = true, overlayGranted = false))
        assertFalse(denied.ready)

        val granted = SetupEvaluator.evaluate(readyInputs(floatingModeRequested = true, overlayGranted = true))
        assertTrue(granted.ready)
    }

    @Test
    fun notification_denial_blocks_only_when_runtime_permission_is_applicable() {
        assertFalse(
            SetupEvaluator.evaluate(
                readyInputs(notificationsRequired = true, notificationsGranted = false),
            ).ready,
        )
        assertTrue(
            SetupEvaluator.evaluate(
                readyInputs(notificationsRequired = false, notificationsGranted = false),
            ).ready,
        )
    }

    @Test
    fun usb_denial_and_retry_are_truthful() {
        assertFalse(SetupEvaluator.evaluate(readyInputs(usbPermission = false)).ready)
        assertTrue(SetupEvaluator.evaluate(readyInputs(usbPermission = true)).ready)
    }

    @Test
    fun bluetooth_connection_is_never_substituted_for_route_readiness() {
        val result = SetupEvaluator.evaluate(readyInputs(bluetoothRouteReady = false))
        assertFalse(result.ready)
        assertTrue(result.items[1].detail.contains("connection alone is not enough"))
    }

    @Test
    fun every_required_setup_dimension_must_be_ready() {
        assertFalse(SetupEvaluator.evaluate(readyInputs(usbAttached = false)).ready)
        assertFalse(SetupEvaluator.evaluate(readyInputs(modelReady = false)).ready)
        assertTrue(SetupEvaluator.evaluate(readyInputs()).ready)
    }

    private fun readyInputs(
        usbAttached: Boolean = true,
        usbPermission: Boolean = true,
        bluetoothRouteReady: Boolean = true,
        modelReady: Boolean = true,
        notificationsRequired: Boolean = true,
        notificationsGranted: Boolean = true,
        floatingModeRequested: Boolean = false,
        overlayGranted: Boolean = false,
    ) = SetupInputs(
        usbAttached = usbAttached,
        usbPermission = usbPermission,
        bluetoothRouteReady = bluetoothRouteReady,
        modelReady = modelReady,
        notificationsRequired = notificationsRequired,
        notificationsGranted = notificationsGranted,
        floatingModeRequested = floatingModeRequested,
        overlayGranted = overlayGranted,
    )
}
