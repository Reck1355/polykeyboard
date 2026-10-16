package com.slide.core.settings

/**
 * The one stock control the hex board cannot answer, and the reason it cannot.
 *
 * "Number row" prepends a row of digits to the rectangular boards. The hex board has no such row and
 * cannot grow one: its rows are columns of hexagons, so its digits live behind `?123`. The control
 * used to sit beside the ones that do work with nothing to say it was inert, which is how a stored
 * setting came to look broken.
 *
 * "Keyboard height" was the other half of this group and has left it. A hexagon has no row height to
 * stretch, but the whole board still has a size, so that setting now scales the honeycomb into a
 * shorter or taller frame and is a live control on both boards.
 *
 * The rule is here rather than in either settings surface because there are two of them, the app's
 * screen and the keyboard's own panel, and a rule written twice is a rule that will disagree with
 * itself. Nothing is deleted from either surface: when the classic keyboard lands behind its toggle,
 * the flag below flips and the control comes alive with no other change.
 */
object ClassicKeyboard {

    /** The heading both surfaces give the group. */
    const val GROUP_TITLE = "Classic keyboard"

    /**
     * What both surfaces say under that heading.
     *
     * It names the key that does reach the hex board's digits, because a control that is off without
     * saying which one to use instead is only half a fix.
     */
    const val HEX_BOARD_REASON =
        "The hex board has no number row: its digits are behind ?123. This control applies to the " +
            "classic keyboard."

    /**
     * Whether the stock keyboard's number row applies, given the board on screen.
     *
     * [hexBoard] decides on its own, so the rule is testable in both directions before the toggle
     * that will use it exists.
     */
    fun controlsApply(hexBoard: Boolean = HEX_BOARD_IS_THE_BOARD): Boolean = !hexBoard

    /**
     * True today, and it is the honest whole of the state: every field the keyboard answers gets a
     * hex board, text, email and URL alike.
     */
    const val HEX_BOARD_IS_THE_BOARD: Boolean = true
}
