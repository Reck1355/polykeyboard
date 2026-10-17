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

        // With voice on the window runs from the strip's left edge to the microphone's square:
        // 0..168, so three 56-pixel slots. A point in the microphone's square is a button, and so is
        // a point in the emoji square at the right edge; neither may resolve to a candidate.
        assertEquals("the window starts at the strip's own left edge", 0, SuggestionStripLayout.slotAt(24f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(0, SuggestionStripLayout.slotAt(48f, width, height, voiceEnabled = true, slotCount = 3))
        assertEquals(1, SuggestionStripLayout.slotAt(110f, width, height, voiceEnabled = true, slotCount = 3))
        assertNull("the microphone's square is a button", SuggestionStripLayout.slotAt(170f, width, height, voiceEnabled = true, slotCount = 3))
        assertNull(SuggestionStripLayout.slotAt(215f, width, height, voiceEnabled = true, slotCount = 3))
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
        // The window begins at the strip's own left edge in both states. It used to begin one
        // square in while the microphone was on, which is where the hole came from: switch the
        // button off and that square was simply empty. Where the microphone lives now is the first
        // square *inside* the trailing buttons, so its width comes off the right end of the window.
        assertEquals(0f, SuggestionStripLayout.suggestionLeft(48f))
        // And it is the microphone's square that moved to the other end, not the window's left edge.
        assertTrue(SuggestionStripLayout.micLeft(360f, 48f, voiceEnabled = true) > 0f)
    }

    @Test
    fun `the microphone sits inside the buttons at the right, not at the left`() {
        // His ask, as arithmetic: "the mic button should probably be on the inner side of the top
        // bar cuz after making it disappear it leaves a hole". Its square is the first one inside
        // the trailing buttons, and switching it off hands that square to the words rather than
        // leaving a gap where it used to be.
        assertEquals(168f, SuggestionStripLayout.micLeft(360f, 48f, voiceEnabled = true))
        assertEquals(216f, SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = false))
        // Its square is exactly one cell of the window's own width.
        assertEquals(
            48f,
            SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = false) -
                SuggestionStripLayout.micLeft(360f, 48f, voiceEnabled = true),
            0.001f,
        )
        // With the button away there is no seat to hit, and the whole width is the words'.
        assertEquals(360f, SuggestionStripLayout.micLeft(360f, 48f, voiceEnabled = false))
        // The square the microphone leaves is a word's while it is away, and a button's while it is
        // there: the same point on the bar, two different answers, which is the whole point of the
        // seat being inside the trailing buttons rather than off on its own.
        assertEquals(2, SuggestionStripLayout.slotAt(200f, 360f, 48f, voiceEnabled = false, slotCount = 3))
        assertNull(SuggestionStripLayout.slotAt(200f, 360f, 48f, voiceEnabled = true, slotCount = 3))
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

    @Test
    fun `a drag shorter than one cell still moves the words`() {
        val cell = SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true) / 3f

        // The defect in one line: a flick shorter than a cell was rounded down to no movement at
        // all, because the drag travelled in whole cells. This is the property that was missing.
        val shortFlick = SuggestionStripLayout.draggedPx(
            startPx = 0f,
            fingerDownX = 500f,
            fingerX = 500f - cell / 3f,
            maxPx = cell * 5f,
        )
        assertTrue("a flick under one cell must still move the list", shortFlick > 0f)
        assertEquals(cell / 3f, shortFlick, 0.001f)

        // And it follows exactly: one pixel of finger, one pixel of words.
        assertEquals(
            cell * 2f,
            SuggestionStripLayout.draggedPx(0f, 500f, 500f - cell * 2f, cell * 5f),
            0.001f,
        )
    }

    @Test
    fun `a drag cannot pull the list past its ends`() {
        val maxPx = SuggestionStripLayout.maxScrollPx(
            width = 360f,
            height = 48f,
            voiceEnabled = true,
            slotCount = 3,
            count = 8,
        )

        // Five candidates past the three the window holds, so five cells of travel and not more.
        assertEquals(
            5f * SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true) / 3f,
            maxPx,
            0.001f,
        )
        assertEquals("dragging right at the start goes nowhere", 0f, SuggestionStripLayout.draggedPx(0f, 0f, 900f, maxPx), 0.001f)
        assertEquals("dragging left past the end stops at the last candidate", maxPx, SuggestionStripLayout.draggedPx(0f, 900f, 0f, maxPx), 0.001f)
        // Three candidates fit, so there is nothing to scroll to and no travel at all.
        assertEquals(0f, SuggestionStripLayout.maxScrollPx(360f, 48f, true, slotCount = 3, count = 3), 0.001f)
    }

    @Test
    fun `the scrolled window reads the candidate under the finger`() {
        val cell = SuggestionStripLayout.suggestionWidth(360f, 48f, voiceEnabled = true) / 3f
        val leftEdge = SuggestionStripLayout.suggestionLeft(48f) + 1f

        // Scrolled by two whole cells, the left edge of the window is the third candidate: the hit
        // test has to travel with the words, or a tap after a scroll lands on the wrong one.
        assertEquals(0, SuggestionStripLayout.indexAt(leftEdge, 360f, 48f, true, 3, scrollPx = 0f))
        assertEquals(2, SuggestionStripLayout.indexAt(leftEdge, 360f, 48f, true, 3, scrollPx = cell * 2f))
        assertEquals(5, SuggestionStripLayout.indexAt(leftEdge, 360f, 48f, true, 3, scrollPx = cell * 5f))
        // Outside the window is still null, scrolled or not: past its right edge is the
        // microphone's square, which is a button and not a word.
        assertNull(SuggestionStripLayout.indexAt(170f, 360f, 48f, true, 3, scrollPx = cell * 2f))
    }

    @Test
    fun `the left group holds square nought in every state`() {
        val square = 48f

        // The hole, as arithmetic: the clipboard and edit icons are the only controls on the left,
        // so they begin at the strip's own left edge whether the microphone is on or off. They used
        // to begin one square in whenever it was on, which left that square empty the moment it was
        // switched off - a gap at the end of the bar with two icons set in from it.
        assertEquals("the icons begin at the edge with the microphone on", 0f, SuggestionStripLayout.leftGroupStart())
        assertEquals(0f, square * SuggestionStripLayout.leftGroupStart(), 0.001f)
        // And the second control sits next to the first, so no square is skipped between them.
        val second = square * (SuggestionStripLayout.leftGroupStart() + 1f)
        assertEquals(square, second, 0.001f)
        // The microphone is nowhere near this group any more.
        assertTrue(SuggestionStripLayout.micLeft(360f, square, voiceEnabled = true) > second)
    }

    @Test
    fun `a press on the empty part of the window still starts a drag`() {
        // The window runs 0..168 with the microphone on. A press inside it has to be claimed even
        // where no word is drawn, or no move event arrives and the drag never begins; it used to be
        // claimed only over a word.
        assertTrue(SuggestionStripLayout.withinWindow(48f, 360f, 48f, voiceEnabled = true))
        assertTrue(SuggestionStripLayout.withinWindow(160f, 360f, 48f, voiceEnabled = true))
        assertFalse("the microphone's square is a button", SuggestionStripLayout.withinWindow(170f, 360f, 48f, voiceEnabled = true))
        assertFalse("past the window are the mode buttons", SuggestionStripLayout.withinWindow(216f, 360f, 48f, voiceEnabled = true))
        assertFalse(SuggestionStripLayout.withinWindow(300f, 360f, 48f, voiceEnabled = true))
        // With the microphone away the window starts at the strip's edge and the two controls take
        // square nought: a press there is theirs, not the words'.
        assertTrue(SuggestionStripLayout.withinWindow(96f, 360f, 48f, voiceEnabled = false))
    }
}
