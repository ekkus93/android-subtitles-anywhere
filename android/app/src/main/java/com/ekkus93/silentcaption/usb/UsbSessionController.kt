package com.ekkus93.silentcaption.usb

import java.util.concurrent.atomic.AtomicBoolean

interface ProtocolFrameSink {
    fun accept(frame: ByteArray)

    fun reset()
}

class UsbSessionController(
    private val transport: UsbByteTransport,
    private val frameSink: ProtocolFrameSink,
    private val onState: (UsbTransportState) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        onState(UsbTransportState.Ready(transport.identity))
        worker = Thread({ readLoop() }, "silent-caption-usb-rx").also { it.start() }
    }

    fun write(frame: ByteArray): Boolean {
        if (!running.get()) return false
        return try {
            transport.write(frame, WRITE_TIMEOUT_MS) == frame.size
        } catch (error: Exception) {
            fail(UsbTransportError.IO_ERROR, error.message ?: "USB write failed")
            false
        }
    }

    private fun readLoop() {
        val parser = ProtocolStreamParser(onFrame = { frameSink.accept(it.bytes) })
        val buffer = ByteArray(READ_BUFFER_BYTES)
        try {
            while (running.get()) {
                val count = transport.read(buffer, READ_TIMEOUT_MS)
                when {
                    count > 0 -> parser.accept(buffer, count)
                    count < 0 -> {
                        fail(UsbTransportError.DISCONNECTED, "USB transport disconnected")
                        break
                    }
                }
            }
        } catch (error: Exception) {
            if (running.get()) {
                fail(UsbTransportError.IO_ERROR, error.message ?: "USB read failed")
            }
        } finally {
            parser.reset()
            frameSink.reset()
        }
    }

    private fun fail(
        error: UsbTransportError,
        detail: String,
    ) {
        running.set(false)
        onState(UsbTransportState.Failed(error, detail))
    }

    override fun close() {
        running.set(false)
        transport.close()
        worker?.interrupt()
        if (worker !== Thread.currentThread()) worker?.join(JOIN_TIMEOUT_MS)
        worker = null
        frameSink.reset()
        onState(UsbTransportState.Detached)
    }

    companion object {
        private const val READ_BUFFER_BYTES = 4096
        private const val READ_TIMEOUT_MS = 100
        private const val WRITE_TIMEOUT_MS = 500
        private const val JOIN_TIMEOUT_MS = 1000L
    }
}
