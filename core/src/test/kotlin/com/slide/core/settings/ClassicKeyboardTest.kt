package com.slide.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Number row" is the one stock control the hex board cannot answer. The hex board has no such row
 * and cannot grow one — its rows are columns of hexagons — so its digits are behind `?123`, and the
 * hex branch of the view builds no number row whatever the setting says.
 *
 * "Keyboard height" used to be pinned here beside it and has left: a hexagon has no row height to
 * stretch, but the board still has a size, so that setting now scales the honeycomb into a shorter
 * or taller frame. [HexBoardHeightScaleTest] guards that arithmetic.
 *
 * What is pinned here is the rule and the sentence, not the pixels: the rule decides in both
 * directions (so the parked classic-keyboard toggle cannot be wired backwards), the surfaces cannot
 * drift from the state they describe, and the reason names the key a user would otherwise have to
 * guess. The wiring into the two surfaces is a compile-time fact — both read these constants — and
 * the look of it is the app's, not this test's.
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
    fun `the reason names the key that reaches the hex board's digits`() {
        val reason = ClassicKeyboard.HEX_BOARD_REASON
        assertTrue("names the key that reaches the digits", reason.contains("?123"))
        assertTrue("says which keyboard it applies to", reason.contains("classic keyboard"))
    }

    @Test
    fun `the reason no longer claims the height setting is inert`() {
        // The height setting scales the honeycomb, so a sentence saying its height comes from
        // somewhere else would be the old lie told in the other direction.
        assertFalse(
            "does not send the height slider somewhere else",
            ClassicKeyboard.HEX_BOARD_REASON.contains("Keyboard size"),
        )
    }

    @Test
    fun `the group has a title and does not borrow the settings screen's own card name`() {
        assertEquals("Classic keyboard", ClassicKeyboard.GROUP_TITLE)
    }
}
