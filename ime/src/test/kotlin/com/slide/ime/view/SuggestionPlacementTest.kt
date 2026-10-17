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

        assertNull(SuggestionStripLayout.slotAt(24f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(0, SuggestionStripLayout.slotAt(48f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(1, SuggestionStripLayout.slotAt(150f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(2, SuggestionStripLayout.slotAt(311f, width, height, voiceEnabled = true, slotCount = 3))
        assertNull(SuggestionStripLayout.slotAt(312f, width, height, voiceEnabled = true, slotCount = 3))
    }

    @Test
    fun `disabling voice gives its control width back to suggestions`() {
        assertEquals(264f, SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true))
        assertEquals(312f, SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = false))
        // The strip the keyboard actually draws: the settings square at the left and four to the
        // right of the candidates - emoji, language, symbols and the microphone. Five squares, and
        // the arithmetic has to agree with the drawing or a column of the strip is invisible.
        val trailing = SuggestionStripLayout.trailingSquares(voiceEnabled = true)
        assertEquals("emoji, language, symbols, microphone", 4, trailing)
        assertEquals(
            360f - 48f * 5,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true, trailingSquares = trailing),
        )
        assertEquals(
            2,
            SuggestionStripLayout.slotAt(340f, 360f, 48f, voiceEnabled = false, slotCount = 3),
        )
    }

    @Test
    fun `the emoji and language buttons take their width from the candidates`() {
        // The strip carries the two buttons the boards gave up. They are on it in every state, so
        // the candidate window is what gives way, and the window's right edge is exactly where the
        // language button's square begins: a point on a button is never read as a candidate.
        // Two trailing squares plus the settings square at the left: three of the strip's eight
        // forty-eight-pixel squares, so the candidates keep five. 168f here was four squares - the
        // count that put an ellipsis in every candidate on his phone, because the microphone was
        // being reserved once in this call and once more inside the arithmetic.
        assertEquals(
            216f,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true, trailingSquares = 2),
        )
        assertEquals(
            216f,
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
