package com.ekkus93.silentcaption.session

enum class CaptionSessionPhase {
    Unavailable,
    Ready,
    Starting,
    Listening,
    Reconnecting,
    Stopping,
    Error,
}

data class CaptionSessionState(
    val phase: CaptionSessionPhase = CaptionSessionPhase.Unavailable,
    val sessionId: Long? = null,
    val generation: Long = 0,
    val detail: String? = null,
)

sealed interface CaptionSessionEvent {
    data object DependenciesReady : CaptionSessionEvent
    data object DependenciesUnavailable : CaptionSessionEvent
    data class StartRequested(val sessionId: Long) : CaptionSessionEvent
    data object Started : CaptionSessionEvent
    data object TransportLost : CaptionSessionEvent
    data object Reconnected : CaptionSessionEvent
    data object StopRequested : CaptionSessionEvent
    data object Stopped : CaptionSessionEvent
    data class Failed(val detail: String) : CaptionSessionEvent
}

class CaptionSessionStateMachine {
    var state: CaptionSessionState = CaptionSessionState()
        private set

    fun accept(event: CaptionSessionEvent): CaptionSessionState {
        state = reduce(state, event)
        return state
    }

    fun acceptsEventGeneration(generation: Long): Boolean =
        generation == state.generation &&
            state.phase in setOf(CaptionSessionPhase.Listening, CaptionSessionPhase.Reconnecting)

    private fun reduce(
        current: CaptionSessionState,
        event: CaptionSessionEvent,
    ): CaptionSessionState = when (event) {
        CaptionSessionEvent.DependenciesReady ->
            if (current.phase == CaptionSessionPhase.Unavailable || current.phase == CaptionSessionPhase.Error) {
                current.copy(phase = CaptionSessionPhase.Ready, sessionId = null, detail = null)
            } else {
                current
            }
        CaptionSessionEvent.DependenciesUnavailable ->
            current.copy(phase = CaptionSessionPhase.Unavailable, sessionId = null, detail = null)
        is CaptionSessionEvent.StartRequested ->
            if (current.phase == CaptionSessionPhase.Ready && event.sessionId > 0) {
                current.copy(
                    phase = CaptionSessionPhase.Starting,
                    sessionId = event.sessionId,
                    generation = current.generation + 1,
                    detail = null,
                )
            } else {
                current
            }
        CaptionSessionEvent.Started ->
            if (current.phase == CaptionSessionPhase.Starting) {
                current.copy(phase = CaptionSessionPhase.Listening)
            } else {
                current
            }
        CaptionSessionEvent.TransportLost ->
            if (current.phase == CaptionSessionPhase.Listening) {
                current.copy(phase = CaptionSessionPhase.Reconnecting)
            } else {
                current
            }
        CaptionSessionEvent.Reconnected ->
            if (current.phase == CaptionSessionPhase.Reconnecting) {
                current.copy(phase = CaptionSessionPhase.Listening)
            } else {
                current
            }
        CaptionSessionEvent.StopRequested ->
            if (current.phase in setOf(
                    CaptionSessionPhase.Starting,
                    CaptionSessionPhase.Listening,
                    CaptionSessionPhase.Reconnecting,
                )
            ) {
                current.copy(phase = CaptionSessionPhase.Stopping)
            } else {
                current
            }
        CaptionSessionEvent.Stopped ->
            if (current.phase == CaptionSessionPhase.Stopping) {
                current.copy(phase = CaptionSessionPhase.Ready, sessionId = null, detail = null)
            } else {
                current
            }
        is CaptionSessionEvent.Failed ->
            current.copy(phase = CaptionSessionPhase.Error, sessionId = null, detail = event.detail)
    }
}
