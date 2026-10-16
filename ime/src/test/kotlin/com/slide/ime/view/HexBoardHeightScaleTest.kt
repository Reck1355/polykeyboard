package com.slide.ime.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Keyboard height" on the honeycomb, which is a scale on the board rather than a row height.
 *
 * A hexagon has one proportion and no row to stretch, so the setting moves the frame the board is
 * measured into and the geometry scales the whole honeycomb into it — the shape is never what
 * changes. The case that matters most is the default of 1: every approved number (the landscape cap,
 * the 157.4 px cell, the two five-cell halves, the 668.5 px channel) is read off the capped height,
 * so a scale of 1 has to leave that height untouched, byte for byte.
 */
class HexBoardHeightScaleTest {

    private val sizing = HexBoard.sizing(HexBoard.Orientation.PORTRAIT, 0)
    private val capped = HexBoard.cappedBoardHeight(1080f, 2340f, sizing)

    @Test
    fun `the default scale leaves the capped board exactly as it was approved`() {
        assertEquals(capped, HexBoard.scaledBoardHeight(capped, 1f), 0.0001f)
    }

    @Test
    fun `a lower stop asks for a shorter board`() {
        assertEquals(capped * 0.7f, HexBoard.scaledBoardHeight(capped, 0.7f), 0.0001f)
    }

    @Test
    fun `a higher stop asks for a taller board`() {
        assertEquals(capped * 1.4f, HexBoard.scaledBoardHeight(capped, 1.4f), 0.0001f)
    }

    @Test
    fun `the scale is the whole of the difference, so the ladder still decides the base`() {
        // Two stops of the size ladder, each scaled by the same control, stay in the ladder's order.
        val small = HexBoard.sizing(HexBoard.Orientation.PORTRAIT, -2)
        val large = HexBoard.sizing(HexBoard.Orientation.PORTRAIT, 2)
        val scaledSmall = HexBoard.scaledBoardHeight(HexBoard.cappedBoardHeight(1080f, 2340f, small), 0.8f)
        val scaledLarge = HexBoard.scaledBoardHeight(HexBoard.cappedBoardHeight(1080f, 2340f, large), 1.2f)
        assertTrue(scaledSmall < scaledLarge)
    }
}
