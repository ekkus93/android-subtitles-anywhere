package com.ekkus93.silentcaption.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RustProtocolFrameSinkTest {
    @Test
    fun acceptedRustEventsAreForwardedInOrder() {
        val rust = FakeRustApi(
            RustProtocolResult.Accepted(
                listOf(
                    RustProtocolEvent("sequence_gap", numericValue = 2),
                    RustProtocolEvent("audio_data", sessionId = 7, payload = byteArrayOf(1, 2)),
                ),
            ),
        )
        val events = mutableListOf<RustProtocolEvent>()
        val rejected = mutableListOf<String>()
        val sink = RustProtocolFrameSink(rust, events::add, rejected::add)
        sink.accept(byteArrayOf(1))
        assertEquals(listOf("sequence_gap", "audio_data"), events.map { it.kind })
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun rejectionIsDiagnosticAndResetCrossesBoundary() {
        val rust = FakeRustApi(RustProtocolResult.Rejected("integrity"))
        val rejected = mutableListOf<String>()
        val sink = RustProtocolFrameSink(rust, {}, rejected::add)
        sink.accept(byteArrayOf(1))
        assertEquals(listOf("integrity"), rejected)
        sink.reset()
        assertEquals(1, rust.resetCount)
    }

    private class FakeRustApi(private val result: RustProtocolResult) : RustProtocolApi {
        var resetCount = 0
        override fun acceptFrame(frame: ByteArray): RustProtocolResult = result
        override fun reset() { resetCount++ }
    }
}
