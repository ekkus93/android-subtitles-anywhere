package com.ekkus93.silentcaption.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolStreamParserTest {
    @Test
    fun partialReadsProduceOneCompleteFrame() {
        val frame = frame(payload = byteArrayOf(1, 2, 3, 4))
        val received = mutableListOf<ByteArray>()
        val parser = ProtocolStreamParser(onFrame = { received += it.bytes })

        parser.accept(frame.copyOfRange(0, 7))
        parser.accept(frame.copyOfRange(7, 31))
        assertEquals(0, received.size)
        parser.accept(frame.copyOfRange(31, frame.size))

        assertEquals(1, received.size)
        assertArrayEquals(frame, received.single())
        assertEquals(0, parser.bufferedBytes())
    }

    @Test
    fun noiseBeforeMagicIsDiscardedAndParserResynchronizes() {
        val frame = frame(payload = byteArrayOf(9))
        val received = mutableListOf<ByteArray>()
        var rejected = 0
        val parser =
            ProtocolStreamParser(
                onFrame = { received += it.bytes },
                onRejectedBytes = { rejected += it },
            )
        parser.accept(byteArrayOf(0x55, 0x66, 0x77) + frame)
        assertEquals(3, rejected)
        assertArrayEquals(frame, received.single())
    }

    @Test
    fun oversizedLengthCannotGrowBufferWithoutBound() {
        val malformed = ByteArray(32)
        byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte())
            .copyInto(malformed)
        malformed[8] = 0xff.toByte()
        malformed[9] = 0xff.toByte()
        val parser = ProtocolStreamParser(onFrame = {})
        repeat(20) { parser.accept(malformed) }
        assert(parser.bufferedBytes() <= 32 + 4096)
    }

    private fun frame(payload: ByteArray): ByteArray {
        val result = ByteArray(32 + payload.size)
        byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte())
            .copyInto(result)
        result[4] = 1
        result[6] = 0x11
        result[8] = payload.size.toByte()
        payload.copyInto(result, 32)
        return result
    }
}
