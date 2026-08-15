package com.ekkus93.silentcaption.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSessionCoordinatorTest {
    private class FakeDependency(
        override var ready: Boolean = true,
        var startResult: Boolean = true,
    ) : SessionDependency {
        var starts = 0
        var stops = 0

        override fun start(sessionId: Long): Boolean {
            starts++
            return startResult
        }

        override fun stop() {
            stops++
        }
    }

    @Test
    fun all_dependencies_must_be_ready() {
        val usb = FakeDependency(ready = false)
        val coordinator =
            CaptionSessionCoordinator(
                usb,
                FakeDependency(),
                FakeDependency(),
                FakeDependency(),
            )
        assertEquals(CaptionSessionPhase.Unavailable, coordinator.refreshReadiness().phase)
        usb.ready = true
        assertEquals(CaptionSessionPhase.Ready, coordinator.refreshReadiness().phase)
    }

    @Test
    fun start_and_stop_are_idempotent_across_dependencies() {
        val dependencies = List(4) { FakeDependency() }
        val coordinator =
            CaptionSessionCoordinator(
                dependencies[0],
                dependencies[1],
                dependencies[2],
                dependencies[3],
            )
        assertEquals(CaptionSessionPhase.Listening, coordinator.startListening(11).phase)
        coordinator.startListening(12)
        dependencies.forEach { assertEquals(1, it.starts) }
        assertEquals(CaptionSessionPhase.Ready, coordinator.stopListening().phase)
        coordinator.stopListening()
        dependencies.forEach { assertEquals(1, it.stops) }
    }

    @Test
    fun partial_start_failure_rolls_back_started_dependencies() {
        val usb = FakeDependency(startResult = false)
        val rust = FakeDependency()
        val bluetooth = FakeDependency()
        val asr = FakeDependency()
        val coordinator = CaptionSessionCoordinator(usb, rust, bluetooth, asr)
        assertEquals(CaptionSessionPhase.Error, coordinator.startListening(3).phase)
        assertEquals(1, rust.starts)
        assertEquals(1, rust.stops)
        assertEquals(1, usb.starts)
        assertEquals(0, bluetooth.starts)
        assertEquals(0, asr.starts)
    }

    @Test
    fun reconnect_preserves_generation_and_filters_stale_events() {
        val coordinator =
            CaptionSessionCoordinator(
                FakeDependency(),
                FakeDependency(),
                FakeDependency(),
                FakeDependency(),
            )
        coordinator.startListening(1)
        val first = coordinator.state.generation
        assertTrue(coordinator.acceptsEventGeneration(first))
        coordinator.transportLost()
        assertEquals(CaptionSessionPhase.Reconnecting, coordinator.state.phase)
        assertTrue(coordinator.acceptsEventGeneration(first))
        coordinator.reconnected()
        coordinator.stopListening()
        coordinator.startListening(2)
        assertFalse(coordinator.acceptsEventGeneration(first))
    }
}
