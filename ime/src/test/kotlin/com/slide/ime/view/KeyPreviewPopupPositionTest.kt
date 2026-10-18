package com.slide.ime.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the key-preview popup lands on the display.
 *
 * The popup positions itself with `PopupWindow.showAtLocation`, which works in **screen**
 * coordinates, while the board places its keys in the **view's** own space. The first version read the
 * anchor's window origin, mixed the two, and parked the preview one keyboard-and-status-bar height
 * above the finger — off the display — so the feature was reported as "does nothing" while every
 * unit of geometry in it was correct. These tests pin the one thing that was wrong: the view's own
 * origin on screen has to be added, and a test that feeds a zero origin cannot see the difference.
 */
class KeyPreviewPopupPositionTest {

    /** A portrait phone: the keyboard view begins 1800 px down a 1080 px wide display. */
    private val viewScreenY = 1800
    private val viewWidth = 1080

    @Test
    fun `the preview sits just above the key it belongs to`() {
        val (x, y) = previewPopupPosition(
            viewScreenX = 0,
            viewScreenY = viewScreenY,
            viewWidth = viewWidth,
            keyCenterX = 300f,
            keyTop = 1700f,
            popupWidth = 80,
            popupHeight = 60,
            marginPx = 12,
        )

        assertEquals("centred on the key", 260, x)
        assertEquals("above the key, offset by the view's own origin on screen", 1800 + 1700 - 60 - 12, y)
    }

    @Test
    fun `the view's origin on screen is added, which is what the broken version missed`() {
        // The same key, two different placements of the keyboard on screen. The old arithmetic
        // produced the SAME y for both — it treated the view's origin as the display's — and that is
        // the bug in one line: a preview that ignores where the keyboard actually is.
        val high = previewPopupPosition(0, 800, viewWidth, 300f, 700f, 80, 60, 12).second
        val low = previewPopupPosition(0, 1800, viewWidth, 300f, 700f, 80, 60, 12).second

        assertEquals(800 + 700 - 72, high)
        assertEquals(1800 + 700 - 72, low)
        assertTrue("the two must differ by exactly the keyboard's shift on screen", low - high == 1000)
    }

    @Test
    fun `a key at either edge keeps the popup inside the view`() {
        val left = previewPopupPosition(0, viewScreenY, viewWidth, 2f, 900f, 80, 60, 12).first
        val right = previewPopupPosition(0, viewScreenY, viewWidth, 1078f, 900f, 80, 60, 12).first

        assertEquals("the leftmost key does not push it off the left edge", 0, left)
        assertEquals("the rightmost key does not push it off the right edge", viewWidth - 80, right)
    }

    @Test
    fun `a popup wider than the view is pinned to the left edge rather than mirrored`() {
        val x = previewPopupPosition(0, viewScreenY, 100, 50f, 900f, 400, 60, 12).first
        assertEquals(0, x)
    }
}
