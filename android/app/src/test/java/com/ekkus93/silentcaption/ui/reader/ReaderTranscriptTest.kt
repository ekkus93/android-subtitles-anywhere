package com.ekkus93.silentcaption.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTranscriptTest {
    @Test
    fun `partial caption remains separate from committed history`() {
        val transcript = ReaderTranscript()

        transcript.updatePartial("hello wor")

        assertEquals("hello wor", transcript.snapshot().currentCaption)
        assertTrue(transcript.snapshot().committed.isEmpty())
    }

    @Test
    fun `commit moves final text into history`() {
        val transcript = ReaderTranscript()
        transcript.updatePartial("hello wor")

        val state = transcript.commit("hello world")

        assertEquals("", state.currentCaption)
        assertEquals(listOf("hello world"), state.committed.map { it.text })
    }

    @Test
    fun `history retention is bounded`() {
        val transcript = ReaderTranscript(maxEntries = 2)

        transcript.commit("one")
        transcript.commit("two")
        val state = transcript.commit("three")

        assertEquals(listOf("two", "three"), state.committed.map { it.text })
    }

    @Test
    fun `scrolling backward disengages live following until jump to live`() {
        val transcript = ReaderTranscript()

        assertTrue(transcript.snapshot().followingLive)
        assertFalse(transcript.userScrolledBackward().followingLive)
        assertTrue(transcript.jumpToLive().followingLive)
    }

    @Test
    fun `clear removes current and committed captions`() {
        val transcript = ReaderTranscript()
        transcript.commit("one")
        transcript.updatePartial("two")

        val state = transcript.clear()

        assertTrue(state.committed.isEmpty())
        assertEquals("", state.currentCaption)
    }
}
