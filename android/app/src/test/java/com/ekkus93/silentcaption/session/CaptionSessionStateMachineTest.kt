package com.ekkus93.silentcaption.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSessionStateMachineTest {
    @Test
    fun lifecycle_has_explicit_expected_states() {
        val machine = CaptionSessionStateMachine()
        assertEquals(CaptionSessionPhase.Unavailable, machine.state.phase)
        machine.accept(CaptionSessionEvent.DependenciesReady)
        assertEquals(CaptionSessionPhase.Ready, machine.state.phase)
        machine.accept(CaptionSessionEvent.StartRequested(7))
        assertEquals(CaptionSessionPhase.Starting, machine.state.phase)
        machine.accept(CaptionSessionEvent.Started)
        assertEquals(CaptionSessionPhase.Listening, machine.state.phase)
        machine.accept(CaptionSessionEvent.TransportLost)
        assertEquals(CaptionSessionPhase.Reconnecting, machine.state.phase)
        machine.accept(CaptionSessionEvent.Reconnected)
        assertEquals(CaptionSessionPhase.Listening, machine.state.phase)
        machine.accept(CaptionSessionEvent.StopRequested)
        assertEquals(CaptionSessionPhase.Stopping, machine.state.phase)
        machine.accept(CaptionSessionEvent.Stopped)
        assertEquals(CaptionSessionPhase.Ready, machine.state.phase)
    }

    @Test
    fun duplicate_start_and_stop_are_idempotent() {
        val machine = CaptionSessionStateMachine()
        machine.accept(CaptionSessionEvent.DependenciesReady)
        machine.accept(CaptionSessionEvent.StartRequested(9))
        val generation = machine.state.generation
        machine.accept(CaptionSessionEvent.StartRequested(10))
        assertEquals(9, machine.state.sessionId)
        assertEquals(generation, machine.state.generation)
        machine.accept(CaptionSessionEvent.Started)
        machine.accept(CaptionSessionEvent.StopRequested)
        machine.accept(CaptionSessionEvent.Stopped)
        val stopped = machine.state
        machine.accept(CaptionSessionEvent.Stopped)
        assertEquals(stopped, machine.state)
    }

    @Test
    fun prior_generation_events_are_rejected_after_restart() {
        val machine = CaptionSessionStateMachine()
        machine.accept(CaptionSessionEvent.DependenciesReady)
        machine.accept(CaptionSessionEvent.StartRequested(1))
        machine.accept(CaptionSessionEvent.Started)
        val first = machine.state.generation
        assertTrue(machine.acceptsEventGeneration(first))
        machine.accept(CaptionSessionEvent.StopRequested)
        machine.accept(CaptionSessionEvent.Stopped)
        machine.accept(CaptionSessionEvent.StartRequested(2))
        machine.accept(CaptionSessionEvent.Started)
        assertFalse(machine.acceptsEventGeneration(first))
        assertTrue(machine.acceptsEventGeneration(machine.state.generation))
    }

    @Test
    fun unavailable_and_error_states_never_claim_listening() {
        val machine = CaptionSessionStateMachine()
        machine.accept(CaptionSessionEvent.StartRequested(1))
        assertEquals(CaptionSessionPhase.Unavailable, machine.state.phase)
        machine.accept(CaptionSessionEvent.Failed("usb unavailable"))
        assertEquals(CaptionSessionPhase.Error, machine.state.phase)
        assertEquals("usb unavailable", machine.state.detail)
    }
}
