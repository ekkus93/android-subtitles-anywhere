package com.ekkus93.silentcaption.usb

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeRustProtocolApi : RustProtocolApi, Closeable {
    private var handle = nativeCreate().also { check(it != 0L) { "Rust protocol boundary creation failed" } }

    override fun acceptFrame(frame: ByteArray): RustProtocolResult {
        val active = handle
        if (active == 0L) return RustProtocolResult.Rejected("native_handle_closed")
        return decodeResult(nativeAcceptFrame(active, frame))
    }

    fun startSession(sessionId: Long): Boolean =
        handle != 0L && sessionId > 0L && nativeStartSession(handle, sessionId)

    override fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    override fun close() {
        val active = handle
        handle = 0L
        if (active != 0L) nativeDestroy(active)
    }

    private fun decodeResult(encoded: ByteArray): RustProtocolResult {
        if (encoded.size < 2) return RustProtocolResult.Rejected("native_result_truncated")
        val status = encoded[0].toInt() and 0xff
        if (status != 0) return RustProtocolResult.Rejected(errorName(status))
        val count = encoded[1].toInt() and 0xff
        val buffer = ByteBuffer.wrap(encoded, 2, encoded.size - 2).order(ByteOrder.LITTLE_ENDIAN)
        val events = ArrayList<RustProtocolEvent>(count)
        repeat(count) {
            if (buffer.remaining() < EVENT_HEADER_BYTES) return RustProtocolResult.Rejected("native_event_truncated")
            val kind = eventName(buffer.get().toInt() and 0xff)
                ?: return RustProtocolResult.Rejected("native_event_unknown")
            val sessionId = buffer.long
            val timestampMs = buffer.int.toLong() and 0xffff_ffffL
            val numericValue = buffer.long
            val payloadLength = buffer.int.toLong() and 0xffff_ffffL
            if (payloadLength > buffer.remaining().toLong()) {
                return RustProtocolResult.Rejected("native_payload_truncated")
            }
            val payload = ByteArray(payloadLength.toInt())
            buffer.get(payload)
            events += RustProtocolEvent(kind, sessionId, timestampMs, numericValue, payload)
        }
        if (buffer.hasRemaining()) return RustProtocolResult.Rejected("native_result_trailing_bytes")
        return RustProtocolResult.Accepted(events)
    }

    private fun errorName(code: Int): String = when (code) {
        1 -> "invalid_handle"
        2 -> "integrity"
        3 -> "stale_session"
        else -> "protocol_error"
    }

    private fun eventName(code: Int): String? = when (code) {
        1 -> "hello"
        2 -> "audio_format"
        3 -> "audio_data"
        4 -> "status"
        5 -> "diagnostics"
        6 -> "error"
        7 -> "sequence_gap"
        8 -> "sequence_duplicate"
        9 -> "sequence_reset"
        else -> null
    }

    companion object {
        private const val EVENT_HEADER_BYTES = 25

        init {
            System.loadLibrary("silent_caption_jni")
        }

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long): Boolean
        @JvmStatic private external fun nativeReset(handle: Long): Boolean
        @JvmStatic private external fun nativeStartSession(handle: Long, sessionId: Long): Boolean
        @JvmStatic private external fun nativeAcceptFrame(handle: Long, frame: ByteArray): ByteArray
    }
}
