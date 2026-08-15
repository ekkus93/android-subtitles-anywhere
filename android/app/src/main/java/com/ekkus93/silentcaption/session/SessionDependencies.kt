package com.ekkus93.silentcaption.session

import com.ekkus93.silentcaption.usb.NativeRustProtocolApi
import com.ekkus93.silentcaption.usb.UsbSessionController

/** Native Rust session operations required by Android session orchestration. */
interface RustSessionApi {
    fun startSession(sessionId: Long): Boolean

    fun stopSession(): Boolean
}

/** Owns Rust's per-session state while leaving the process-wide JNI handle reusable. */
class RustCoreSessionDependency(
    private val rust: RustSessionApi,
) : SessionDependency {
    override val ready: Boolean = true
    private var started = false

    override fun start(sessionId: Long): Boolean {
        if (started) return true
        if (!rust.startSession(sessionId)) return false
        started = true
        return true
    }

    override fun stop() {
        if (!started) return
        rust.stopSession()
        started = false
    }
}

/** Starts and owns one USB receive loop for an active caption session. */
class UsbSessionDependency(
    private val isReady: () -> Boolean,
    private val controllerFactory: () -> UsbSessionController,
) : SessionDependency {
    private var controller: UsbSessionController? = null

    override val ready: Boolean
        get() = isReady()

    override fun start(sessionId: Long): Boolean {
        if (controller != null) return true
        if (sessionId <= 0L || !ready) return false
        return try {
            controllerFactory().also {
                it.start()
                controller = it
            }
            true
        } catch (_: RuntimeException) {
            controller?.close()
            controller = null
            false
        }
    }

    override fun stop() {
        controller?.close()
        controller = null
    }
}

/**
 * Adapts a platform readiness probe and optional lifecycle hooks to session orchestration.
 *
 * Bluetooth routing and ASR implementations can use this boundary without teaching the
 * state machine platform-specific APIs. A dependency never reports a successful start if
 * its readiness probe is false.
 */
class PlatformSessionDependency(
    private val isReady: () -> Boolean,
    private val onStart: (Long) -> Boolean = { true },
    private val onStop: () -> Unit = {},
) : SessionDependency {
    private var started = false

    override val ready: Boolean
        get() = isReady()

    override fun start(sessionId: Long): Boolean {
        if (started) return true
        if (sessionId <= 0L || !ready || !onStart(sessionId)) return false
        started = true
        return true
    }

    override fun stop() {
        if (!started) return
        onStop()
        started = false
    }
}

/** Production adapter for the existing JNI protocol boundary. */
fun NativeRustProtocolApi.asSessionDependency(): SessionDependency =
    RustCoreSessionDependency(
        object : RustSessionApi {
            override fun startSession(sessionId: Long): Boolean = this@asSessionDependency.startSession(sessionId)

            override fun stopSession(): Boolean = this@asSessionDependency.stopSession()
        },
    )
