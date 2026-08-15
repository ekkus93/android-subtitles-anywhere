package com.ekkus93.silentcaption.usb

sealed interface RustProtocolResult {
    data class Accepted(val events: List<RustProtocolEvent>) : RustProtocolResult
    data class Rejected(val reason: String) : RustProtocolResult
}

data class RustProtocolEvent(
    val kind: String,
    val sessionId: Long = 0,
    val timestampMs: Long = 0,
    val numericValue: Long = 0,
    val payload: ByteArray = byteArrayOf(),
)

interface RustProtocolApi {
    fun acceptFrame(frame: ByteArray): RustProtocolResult
    fun reset()
}

class RustProtocolFrameSink(
    private val rust: RustProtocolApi,
    private val onEvent: (RustProtocolEvent) -> Unit,
    private val onRejected: (String) -> Unit,
) : ProtocolFrameSink {
    override fun accept(frame: ByteArray) {
        when (val result = rust.acceptFrame(frame)) {
            is RustProtocolResult.Accepted -> result.events.forEach(onEvent)
            is RustProtocolResult.Rejected -> onRejected(result.reason)
        }
    }

    override fun reset() {
        rust.reset()
    }
}
