package com.slide.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Keyboard height" and "Number row" are the stock keyboard's controls. The hex board does not read
 * either one, because its height comes from [KeyboardSize]'s ladder and its digits are behind `?123`
 * — the hex branch of the view says so in as many words. They used to sit in the same list as the
 * control that does resize the board, which is why the height slider read as broken rather than
 * inapplicable.
 *
 * What is pinned here is the rule and the sentence, not the pixels: the rule decides in both
 * directions (so the parked classic-keyboard toggle cannot be wired backwards), the surfaces cannot
 * drift from the state they describe, and the reason names the control and the key a user would
 * otherwise have to guess. The wiring into the two surfaces is a compile-time fact — both read these
 * constants — and the look of it is the app's, not this test's.
 */
class ClassicKeyboardTest {

    @Test
    fun `the stock controls do not apply to the hex board and do to the classic one`() {
        assertFalse(ClassicKeyboard.controlsApply(hexBoard = true))
        assertTrue(ClassicKeyboard.controlsApply(hexBoard = false))
    }

    @Test
    fun `the default follows the board the build actually draws`() {
        assertTrue(ClassicKeyboard.HEX_BOARD_IS_THE_BOARD)
        assertEquals(
            ClassicKeyboard.controlsApply(hexBoard = ClassicKeyboard.HEX_BOARD_IS_THE_BOARD),
            ClassicKeyboard.controlsApply(),
        )
    }

    @Test
    fun `the reason names where the hex board's height and digits come from`() {
        val reason = ClassicKeyboard.HEX_BOARD_REASON
        assertTrue("names the control that does resize it", reason.contains("Keyboard size"))
        assertTrue("names the key that reaches the digits", reason.contains("?123"))
        assertTrue("says which keyboard it applies to", reason.contains("classic keyboard"))
    }

    @Test
    fun `the group has a title and does not borrow the settings screen's own card name`() {
        assertEquals("Classic keyboard", ClassicKeyboard.GROUP_TITLE)
    }
}
