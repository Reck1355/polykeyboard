package com.slide.ime.view

import com.slide.core.layout.Layouts
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's height share, as pure measurements — no device.
 *
 * The gap this closes: nothing capped the IME's height, so the keyboard view self-sized to the
 * board's natural block on a 2400 px wide landscape screen and the board filled the width with no
 * side bands — not the board that was approved. The share is now
 * [HexBoard.LANDSCAPE_HEIGHT_FRACTION], and it is deliberately *under* half: at 0.55 of a 1080 px
 * landscape screen there was no room left to read what was being typed, and 0.45 gives the block
 * 486 px, leaving 55% of the height to the app above. That block is now spent on the two-handed split
 * — two 787 px halves, one anchored to each edge, with the 747 px channel between them — so the
 * leftover width is the channel rather than the old single board's side bands. Portrait is uncapped —
 * it is width-bound and its natural 526.5 px block is nowhere near any sane share — so the share has
 * to be a no-op there, and it is still one board.
 *
 * The screen the keyboard is drawn on is the viewport here: an IME keyboard spans the display's
 * width, and the share is a fraction of the display's height. Every expectation is derived from
 * [HexBoard.LANDSCAPE_HEIGHT_FRACTION] and the [HexBoard.sizing] a display's orientation yields,
 * rather than restated, so a drifted fraction or a stretched hexagon fails here instead of shipping.
 */
class HexBoardHeightCapTest {

    private val portrait = HexBoard.sizing(HexBoard.Orientation.PORTRAIT)
    private val landscape = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE)

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
        /** The channel between the halves of a split board; 0 when the board is single. */
        val channelStart: Float,
        val channelWidth: Float,
    )

    private fun measure(
        displayWidth: Float,
        displayHeight: Float,
        sizing: HexBoard.Sizing,
    ): Measurement {
        val available = HexBoard.cappedBoardHeight(displayWidth, displayHeight, sizing)
        val placement = HexBoard.place(Layouts.HexQwertyEn, displayWidth, available, sizing)
        return Measurement(
            cellWidth = placement.cellWidth,
            blockHeight = placement.boardHeight,
            leftInset = placement.leftInset,
            rightBand = displayWidth - placement.boardWidth - placement.leftInset,
            scale = placement.scale,
            capRatio = HexBoard.capHeight(HexBoard.cellHeight(placement.cellWidth, sizing)) /
                placement.cellWidth,
            availableHeight = available,
            heightBinds = HexBoard.heightBinds(displayWidth, available, sizing),
            channelStart = placement.channel?.start ?: 0f,
            channelWidth = placement.channel?.width ?: 0f,
        )
    }

    @Test
    fun `portrait is width-governed and the height share is a no-op there`() {
        val displayHeight = 2400f
        val cap = HexBoard.heightCap(displayHeight, portrait)
        val naturalBlock = HexBoard.cappedBoardHeight(1080f, displayHeight, portrait)

        // Uncapped in portrait: the board's own natural block is what comes back, and the viewport
        // height is the only bound — which this frame is nowhere near.
        assertEquals(displayHeight, cap, 0.001f)
        assertEquals(HexBoard.boardHeight(1080f, portrait), naturalBlock, 0.001f)

        val measured = measure(1080f, displayHeight, portrait)
        // Width governs: ten 108 px cells span the 1080 px width exactly; the height never binds.
        assertFalse("the height must not bind in portrait", measured.heightBinds)
        assertEquals(108f, measured.cellWidth, 0.01f)
        // It fills the width, so there is no side margin and it is drawn at full scale.
        assertEquals(0f, measured.leftInset, 0.01f)
        assertEquals(0f, measured.rightBand, 0.01f)
        assertEquals(1f, measured.scale, 0.001f)
        // The whole block is 3.25 x 1.5 = 4.875 cells of 108 px, comfortably inside the frame.
        assertEquals(108f * HexBoard.blockHeightInCells(portrait), measured.blockHeight, 0.01f)
        assertEquals(526.5f, measured.blockHeight, 0.01f)
        assertTrue(measured.blockHeight < cap)
    }

    @Test
    fun `landscape is capped to 486 px - under half the screen - and the flat split fits in it`() {
        val displayHeight = 1080f
        // 0.45 of the screen, straight from the constant — deliberately under half, so the app
        // above keeps the rest and the words being typed stay visible.
        assertEquals(0.45f, HexBoard.LANDSCAPE_HEIGHT_FRACTION, 0f)
        val cap = HexBoard.heightCap(displayHeight, landscape)
        assertEquals(486f, cap, 0.01f)

        // Uncapped, the width alone would decide a 240 px cell and a 741 px block that fills the
        // width with no channel — the defect.
        assertEquals(240f, HexBoard.cellWidth(2400f), 0.01f)
        assertEquals(240f * HexBoard.blockHeightInCells(landscape), HexBoard.boardHeight(2400f, landscape), 0.01f)
        assertFalse(HexBoard.heightBinds(2400f, HexBoard.boardHeight(2400f, landscape), landscape))

        // Under the cap the height is what binds, and the height the board is placed into is the
        // cap itself: 0.45 of 1080 px is the 486 px block.
        val available = HexBoard.cappedBoardHeight(2400f, displayHeight, landscape)
        assertEquals(cap, available, 0.001f)
        assertTrue("the cap must bind in landscape", HexBoard.heightBinds(2400f, available, landscape))

        val measured = measure(2400f, displayHeight, landscape)
        // The approved block, and the 157.4089 px cell that falls out of it — neither is hard-coded
        // anywhere; both are the cap divided by the derived block height, 3.25 x 0.95 cells. (This
        // assertion pinned the old regular ratio's 129.5041 px cell — rewritten, not dropped.)
        assertEquals(cap, measured.blockHeight, 0.01f)
        assertEquals(cap / HexBoard.blockHeightInCells(landscape), measured.cellWidth, 0.001f)
        assertEquals(157.42f, measured.cellWidth, 0.5f)
        assertEquals(0.95f, measured.capRatio, 0.0001f)
        assertTrue(measured.cellWidth < 240f)

        // The app keeps more than half the screen — the whole reason the share came down.
        assertTrue("the app keeps over half", displayHeight - measured.blockHeight > displayHeight * 0.5f)

        // The split replaced the single centred board's equal side bands: there is no side margin any
        // more, and the free width is the channel between the halves, opening exactly where each
        // half's inner edge ends.
        assertEquals(0f, measured.leftInset, 0.01f)
        assertEquals(0f, measured.rightBand, 0.01f)
        assertEquals(668.5f, measured.channelWidth, 0.5f)
        assertTrue("the channel is real", measured.channelWidth > 1f)
        assertEquals(
            HexBoard.splitHalves(Layouts.HexQwertyEn).left.spanInCells * measured.cellWidth,
            measured.channelStart,
            0.01f,
        )
    }

    @Test
    fun `the hexagon ratio is per orientation - tall in portrait, flat in landscape`() {
        val portraitMeasured = measure(1080f, 2400f, portrait)
        val landscapeMeasured = measure(2400f, 1080f, landscape)

        // Portrait spends its free height on a taller hexagon: cap height = 1.5 x cell width.
        assertEquals(1.5f, portraitMeasured.capRatio, 0.0001f)
        assertEquals(HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION, portraitMeasured.capRatio, 0.0001f)

        // Landscape is the flat 0.95 he picked for the split board — cap height almost exactly the
        // cell width. This used to assert the regular 2/sqrt(3) = 1.1547, which landscape no longer
        // draws; the regular ratio stays only as the reference hexagon. (Rewritten, not dropped.)
        assertEquals(0.95f, landscapeMeasured.capRatio, 0.0001f)
        assertEquals(HexBoard.LANDSCAPE_HEIGHT_RATIO, landscapeMeasured.capRatio, 0.0001f)
        assertEquals(2f / sqrt(3f), HexBoard.REGULAR_HEIGHT_RATIO, 0.0001f)
        assertTrue(
            "landscape is flatter than the regular hexagon",
            landscapeMeasured.capRatio < HexBoard.REGULAR_HEIGHT_RATIO,
        )

        // And they differ, deliberately — that is the point of the per-orientation ratio.
        assertTrue(
            "portrait's hexagon is taller than landscape's",
            portraitMeasured.capRatio > landscapeMeasured.capRatio,
        )
    }

    @Test
    fun `rotating portrait to landscape and back returns the first measurement exactly`() {
        val portraitBefore = measure(1080f, 2400f, portrait)
        val landscapeMeasured = measure(2400f, 1080f, landscape)
        val portraitAgain = measure(1080f, 2400f, portrait)

        // The rotation really did re-size the board for the new screen, ratio and all ...
        assertTrue("rotation changes the cell", abs(portraitBefore.cellWidth - landscapeMeasured.cellWidth) > 1f)
        assertTrue("rotation changes the ratio", abs(portraitBefore.capRatio - landscapeMeasured.capRatio) > 0.1f)
        // ... and rotating back reproduces the first measurement exactly, with no stale size.
        assertEquals(portraitBefore.cellWidth, portraitAgain.cellWidth, 0f)
        assertEquals(portraitBefore.blockHeight, portraitAgain.blockHeight, 0f)
        assertEquals(portraitBefore.leftInset, portraitAgain.leftInset, 0f)
        assertEquals(portraitBefore.rightBand, portraitAgain.rightBand, 0f)
        assertEquals(portraitBefore.scale, portraitAgain.scale, 0f)
        assertEquals(portraitBefore.capRatio, portraitAgain.capRatio, 0f)
        assertEquals(portraitBefore.availableHeight, portraitAgain.availableHeight, 0f)
        assertEquals(portraitBefore.heightBinds, portraitAgain.heightBinds)
    }
}
