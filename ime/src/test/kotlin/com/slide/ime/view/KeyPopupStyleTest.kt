package com.slide.ime.view

import com.slide.core.theme.HexPalette
import com.slide.core.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which colours the press preview is drawn in on each board.
 *
 * One window serves both boards, so the difference is data, and this is the setting that was broken
 * on the glass: the preview itself is board-agnostic and did run on the honeycomb, but it was handed
 * the classic theme's colours, which sit close enough to the board's own jade to read as nothing
 * happening. The hex version comes from the palette, and its stroke is the board's background so the
 * popup keeps an edge instead of relying on elevation alone.
 */
class KeyPopupStyleTest {

    @Test
    fun `the hex preview is drawn from the board's own palette`() {
        val colours = KeyPopupStyle.coloursFor(hexBoard = true, palette = HexPalette.Jade, theme = Themes.Dark)
        assertEquals(HexPalette.Jade.specialKey, colours.background)
        assertEquals(HexPalette.Jade.label, colours.text)
        assertEquals(HexPalette.Jade.background, colours.border)
    }

    @Test
    fun `the classic preview keeps the theme it always had`() {
        val colours = KeyPopupStyle.coloursFor(hexBoard = false, palette = HexPalette.Jade, theme = Themes.Dark)
        assertEquals(Themes.Dark.popupBackground, colours.background)
        assertEquals(Themes.Dark.popupText, colours.text)
        assertEquals(Themes.Dark.keyBorder, colours.border)
    }

    @Test
    fun `the hex preview stands off the board behind it`() {
        val colours = KeyPopupStyle.coloursFor(hexBoard = true, palette = HexPalette.Jade, theme = Themes.Dark)
        assertNotEquals(HexPalette.Jade.key, colours.background)
        assertNotEquals(colours.background, colours.border)
        assertNotEquals(colours.border, colours.text)
    }

    @Test
    fun `only the board decides, so the same board always gets the same colours`() {
        val first = KeyPopupStyle.coloursFor(hexBoard = true, palette = HexPalette.Jade, theme = Themes.Light)
        val second = KeyPopupStyle.coloursFor(hexBoard = true, palette = HexPalette.Jade, theme = Themes.Dark)
        assertEquals(first, second)
    }
}
