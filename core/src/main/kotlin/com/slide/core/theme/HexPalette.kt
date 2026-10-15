package com.slide.core.theme

/**
 * The six colours the hex board draws with, and nothing else.
 *
 * Deliberately not a [KeyboardTheme]: the rest of the keyboard resolves eighteen colours from one
 * theme, and the hex rendering slice needs six. Keeping them separate means the board can be
 * repainted without dragging the strip, the popups or the voice UI along, and it keeps this slice
 * honest about which values the drawing actually reads.
 *
 * The point of gathering them here is the next slice, not this one: a user-settable hex palette
 * becomes "fill these six fields from settings" without the drawing code changing at all. That is
 * why the defaults live on [Jade] rather than at the call site.
 *
 * One board, one instance — [KeyboardTheme]'s sibling for the honeycomb.
 */
data class HexPalette(
    /** Behind the board, between the caps and inside them where the seams are painted out. */
    val background: Int,
    /** A letter or the space bar. */
    val key: Int,
    /** Shift, delete, `?123`, emoji and the globe. */
    val specialKey: Int,
    /** Enter, which earns its own colour because it is the one key that ends a thought. */
    val enterKey: Int,
    /** Every glyph: letters, action icons and the space bar's caption. */
    val label: Int,
    /**
     * Drawn over a cap while it is held, and the accent a future slice may key on.
     *
     * Translucent on purpose — it is an overlay on the key's own colour, so one value works over
     * the normal, special and enter fills alike.
     */
    val pressed: Int,
) {
    companion object {
        /** Jacky's jade defaults, signed off 2026-10-16. */
        val Jade = HexPalette(
            background = 0xFF0E1417.toInt(),
            key = 0xFF1F6F5C.toInt(),
            specialKey = 0xFF17564A.toInt(),
            enterKey = 0xFFA8452B.toInt(),
            label = 0xFFF5F7F6.toInt(),
            pressed = 0x40FFFFFF,
        )
    }
}
