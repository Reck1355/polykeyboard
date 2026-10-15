package com.slide.ime.view

import com.slide.core.layout.Layouts
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's height cap, as pure measurements — no device.
 *
 * The gap this closes: nothing capped the IME's height, so the keyboard view self-sized to the
 * board's natural 900 px block on a 2400 px wide landscape screen and the board filled the width
 * with no side margins — not the single uniformly scaled board that was approved. The approved
 * landscape render is a 594 px block, 55% of the 1080 px screen, giving 158.28 px cells with the
 * leftover width deliberately empty; portrait's natural block is 405 px of a 2400 px screen, well
 * under the cap, so the cap has to be a no-op there.
 *
 * The screen the keyboard is drawn on is the viewport here: an IME keyboard spans the display's
 * width, and the cap is a share of the display's height. Every expectation is derived from the frozen
 * [HexBoard.LANDSCAPE_HEIGHT_FRACTION] and [HexBoard.BLOCK_HEIGHT_IN_CELLS] rather than restated, so
 * a drifted fraction or a stretched hexagon fails here instead of shipping.
 */
class HexBoardHeightCapTest {

    /** The whole sizing path for a screen [displayWidth] x [displayHeight], reduced to its numbers. */
    private data class Measurement(
        val cellWidth: Float,
        val blockHeight: Float,
        val leftInset: Float,
        val rightBand: Float,
        val scale: Float,
        val capRatio: Float,
        val availableHeight: Float,
        val heightBinds: Boolean,
    )

    private fun measure(displayWidth: Float, displayHeight: Float): Measurement {
        val available = HexBoard.cappedBoardHeight(displayWidth, displayHeight)
        val placement = HexBoard.place(Layouts.HexQwertyEn, displayWidth, available)
        return Measurement(
            cellWidth = placement.cellWidth,
            blockHeight = placement.boardHeight,
            leftInset = placement.leftInset,
            rightBand = displayWidth - placement.boardWidth - placement.leftInset,
            scale = placement.scale,
            capRatio = HexBoard.capHeight(HexBoard.cellHeight(placement.cellWidth)) / placement.cellWidth,
            availableHeight = available,
            heightBinds = HexBoard.heightBinds(displayWidth, available),
        )
    }

    @Test
    fun `portrait is width-governed and its natural block sits far under the cap`() {
        val displayHeight = 2400f
        val cap = HexBoard.heightCap(displayHeight)
        val naturalBlock = HexBoard.cappedBoardHeight(1080f, displayHeight)

        // The cap is a no-op in portrait: the board's own natural block is what comes back.
        assertEquals(HexBoard.boardHeight(1080f), naturalBlock, 0.001f)
        // And it is a small share of the cap, nowhere near it — 405 px against a 1320 px cap.
        assertTrue("portrait block $naturalBlock vs cap $cap", naturalBlock < cap * 0.5f)

        val portrait = measure(1080f, displayHeight)
        // Width governs: ten 108 px cells span the 1080 px width exactly; the cap never binds.
        assertFalse("the cap must not bind in portrait", portrait.heightBinds)
        assertEquals(108f, portrait.cellWidth, 0.01f)
        // It fills the width, so there is no side margin and it is drawn at full scale.
        assertEquals(0f, portrait.leftInset, 0.01f)
        assertEquals(0f, portrait.rightBand, 0.01f)
        assertEquals(1f, portrait.scale, 0.001f)
        // And the whole block is three and three quarter 108 px cells, comfortably inside the cap.
        assertEquals(108f * HexBoard.BLOCK_HEIGHT_IN_CELLS, portrait.blockHeight, 0.01f)
        assertTrue(portrait.blockHeight < cap)
    }

    @Test
    fun `landscape is capped to the approved block and yields the approved cell`() {
        val displayHeight = 1080f
        // 55% of the screen, straight from the constant — the approved landscape share.
        assertEquals(0.55f, HexBoard.LANDSCAPE_HEIGHT_FRACTION, 0f)
        val cap = HexBoard.heightCap(displayHeight)

        // Uncapped, the width alone would decide a 240 px cell and a 900 px block that fills the
        // width with no side margins — the defect.
        assertEquals(240f, HexBoard.cellWidth(2400f), 0.01f)
        assertEquals(240f * HexBoard.BLOCK_HEIGHT_IN_CELLS, HexBoard.boardHeight(2400f), 0.01f)
        assertFalse(HexBoard.heightBinds(2400f, HexBoard.boardHeight(2400f)))

        // Under the cap the height is what binds, and the height the board is placed into is the
        // cap itself: 55% of 1080 px is the approved 594 px block.
        val available = HexBoard.cappedBoardHeight(2400f, displayHeight)
        assertEquals(cap, available, 0.001f)
        assertTrue("the cap must bind in landscape", HexBoard.heightBinds(2400f, available))

        val landscape = measure(2400f, displayHeight)
        // The approved block, and the 158.28 px cell that falls out of it — neither is hard-coded
        // anywhere; both are the cap divided by the frozen block height.
        assertEquals(cap, landscape.blockHeight, 0.01f)
        assertEquals(cap / HexBoard.BLOCK_HEIGHT_IN_CELLS, landscape.cellWidth, 0.01f)
        assertEquals(1.1547f, landscape.capRatio, 0.0001f)
        assertTrue(landscape.cellWidth < 240f)

        // Centred, with the leftover width empty on both sides in equal bands.
        assertEquals(landscape.leftInset, landscape.rightBand, 0.01f)
        assertEquals(10f * landscape.cellWidth, 2400f - 2f * landscape.leftInset, 0.01f)
        assertTrue("the side band is real", landscape.leftInset > 1f)
    }

    @Test
    fun `the hexagon ratio is the same in both orientations`() {
        val portrait = measure(1080f, 2400f)
        val landscape = measure(2400f, 1080f)

        // cap height = cap width x 1.1547 (= 2/sqrt(3)), identical in both: the one number that says
        // the caps are regular hexagons and the honeycomb has not been stretched to fit.
        assertEquals(1.1547f, portrait.capRatio, 0.0001f)
        assertEquals(1.1547f, landscape.capRatio, 0.0001f)
        assertEquals(2f / sqrt(3f), portrait.capRatio, 0.0001f)
        assertEquals(2f / sqrt(3f), landscape.capRatio, 0.0001f)
        // Identical to portrait to within a float's own last bit — the cells differ in size, so the
        // division does not land on the same ULP, but the proportion is not what the orientation moves.
        assertEquals(portrait.capRatio, landscape.capRatio, 0.0001f)
    }

    @Test
    fun `rotating portrait to landscape and back returns the first measurement exactly`() {
        val portrait = measure(1080f, 2400f)
        val landscape = measure(2400f, 1080f)
        val portraitAgain = measure(1080f, 2400f)

        // The rotation really did re-size the board for the new screen ...
        assertTrue("rotation changes the cell", abs(portrait.cellWidth - landscape.cellWidth) > 1f)
        // ... and rotating back reproduces the first measurement exactly, with no stale size.
        assertEquals(portrait.cellWidth, portraitAgain.cellWidth, 0f)
        assertEquals(portrait.blockHeight, portraitAgain.blockHeight, 0f)
        assertEquals(portrait.leftInset, portraitAgain.leftInset, 0f)
        assertEquals(portrait.rightBand, portraitAgain.rightBand, 0f)
        assertEquals(portrait.scale, portraitAgain.scale, 0f)
        assertEquals(portrait.capRatio, portraitAgain.capRatio, 0f)
        assertEquals(portrait.availableHeight, portraitAgain.availableHeight, 0f)
    }
}
