package com.ekkus93.silentcaption.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {
    @Test
    fun unrelated_overlay_permission_is_not_required_for_reader_mode() {
        val result = SetupEvaluator.evaluate(readyInputs.copy(overlayGranted = false))
        assertTrue(result.ready)
        assertTrue(result.items.last().status == SetupStatus.Optional)
    }

    @Test
    fun floating_mode_requires_overlay_permission_and_recovers_after_settings_return() {
        val denied =
            SetupEvaluator.evaluate(
                readyInputs.copy(floatingModeRequested = true, overlayGranted = false),
            )
        assertFalse(denied.ready)

        val granted =
            SetupEvaluator.evaluate(
                readyInputs.copy(floatingModeRequested = true, overlayGranted = true),
            )
        assertTrue(granted.ready)
    }

    @Test
    fun notification_denial_blocks_only_when_runtime_permission_is_applicable() {
        assertFalse(
            SetupEvaluator
                .evaluate(
                    readyInputs.copy(notificationsRequired = true, notificationsGranted = false),
                ).ready,
        )
        assertTrue(
            SetupEvaluator
                .evaluate(
                    readyInputs.copy(notificationsRequired = false, notificationsGranted = false),
                ).ready,
        )
    }

    @Test
    fun usb_denial_and_retry_are_truthful() {
        assertFalse(SetupEvaluator.evaluate(readyInputs.copy(usbPermission = false)).ready)
        assertTrue(SetupEvaluator.evaluate(readyInputs.copy(usbPermission = true)).ready)
    }

    @Test
    fun bluetooth_connection_is_never_substituted_for_route_readiness() {
        val result = SetupEvaluator.evaluate(readyInputs.copy(bluetoothRouteReady = false))
        assertFalse(result.ready)
        assertTrue(result.items[1].detail.contains("connection alone is not enough"))
    }

    @Test
    fun every_required_setup_dimension_must_be_ready() {
        assertFalse(SetupEvaluator.evaluate(readyInputs.copy(usbAttached = false)).ready)
        assertFalse(SetupEvaluator.evaluate(readyInputs.copy(modelReady = false)).ready)
        assertTrue(SetupEvaluator.evaluate(readyInputs).ready)
    }

    private val readyInputs =
        SetupInputs(
            usbAttached = true,
            usbPermission = true,
            bluetoothRouteReady = true,
            modelReady = true,
            notificationsRequired = true,
            notificationsGranted = true,
            floatingModeRequested = false,
            overlayGranted = false,
        )
}
