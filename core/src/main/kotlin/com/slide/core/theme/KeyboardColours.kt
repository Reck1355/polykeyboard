package com.slide.core.theme

/**
 * The chrome derived from one palette.
 *
 * The gesture trail, the key-preview popups and the emoji-search header all read [KeyboardTheme].
 * Rather than resolve a theme of their own, they are painted from this function, which fills every
 * chrome slot out of the palette's six — so the trail is the palette's accent, the popups sit on the
 * palette's key, the header wears the palette's background, and none of them can be left wearing a
 * colour the board is not.
 *
 * Pure, so the "the board and the chrome are one palette" promise is checkable off-device.
 */
fun HexPalette.chromeTheme(): KeyboardTheme {
    val dark = Hsl.isDark(background)
    // The hairline that keeps popups and keycaps legible: the label colour, barely there.
    val hairline = Hsl.withAlpha(label, if (dark) 0x33 else 0x1A)

    return KeyboardTheme(
        id = CHROME_ID,
        name = "Colours",
        isDark = dark,
        background = background,
        keyBackground = key,
        specialKeyBackground = specialKey,
        accentBackground = enterKey,
        keyText = label,
        specialKeyText = label,
        accentText = label,
        hintText = Hsl.withAlpha(label, 0x99),
        keyBorder = hairline,
        keyShadow = if (dark) 0x66000000 else 0x30000000,
        keyPressedOverlay = pressed,
        // The chrome's own accents are the palette's: the trail and the popup selection are the
        // colour of Enter, so they move the moment the board does.
        gestureTrail = enterKey,
        popupBackground = key,
        popupText = label,
        popupSelectedBackground = enterKey,
        suggestionText = label,
        suggestionHighlightText = enterKey,
        divider = hairline,
    )
}

/**
 * One resolved colour set: the six slots the hex board draws, and the chrome derived from them.
 *
 * This is the single object the keyboard is painted from. A surface is handed either [palette]
 * (the board) or [chrome] (the trail, popups, header, panels), and [chrome] is a pure function of
 * [palette] — so there is exactly one colour path, and a change cannot repaint half the keyboard.
 */
data class KeyboardColours(
    val palette: HexPalette,
    val chrome: KeyboardTheme,
) {
    companion object {
        fun of(palette: HexPalette): KeyboardColours = KeyboardColours(palette, palette.chromeTheme())

        fun resolve(choice: ColourChoice): KeyboardColours = of(Colours.palette(choice))
    }
}

private const val CHROME_ID = "colours"
