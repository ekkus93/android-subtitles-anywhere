package com.ekkus93.silentcaption.usb

private const val HEADER_BYTES = 32
private const val MAX_PAYLOAD_BYTES = 4096
private const val MAX_FRAME_BYTES = HEADER_BYTES + MAX_PAYLOAD_BYTES
private val MAGIC = byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte())

data class ProtocolFrameBytes(
    val bytes: ByteArray,
)

class ProtocolStreamParser(
    private val onFrame: (ProtocolFrameBytes) -> Unit,
    private val onRejectedBytes: (Int) -> Unit = {},
) {
    private val buffer = ByteArray(MAX_FRAME_BYTES)
    private var used = 0

    fun accept(
        source: ByteArray,
        length: Int = source.size,
    ) {
        require(length in 0..source.size)
        var offset = 0
        while (offset < length) {
            if (used == buffer.size) {
                discardPrefix(1)
                onRejectedBytes(1)
            }
            val copied = minOf(length - offset, buffer.size - used)
            source.copyInto(buffer, used, offset, offset + copied)
            used += copied
            offset += copied
            drain()
        }
    }

    fun bufferedBytes(): Int = used

    fun reset() {
        used = 0
    }

    private fun drain() {
        while (used >= HEADER_BYTES) {
            if (!hasMagic()) {
                discardPrefix(1)
                onRejectedBytes(1)
                continue
            }
            val payloadLength = littleEndianU32(8)
            if (payloadLength > MAX_PAYLOAD_BYTES.toLong()) {
                discardPrefix(1)
                onRejectedBytes(1)
                continue
            }
            val frameLength = HEADER_BYTES + payloadLength.toInt()
            if (used < frameLength) return
            onFrame(ProtocolFrameBytes(buffer.copyOfRange(0, frameLength)))
            discardPrefix(frameLength)
        }
    }

    private fun hasMagic(): Boolean = MAGIC.indices.all { buffer[it] == MAGIC[it] }

    private fun littleEndianU32(offset: Int): Long =
        (buffer[offset].toLong() and 0xffL) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 8) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 3].toLong() and 0xffL) shl 24)

    private fun discardPrefix(count: Int) {
        if (count >= used) {
            used = 0
            return
        }
        buffer.copyInto(buffer, 0, count, used)
        used -= count
    }
}
