package com.slide.ime.view

import com.slide.core.layout.KeyboardLanguages
import com.slide.core.layout.KeyboardLayout
import com.slide.core.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the honeycomb's digit row does to the geometry, in both orientations.
 *
 * The row was added to the layout without teaching the board about it. The board places a layout into a
 * content box sized for the rows a honeycomb used to have, so on a fifth row it had two choices: overrun
 * the box, or squeeze the rows to fit the box. It did whichever the orientation made it do — landscape
 * grew by exactly one row and drew past the frame, portrait squashed every key from 121.5 px to 97.2 — and
 * neither is a keyboard that can be typed on. These are the numbers that were measured before the fix, so
 * they are the numbers a regression has to break.
 */
class HexNumberRowGeometryTest {

    private val portrait = HexBoard.sizing(HexBoard.Orientation.PORTRAIT)
    private val landscape = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE)

    private fun boards(): KeyboardLayout = Layouts.boardsFor(KeyboardLanguages.FALLBACK).hex

    private fun placed(layout: KeyboardLayout, w: Float, h: Float, s: HexBoard.Sizing) =
        HexBoard.place(layout, w, h, s)

    @Test
    fun `portrait gains a row without shrinking a single key`() {
        val without = placed(boards(), 1080f, 2400f, portrait)
        val with = placed(Layouts.withNumberRowForHoneycomb(boards(), true, portrait = true), 1080f, 2400f, portrait)

        assertEquals("the cell is decided by the width and must not move", without.cellWidth, with.cellWidth, 0.01f)
        assertEquals("ten rows of keys", without.keys.size + 10, with.keys.size)
        // The whole point: a fifth row is a fifth row, not five rows' worth of squeeze into four rows' room.
        assertEquals(
            "every key keeps the height it had before the digit row arrived",
            without.keys.first().height,
            with.keys.first().height,
            0.01f,
        )
        // and the board is exactly one key height taller, not more and not less
        assertEquals(
            "the board grows by one key height",
            without.keys.first().height,
            (with.keys.maxOf { it.bottom } - with.keys.minOf { it.top }) -
                (without.keys.maxOf { it.bottom } - without.keys.minOf { it.top }),
            0.01f,
        )
    }

    @Test
    fun `the landscape board is exactly the board it was before the digit row`() {
        val plain = placed(boards(), 2400f, 1080f, landscape)
        val effective = Layouts.withNumberRowForHoneycomb(boards(), true, portrait = false)
        assertSame("landscape is handed the board unchanged", boards(), effective)
        val after = placed(effective, 2400f, 1080f, landscape)

        assertEquals("same cell", plain.cellWidth, after.cellWidth, 0.01f)
        assertEquals("same keys", plain.keys.size, after.keys.size)
        assertEquals(
            "same drawn height, top and bottom",
            listOf(plain.keys.minOf { it.top }, plain.keys.maxOf { it.bottom }),
            listOf(after.keys.minOf { it.top }, after.keys.maxOf { it.bottom }),
        )
        assertTrue(
            "the board still fits the block it was measured into",
            after.keys.maxOf { it.bottom } <= after.boardHeight + after.keys.first().height,
        )
    }

    @Test
    fun `a taller block is what keeps the keys their size, in portrait`() {
        val fourRows = HexBoard.blockCapFactor(4)
        val fiveRows = HexBoard.blockCapFactor(5)
        // 3 pitches and a cap for four rows — the approved 3.25, unchanged — and one pitch more for five.
        assertEquals(3.25f, fourRows, 0.001f)
        assertEquals(fourRows + HexBoard.PITCH_CAP_FRACTION, fiveRows, 0.001f)
    }
}
