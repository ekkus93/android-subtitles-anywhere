package com.ekkus93.silentcaption.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

class UsbSessionControllerTest {
    @Test
    fun fakeTransportDeliversFragmentedFrameAndDetachesCleanly() {
        val frame = frame(byteArrayOf(1, 2))
        val transport = FakeTransport()
        val sink = RecordingSink()
        val states = mutableListOf<UsbTransportState>()
        val controller = UsbSessionController(transport, sink, states::add)
        controller.start()
        transport.incoming.put(frame.copyOfRange(0, 5))
        transport.incoming.put(frame.copyOfRange(5, frame.size))
        waitUntil { sink.frames.size == 1 }
        controller.close()
        assertEquals(1, sink.frames.size)
        assertTrue(states.first() is UsbTransportState.Ready)
        assertTrue(states.last() is UsbTransportState.Detached)
        assertTrue(sink.resetCount > 0)
    }

    @Test
    fun disconnectedReadNeverLeavesFalseReadyState() {
        val transport = FakeTransport()
        val states = mutableListOf<UsbTransportState>()
        val controller = UsbSessionController(transport, RecordingSink(), states::add)
        controller.start()
        transport.incoming.put(ByteArray(0))
        waitUntil { states.any { it is UsbTransportState.Failed } }
        assertTrue(states.any { it is UsbTransportState.Failed && it.error == UsbTransportError.DISCONNECTED })
        assertFalse(states.last() is UsbTransportState.Ready)
        controller.close()
    }

    private class FakeTransport : UsbByteTransport {
        override val identity = UsbDeviceIdentity(1, 0x10c4, 0xea60, "CP210x")
        val incoming = LinkedBlockingQueue<ByteArray>()

        override fun read(
            destination: ByteArray,
            timeoutMs: Int,
        ): Int {
            val value = incoming.poll(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS) ?: return 0
            if (value.isEmpty()) return -1
            value.copyInto(destination)
            return value.size
        }

        override fun write(
            source: ByteArray,
            timeoutMs: Int,
        ): Int = source.size

        override fun close() = Unit
    }

    private class RecordingSink : ProtocolFrameSink {
        val frames = mutableListOf<ByteArray>()
        var resetCount = 0

        override fun accept(frame: ByteArray) {
            frames += frame
        }

        override fun reset() {
            resetCount++
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(5)
        }
        error("condition not reached")
    }

    private fun frame(payload: ByteArray): ByteArray {
        val result = ByteArray(32 + payload.size)
        byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte()).copyInto(result)
        result[4] = 1
        result[6] = 0x11
        result[8] = payload.size.toByte()
        payload.copyInto(result, 32)
        return result
    }
}
