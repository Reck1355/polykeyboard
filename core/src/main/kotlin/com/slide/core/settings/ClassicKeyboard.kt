package com.slide.core.settings

/**
 * The stock keyboard's two controls, and the one reason they are off while the hex board is up.
 *
 * "Keyboard height" scales the rectangular boards' row height and "Number row" prepends a row of
 * digits to them. The hex board does neither, and cannot: its height comes from [KeyboardSize]'s
 * own ladder and its digits live behind `?123`, because a shape whose proportion is fixed has no
 * spare height to give. Both controls used to sit beside the one that does work with nothing to say
 * they were inert, which is how a working slider came to look broken.
 *
 * The rule is here rather than in either settings surface because there are two of them, the app's
 * screen and the keyboard's own panel, and a rule written twice is a rule that will disagree with
 * itself. Nothing is deleted from either surface: when the classic keyboard lands behind its toggle,
 * the flag below flips and both controls come alive with no other change.
 */
object ClassicKeyboard {

    /** The heading both surfaces give the group. */
    const val GROUP_TITLE = "Classic keyboard"

    /**
     * What both surfaces say under that heading.
     *
     * It names the control that does resize the hex board and the key that does reach its digits,
     * because a control that is off without saying which one to use instead is only half a fix.
     */
    const val HEX_BOARD_REASON =
        "The hex board draws its own shape: its height comes from Keyboard size, and its digits are " +
            "behind ?123. These two apply to the classic keyboard."

    /**
     * Whether the stock keyboard's controls apply, given the board on screen.
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
