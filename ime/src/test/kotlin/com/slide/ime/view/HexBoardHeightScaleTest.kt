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
        // It asks; what frame it actually gets is another matter, and [HexBoard.frameHeight] is what
        // answers that. This pins only the arithmetic of the ask.
        assertEquals(capped * 1.4f, HexBoard.scaledBoardHeight(capped, 1.4f), 0.0001f)
    }

    @Test
    fun `a frame is never taller than the block that will be drawn in it`() {
        // His report: "the height doesn't actually make the keys taller past 100 just moves keyboard
        // up". Width binds in portrait, so past the approved block no height control can make the
        // hexagons larger: the extra height bought an empty band above the board, while the board
        // placement control is what moves the board. The frame now stops at the block.
        assertEquals(capped, HexBoard.frameHeight(capped, 1f), 0.0001f)
        assertEquals(capped, HexBoard.frameHeight(capped, 1.4f), 0.0001f)
        assertEquals(capped, HexBoard.frameHeight(capped, 5f), 0.0001f)
        // Shrinking is untouched: a shorter frame is a genuinely smaller board.
        assertEquals(capped * 0.7f, HexBoard.frameHeight(capped, 0.7f), 0.0001f)
    }

    @Test
    fun `the size ladder is what resizes the portrait board, not the height slider`() {
        // The ladder spends itself on the hexagon ratio, the one axis portrait has left once the
        // width is spent. That is why moving it resizes the keys while the height slider above 100
        // per cent cannot, and why the two controls are not interchangeable.
        val small = HexBoard.cappedBoardHeight(1080f, 2340f, HexBoard.sizing(HexBoard.Orientation.PORTRAIT, -2))
        val large = HexBoard.cappedBoardHeight(1080f, 2340f, HexBoard.sizing(HexBoard.Orientation.PORTRAIT, 2))
        assertTrue("the ladder must actually change the board", large > small)
        assertEquals(large, HexBoard.frameHeight(large, 1.4f), 0.0001f)
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
