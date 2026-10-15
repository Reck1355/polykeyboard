package com.slide.core.theme

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.slide.core.settings.KeyboardSettings
import com.slide.core.settings.toKeyboardSettings
import com.slide.core.settings.writeKeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours feature, as pure arithmetic — no device, no rendering.
 *
 * What matters is asserted here rather than left to the eye: the jade default is untouched, one
 * seed always paints the same board, a different seed paints a different one, a stored choice comes
 * back identical, and the chrome is derived from the board's palette so the two can never disagree.
 */
class ColoursTest {

    private fun custom(hue: Float, saturation: Float, lightness: Float) =
        ColourChoice(Colours.ID_CUSTOM, hue, saturation, lightness)

    // region The jade default is untouched

    @Test
    fun `nothing chosen resolves to today's jade, byte for byte`() {
        val palette = Colours.palette(ColourChoice.Default)

        assertEquals(HexPalette.Jade, palette)
        assertEquals(0xFF0E1417.toInt(), palette.background)
        assertEquals(0xFF1F6F5C.toInt(), palette.key)
        assertEquals(0xFF17564A.toInt(), palette.specialKey)
        assertEquals(0xFFA8452B.toInt(), palette.enterKey)
        assertEquals(0xFFF5F7F6.toInt(), palette.label)
        assertEquals(0x40FFFFFF, palette.pressed)
    }

    @Test
    fun `a fresh install shows jade`() {
        val settings = KeyboardSettings()

        assertEquals(Colours.ID_JADE, settings.colourPresetId)
        assertEquals(HexPalette.Jade, KeyboardColours.resolve(settings.colourChoice).palette)
    }

    // endregion

    // region Determinism

    @Test
    fun `a seed resolves to the same six slots every time, twice over`() {
        val seed = Hsl.hslToColor(210f, 0.6f, 0.3f)

        val first = Colours.paletteFromSeed(seed)
        val second = Colours.paletteFromSeed(seed)

        assertEquals(first, second)
        // The same values the drawing reads, slot for slot — not just data-class equality.
        assertEquals(first.background, second.background)
        assertEquals(first.key, second.key)
        assertEquals(first.specialKey, second.specialKey)
        assertEquals(first.enterKey, second.enterKey)
        assertEquals(first.label, second.label)
        assertEquals(first.pressed, second.pressed)
        // And the same through the path the keyboard actually takes.
        val choice = custom(210f, 0.6f, 0.3f)
        assertEquals(Colours.palette(choice), Colours.palette(choice))
        assertEquals(first, Colours.palette(choice))
    }

    // endregion

    // region Changing the seed changes the palette

    @Test
    fun `a different hue paints a different board`() {
        val teal = Colours.palette(custom(165f, 0.7f, 0.3f))
        val violet = Colours.palette(custom(275f, 0.7f, 0.3f))

        assertNotEquals(teal, violet)
        assertNotEquals(teal.key, violet.key)
        assertNotEquals(teal.specialKey, violet.specialKey)
        assertNotEquals(teal.enterKey, violet.enterKey)
        // Neither is the default, so choosing a seed is never a no-op.
        assertNotEquals(HexPalette.Jade, teal)
        assertNotEquals(HexPalette.Jade, violet)
    }

    @Test
    fun `moving the lightness slider moves the palette`() {
        val dark = Colours.palette(custom(165f, 0.7f, 0.2f))
        val light = Colours.palette(custom(165f, 0.7f, 0.8f))

        assertNotEquals(dark, light)
        assertNotEquals(dark.key, light.key)
    }

    // endregion

    // region Persistence

    @Test
    fun `a custom choice stored then loaded comes back identical`() {
        val stored = KeyboardSettings(
            colourPresetId = Colours.ID_CUSTOM,
            colourSeedHue = 275f,
            colourSeedSaturation = 0.8f,
            colourSeedLightness = 0.35f,
        )

        val written = mutablePreferencesOf()
        written.writeKeyboardSettings(stored)
        val loaded = written.toPreferences().toKeyboardSettings()

        assertEquals(stored.colourChoice, loaded.colourChoice)
        assertEquals(stored.colourPresetId, loaded.colourPresetId)
        assertEquals(stored.colourSeedHue, loaded.colourSeedHue, 0f)
        assertEquals(stored.colourSeedSaturation, loaded.colourSeedSaturation, 0f)
        assertEquals(stored.colourSeedLightness, loaded.colourSeedLightness, 0f)
        // The palette a restart would draw is the palette that was chosen.
        assertEquals(Colours.palette(stored.colourChoice), Colours.palette(loaded.colourChoice))
    }

    @Test
    fun `a preset choice stored then loaded comes back identical`() {
        val stored = KeyboardSettings(colourPresetId = "forest")

        val written = mutablePreferencesOf()
        written.writeKeyboardSettings(stored)
        val loaded = written.toPreferences().toKeyboardSettings()

        assertEquals("forest", loaded.colourPresetId)
        assertEquals(Colours.palette(stored.colourChoice), Colours.palette(loaded.colourChoice))
    }

    // endregion

    // region One palette, no second colour path

    @Test
    fun `the board and the chrome resolve the same palette object`() {
        val colours = KeyboardColours.resolve(custom(30f, 0.6f, 0.35f))

        // Every chrome slot the task names is the palette's own value: the trail and the popup
        // selection are the palette's Enter, the popups sit on its key, the search header wears its
        // background. If this holds, a colour change cannot repaint half the keyboard.
        assertEquals(colours.palette.enterKey, colours.chrome.gestureTrail)
        assertEquals(colours.palette.enterKey, colours.chrome.accentBackground)
        assertEquals(colours.palette.enterKey, colours.chrome.popupSelectedBackground)
        assertEquals(colours.palette.key, colours.chrome.popupBackground)
        assertEquals(colours.palette.key, colours.chrome.keyBackground)
        assertEquals(colours.palette.specialKey, colours.chrome.specialKeyBackground)
        assertEquals(colours.palette.background, colours.chrome.background)
        assertEquals(colours.palette.label, colours.chrome.popupText)
        assertEquals(colours.palette.pressed, colours.chrome.keyPressedOverlay)

        // The chrome is a pure function of the palette — there is no chrome resolved separately.
        assertEquals(colours.chrome, colours.palette.chromeTheme())
        assertEquals(colours.chrome, KeyboardColours.of(colours.palette).chrome)

        // One object, handed to both: the board's palette and the chrome's source are the same.
        val samePalette = KeyboardColours.of(colours.palette)
        assertSame(colours.palette, samePalette.palette)
        assertEquals(colours.chrome, samePalette.chrome)
    }

    @Test
    fun `changing the colour moves the board and the chrome together`() {
        val first = KeyboardColours.resolve(custom(30f, 0.6f, 0.35f))
        val second = KeyboardColours.resolve(custom(275f, 0.6f, 0.35f))

        // The board moved...
        assertNotEquals(first.palette, second.palette)
        //... and so did the chrome, because it was never resolved on its own.
        assertNotEquals(first.chrome.gestureTrail, second.chrome.gestureTrail)
        assertNotEquals(first.chrome.popupBackground, second.chrome.popupBackground)
        assertNotEquals(first.chrome.background, second.chrome.background)
    }

    // endregion

    // region The picker offers every preset

    @Test
    fun `jade is first and every built-in preset is offered`() {
        assertEquals(Colours.ID_JADE, Colours.presets.first().id)
        assertEquals(1 + Themes.presets.size, Colours.presets.size)
        assertTrue(Colours.presets.map { it.id }.containsAll(Themes.presets.map { it.id }))

        val forest = Colours.byId("forest")
        assertTrue(forest != null)
        assertEquals(forest!!.palette, Colours.palette(ColourChoice("forest")))
    }

    @Test
    fun `an unknown preset id falls back to jade rather than throwing`() {
        assertEquals(HexPalette.Jade, Colours.palette(ColourChoice("no-such-preset")))
    }

    // endregion
}
