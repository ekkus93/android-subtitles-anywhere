package com.ekkus93.silentcaption.session

import com.ekkus93.silentcaption.usb.ProtocolFrameSink
import com.ekkus93.silentcaption.usb.UsbByteTransport
import com.ekkus93.silentcaption.usb.UsbDeviceIdentity
import com.ekkus93.silentcaption.usb.UsbSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDependenciesTest {
    @Test
    fun rust_dependency_starts_and_stops_exactly_once() {
        val api = FakeRustSessionApi()
        val dependency = RustCoreSessionDependency(api)
        assertTrue(dependency.start(41))
        assertTrue(dependency.start(41))
        dependency.stop()
        dependency.stop()
        assertEquals(listOf(41L), api.startedSessions)
        assertEquals(1, api.stops)
    }

    @Test
    fun rust_start_failure_does_not_create_false_started_state() {
        val api = FakeRustSessionApi(startResult = false)
        val dependency = RustCoreSessionDependency(api)
        assertFalse(dependency.start(7))
        dependency.stop()
        assertEquals(0, api.stops)
    }

    @Test
    fun usb_dependency_requires_readiness_and_owns_controller_lifecycle() {
        var ready = false
        val transport = RecordingTransport()
        val dependency =
            UsbSessionDependency(
                isReady = { ready },
                controllerFactory = {
                    UsbSessionController(transport, NoOpSink()) {}
                },
            )
        assertFalse(dependency.start(1))
        ready = true
        assertTrue(dependency.start(1))
        assertTrue(dependency.start(1))
        dependency.stop()
        dependency.stop()
        assertEquals(1, transport.closes)
    }

    @Test
    fun platform_dependency_never_starts_when_readiness_is_false() {
        var starts = 0
        val dependency =
            PlatformSessionDependency(
                isReady = { false },
                onStart = {
                    starts++
                    true
                },
            )
        assertFalse(dependency.start(1))
        assertEquals(0, starts)
    }

    @Test
    fun platform_dependency_is_idempotent_and_stoppable() {
        var starts = 0
        var stops = 0
        val dependency =
            PlatformSessionDependency(
                isReady = { true },
                onStart = {
                    starts++
                    true
                },
                onStop = { stops++ },
            )
        assertTrue(dependency.start(9))
        assertTrue(dependency.start(9))
        dependency.stop()
        dependency.stop()
        assertEquals(1, starts)
        assertEquals(1, stops)
    }

    private class FakeRustSessionApi(
        private val startResult: Boolean = true,
    ) : RustSessionApi {
        val startedSessions = mutableListOf<Long>()
        var stops = 0

        override fun startSession(sessionId: Long): Boolean {
            startedSessions += sessionId
            return startResult
        }

        override fun stopSession(): Boolean {
            stops++
            return true
        }
    }

    private class RecordingTransport : UsbByteTransport {
        override val identity = UsbDeviceIdentity(1, 0x10c4, 0xea60, "fixture")
        var closes = 0

        override fun read(destination: ByteArray, timeoutMs: Int): Int = 0

        override fun write(source: ByteArray, timeoutMs: Int): Int = source.size

        override fun close() {
            closes++
        }
    }

    private class NoOpSink : ProtocolFrameSink {
        override fun accept(frame: ByteArray) = Unit

        override fun reset() = Unit
    }
}
