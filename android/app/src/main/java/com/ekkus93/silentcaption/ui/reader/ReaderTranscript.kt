package com.ekkus93.silentcaption.ui.reader

data class TranscriptEntry(
    val id: Long,
    val text: String,
)

data class ReaderTranscriptState(
    val committed: List<TranscriptEntry> = emptyList(),
    val currentCaption: String = "",
    val followingLive: Boolean = true,
)

class ReaderTranscript(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private var nextId = 1L
    private var state = ReaderTranscriptState()

    fun snapshot(): ReaderTranscriptState = state

    fun updatePartial(text: String): ReaderTranscriptState {
        state = state.copy(currentCaption = text)
        return state
    }

    fun commit(text: String): ReaderTranscriptState {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            state = state.copy(currentCaption = "")
            return state
        }
        val updated = state.committed + TranscriptEntry(nextId++, normalized)
        state =
            state.copy(
                committed = updated.takeLast(maxEntries),
                currentCaption = "",
            )
        return state
    }

    fun userScrolledBackward(): ReaderTranscriptState {
        state = state.copy(followingLive = false)
        return state
    }

    fun jumpToLive(): ReaderTranscriptState {
        state = state.copy(followingLive = true)
        return state
    }

    fun clear(): ReaderTranscriptState {
        state = ReaderTranscriptState(followingLive = state.followingLive)
        return state
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
