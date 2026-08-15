package com.ekkus93.silentcaption.session

interface SessionDependency {
    val ready: Boolean
    fun start(sessionId: Long): Boolean
    fun stop()
}

class CaptionSessionCoordinator(
    private val usb: SessionDependency,
    private val rustCore: SessionDependency,
    private val bluetoothRoute: SessionDependency,
    private val asr: SessionDependency,
    private val stateMachine: CaptionSessionStateMachine = CaptionSessionStateMachine(),
) {
    val state: CaptionSessionState
        get() = stateMachine.state

    fun refreshReadiness(): CaptionSessionState {
        val ready = usb.ready && rustCore.ready && bluetoothRoute.ready && asr.ready
        return stateMachine.accept(
            if (ready) CaptionSessionEvent.DependenciesReady else CaptionSessionEvent.DependenciesUnavailable,
        )
    }

    fun startListening(sessionId: Long): CaptionSessionState {
        refreshReadiness()
        if (state.phase != CaptionSessionPhase.Ready || sessionId <= 0) return state
        stateMachine.accept(CaptionSessionEvent.StartRequested(sessionId))

        val started = mutableListOf<SessionDependency>()
        for (dependency in listOf(rustCore, usb, bluetoothRoute, asr)) {
            if (!dependency.start(sessionId)) {
                started.asReversed().forEach(SessionDependency::stop)
                return stateMachine.accept(CaptionSessionEvent.Failed("session dependency failed to start"))
            }
            started += dependency
        }
        return stateMachine.accept(CaptionSessionEvent.Started)
    }

    fun stopListening(): CaptionSessionState {
        if (state.phase !in setOf(
                CaptionSessionPhase.Starting,
                CaptionSessionPhase.Listening,
                CaptionSessionPhase.Reconnecting,
            )
        ) {
            return state
        }
        stateMachine.accept(CaptionSessionEvent.StopRequested)
        listOf(asr, bluetoothRoute, usb, rustCore).forEach(SessionDependency::stop)
        return stateMachine.accept(CaptionSessionEvent.Stopped)
    }

    fun transportLost(): CaptionSessionState = stateMachine.accept(CaptionSessionEvent.TransportLost)

    fun reconnected(): CaptionSessionState = stateMachine.accept(CaptionSessionEvent.Reconnected)

    fun acceptsEventGeneration(generation: Long): Boolean = stateMachine.acceptsEventGeneration(generation)
}
