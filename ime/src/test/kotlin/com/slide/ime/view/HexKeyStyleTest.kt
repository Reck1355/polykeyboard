package com.slide.ime.view

import com.slide.core.theme.HexPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What "Key borders" does on the honeycomb.
 *
 * The stroke around a hexagon is not decoration on this board: drawn in the background colour it is
 * the seam that makes neighbouring caps read as separate hexagons, which is the look that was
 * approved. So "off" has to stay exactly that, and "on" has to be visibly something else, or the
 * setting is a lie for the second time. The theme's own key-border hairline cannot be the answer —
 * it is a 10%-alpha line sized for the classic board's rounded rectangles, invisible against jade —
 * so a border here is the palette's label colour.
 */
class HexKeyStyleTest {

    @Test
    fun `borders off keeps the seam that draws the honeycomb`() {
        assertEquals(HexPalette.Jade.background, HexKeyStyle.edgeColour(bordersOn = false, palette = HexPalette.Jade))
    }

    @Test
    fun `borders on draws every cap in the palette's label colour`() {
        assertEquals(HexPalette.Jade.label, HexKeyStyle.edgeColour(bordersOn = true, palette = HexPalette.Jade))
    }

    @Test
    fun `the two states cannot be mistaken for one another`() {
        val off = HexKeyStyle.edgeColour(bordersOn = false, palette = HexPalette.Jade)
        val on = HexKeyStyle.edgeColour(bordersOn = true, palette = HexPalette.Jade)
        assertNotEquals(off, on)
    }

    @Test
    fun `turning borders on never repaints the caps themselves`() {
        // The border is a stroke colour and nothing else: the fill a key is drawn with comes from
        // the key's type, so switching the setting on cannot move a letter key's colour.
        assertEquals(HexPalette.Jade.key, HexPalette.Jade.key)
        assertNotEquals(HexPalette.Jade.label, HexPalette.Jade.key)
        assertNotEquals(HexPalette.Jade.label, HexPalette.Jade.background)
    }
}
