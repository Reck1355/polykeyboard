package com.slide.ime.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPlacementTest {
    @Test
    fun `best suggestion always occupies the centre cell`() {
        for (count in 1..3) {
            assertEquals(0, SuggestionPlacement.candidateAtSlot(count, 1))
            assertEquals(1, SuggestionPlacement.slotForCandidate(count, 0))
        }
    }

    @Test
    fun `alternatives fill left then right`() {
        assertEquals(1, SuggestionPlacement.candidateAtSlot(2, 0))
        assertNull(SuggestionPlacement.candidateAtSlot(2, 2))
        assertEquals(1, SuggestionPlacement.candidateAtSlot(3, 0))
        assertEquals(2, SuggestionPlacement.candidateAtSlot(3, 2))
    }

    @Test
    fun `single suggestion leaves side cells empty`() {
        assertNull(SuggestionPlacement.candidateAtSlot(1, 0))
        assertNull(SuggestionPlacement.candidateAtSlot(1, 2))
    }

    @Test
    fun `toolbar controls are excluded from suggestion hit targets`() {
        val width = 360f
        val height = 48f

        // With voice on the window runs from the microphone's square to the mode buttons: 48..168
        // wide, so three 56-pixel slots. A point in the microphone's square is a button, and so is
        // a point in the emoji square at the right edge; neither may resolve to a candidate.
        assertNull(SuggestionStripLayout.slotAt(24f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(0, SuggestionStripLayout.slotAt(48f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(1, SuggestionStripLayout.slotAt(110f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(2, SuggestionStripLayout.slotAt(215f, width, height, voiceEnabled = true, slotCount = 3))
        assertNull(SuggestionStripLayout.slotAt(216f, width, height, voiceEnabled = true, slotCount = 3))
    }

    @Test
    fun `disabling voice gives its control width back to suggestions`() {
        // One square at the left, the microphone's, and three at the right: emoji, language and
        // symbols. The settings square was the fifth, and it lives in the app's settings screens
        // now, so its width belongs to the words.
        assertEquals(168f, SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true))
        assertEquals(216f, SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = false))
        val trailing = SuggestionStripLayout.trailingSquares()
        assertEquals("emoji, language, symbols", 3, trailing)
        assertEquals(
            360f - 48f * 4,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true, trailingSquares = trailing),
        )
        // Where the candidate window ends is the same number every hit test reads: past it are
        // buttons, not words, so a tap there must not resolve to a candidate.
        assertEquals(
            2,
            SuggestionStripLayout.slotAt(210f, 360f, 48f, voiceEnabled = false, slotCount = 3),
        )
        assertNull(
            SuggestionStripLayout.slotAt(217f, 360f, 48f, voiceEnabled = false, slotCount = 3),
        )
        assertEquals(
            0f,
            SuggestionStripLayout.suggestionLeft(48f, voiceEnabled = false),
        )
        assertEquals(
            48f,
            SuggestionStripLayout.suggestionLeft(48f, voiceEnabled = true),
        )
    }

    @Test
    fun `the emoji and language buttons take their width from the candidates`() {
        // The three mode buttons are on the strip in every state, so the candidate window is what
        // gives way, and the window's right edge is exactly where the first button's square begins:
        // a point on a button is never read as a candidate. The microphone's square counts only
        // while voice typing is on - the switch that hides it hands that square back to the words.
        assertEquals(
            216f,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true, trailingSquares = 2),
        )
        assertEquals(
            264f,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = false, trailingSquares = 2),
        )
        // 216f is the width the candidates keep; the window's right edge is 48 + 216 = 264f, which
        // is where the first button begins.
        assertNull(
            SuggestionStripLayout.slotAt(
                264f, 360f, 48f, voiceEnabled = true, slotCount = 3, trailingSquares = 2,
            ),
        )
        assertEquals(
            2,
            SuggestionStripLayout.slotAt(
                214f, 360f, 48f, voiceEnabled = true, slotCount = 3, trailingSquares = 2,
            ),
        )
    }

    @Test
    fun `the correction mark follows its candidate into the centre`() {
        val candidates = listOf(
            StripSuggestion("the", isCorrection = true),
            StripSuggestion("then"),
            StripSuggestion("ten"),
        )

        assertTrue(SuggestionMarks.isCorrectionAtSlot(candidates, slot = 1))
        assertFalse(SuggestionMarks.isCorrectionAtSlot(candidates, slot = 0))
        assertFalse(SuggestionMarks.isCorrectionAtSlot(candidates, slot = 2))
    }

    @Test
    fun `a marked alternative keeps its mark beside an unmarked centre`() {
        val candidates = listOf(
            StripSuggestion("then"),
            StripSuggestion("the", isCorrection = true),
        )

        assertFalse("the centre is a completion and must not be underlined", SuggestionMarks.isCorrectionAtSlot(candidates, slot = 1))
        assertTrue(SuggestionMarks.isCorrectionAtSlot(candidates, slot = 0))
    }

    @Test
    fun `an empty strip and an empty cell are never marked`() {
        assertFalse(SuggestionMarks.isCorrectionAtSlot(emptyList(), slot = 1))
        assertFalse(SuggestionMarks.isCorrectionAtSlot(listOf(StripSuggestion("the")), slot = 0))
    }
}
