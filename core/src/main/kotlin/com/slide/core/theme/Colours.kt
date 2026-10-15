package com.slide.core.theme

/** One pickable palette in the Colours screen. */
data class ColourPreset(
    val id: String,
    val name: String,
    val palette: HexPalette,
)

/**
 * The user's colour choice, exactly as persisted.
 *
 * A named [presetId] is the whole choice most of the time; the seed triple only matters when
 * [presetId] is [Colours.ID_CUSTOM], where the hue/saturation strip and the lightness slider have
 * left a seed the palette is derived from.
 */
data class ColourChoice(
    val presetId: String = Colours.ID_JADE,
    /** Custom seed hue, 0..360. */
    val seedHue: Float = Colours.DEFAULT_SEED_HUE,
    /** Custom seed saturation, 0..1. */
    val seedSaturation: Float = Colours.DEFAULT_SEED_SATURATION,
    /** Custom seed lightness, 0..1. */
    val seedLightness: Float = Colours.DEFAULT_SEED_LIGHTNESS,
) {
    /** The custom seed as an opaque ARGB colour, through the same pure HSL machinery as everything. */
    val seedColor: Int get() = Hsl.hslToColor(seedHue, seedSaturation, seedLightness)

    companion object {
        /** A blank choice: the jade default, untouched. */
        val Default = ColourChoice()
    }
}

/**
 * The keyboard's colours: one palette per choice, and the board's six slots are the only source.
 *
 * Every preset — jade, and each built-in theme — is reduced to the same [HexPalette] the hex board
 * draws with, and the chrome is derived back out of that palette by [KeyboardTheme]'s
 * [chromeTheme]. So a colour change moves the board and the trail, popups and search header
 * together, because there is only ever one set of six slots for either to read.
 *
 * The custom seed goes through [Themes.solidTheme] rather than a bespoke recipe: it is the same
 * seed-derivation machinery the built-in presets use, applied to a seed the user chose.
 */
object Colours {

    /** The default palette's id, and the value a fresh install resolves to. */
    const val ID_JADE = "jade"

    /** The id for a user-chosen seed colour. */
    const val ID_CUSTOM = "custom"

    /** A jade-ish seed the custom controls open on, so the first drag is not a surprise. */
    const val DEFAULT_SEED_HUE = 165f
    const val DEFAULT_SEED_SATURATION = 0.45f
    const val DEFAULT_SEED_LIGHTNESS = 0.24f

    const val HUE_MIN = 0f
    const val HUE_MAX = 360f

    /**
     * Every pickable palette, jade first — the default — then each built-in theme preset. Built
     * once through [paletteFor], so the picker and the keyboard resolve the same six slots.
     */
    val presets: List<ColourPreset> = buildList {
        add(ColourPreset(ID_JADE, "Jade", HexPalette.Jade))
        Themes.presets.forEach { theme ->
            add(ColourPreset(theme.id, theme.name, paletteFor(theme)))
        }
    }

    fun byId(id: String): ColourPreset? = presets.firstOrNull { it.id == id }

    /** Resolves a stored choice to the six slots every keyboard surface draws with. */
    fun palette(choice: ColourChoice): HexPalette = when (choice.presetId) {
        // Jade is the hand-tuned default, not a derivation: selecting it returns the exact values
        // the board shipped with, which is what makes "fresh install shows jade" byte-identical.
        ID_JADE -> HexPalette.Jade
        ID_CUSTOM -> paletteFromSeed(choice.seedColor)
        else -> byId(choice.presetId)?.palette ?: HexPalette.Jade
    }

    /**
     * Derives a whole palette from one seed colour through the existing seed-derivation machinery.
     *
     * Pure and deterministic: the same seed always yields the same six slots, so it can be asserted
     * in a unit test and re-checked on the device.
     */
    fun paletteFromSeed(seed: Int): HexPalette =
        paletteFor(
            Themes.solidTheme(
                id = ID_CUSTOM,
                name = "Custom",
                seed = seed,
                dark = Hsl.isDark(seed),
            ),
        )

    /** The six slots a built theme hands to the board, taken from that theme's own key colours. */
    private fun paletteFor(theme: KeyboardTheme): HexPalette = HexPalette(
        background = theme.background,
        key = theme.keyBackground,
        specialKey = theme.specialKeyBackground,
        enterKey = theme.accentBackground,
        label = theme.keyText,
        pressed = theme.keyPressedOverlay,
    )
}
