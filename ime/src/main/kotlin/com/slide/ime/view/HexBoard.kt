package com.slide.ime.view

import com.slide.core.layout.KeyboardLayout
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The hex board's own measurements, derived from the geometry the geometry slice froze.
 *
 * Every constant the cap's shape depends on is read from [HexKeyMetrics] rather than restated, so
 * this file can lay the board out — how wide a cell is, how tall a row is, where the caps overhang —
 * without becoming a second place the hexagon is defined. That separation is what makes the
 * fit rule safe: the board can be scaled, but there is exactly one description of what is being
 * scaled.
 *
 * A pointy-top regular hexagon has cap height = cap width * 2/sqrt(3), i.e. width * sqrt(3)/2 of
 * cell * 4/3 = the frozen [HexKeyMetrics.HEIGHT_FRACTION]. The rows are therefore [COLUMNS] cells
 * across, [ROWS] of them, and the whole block is 3 row pitches plus one cap tall — 3.7528 cells,
 * the "3 and three quarter cells" in words.
 *
 * One board for both orientations. Portrait is a special case of the same rule as landscape: the
 * cell is the largest that fits, and whichever of width and height binds decides the shape of the
 * empty space around it — none in portrait, the two side margins in landscape.
 */
object HexBoard {

    /** Cells across the widest row: ten single-cell keys, in every orientation. */
    const val COLUMNS = 10

    /** Rows on the approved board — 10 / 9 / 9 / 7 keys. */
    const val ROWS = 4

    /**
     * Cell height as a fraction of cell width.
     *
     * sqrt(3)/2 is not a taste: it is the ratio at which a cap of [HexKeyMetrics.WIDTH_FRACTION]
     * wide and [HexKeyMetrics.HEIGHT_FRACTION] of the cell tall is a *regular* hexagon. Change it
     * and the hexagons stretch; that is the distortion the fit rule exists to avoid.
     */
    val CELL_HEIGHT_RATIO = sqrt(3f) / 2f

    /** The honeycomb's half-key stagger, in cells — the step every adjacent row takes. */
    const val HONEYCOMB_STEP_IN_CELLS = 0.5f

    fun cellWidth(viewWidth: Float): Float = viewWidth / COLUMNS

    fun cellHeight(cellWidth: Float): Float = cellWidth * CELL_HEIGHT_RATIO

    fun capHeight(cellHeight: Float): Float = cellHeight * HexKeyMetrics.HEIGHT_FRACTION

    /**
     * How far a cap reaches above its cell's top, and below its bottom.
     *
     * The cap is taller than the cell by design — that overhang is what makes neighbouring rows
     * interlock — so the drawn board starts and ends this far outside the cell block.
     */
    fun topOverhang(cellHeight: Float): Float = (capHeight(cellHeight) - cellHeight) / 2f

    /** Content height that puts the rows at their natural pitch: one cell per row. */
    fun contentHeight(viewWidth: Float): Float = ROWS * cellHeight(cellWidth(viewWidth))

    /** The drawn height of a block whose cell is [cellWidth]: 3.7528 cells, exactly. */
    fun blockHeight(cellWidth: Float): Float = cellWidth * BLOCK_HEIGHT_IN_CELLS

    /** The drawn height of a block laid across a viewport [viewWidth] wide (three pitches + a cap). */
    fun boardHeight(viewWidth: Float): Float = blockHeight(cellWidth(viewWidth))

    /**
     * The most of the display's height the board may take, as a fraction of that height.
     *
     * This is the approved landscape render. On a 2400 x 1080 screen the board is a 594 px block —
     * 55% of the display height — which puts the cell at the approved 158.28 px and leaves the
     * leftover width empty at each side. The board's natural height there is 3.7528 x 240 px = 900
     * px, because with no cap the width alone decides a 240 px cell; drawn at that size the board
     * fills the width with no side margins, which is not the single uniformly scaled board that was
     * approved. Portrait never approaches the cap — its natural block is 405 px of a 2400 px screen
     * — so the cap is a no-op there.
     */
    const val LANDSCAPE_HEIGHT_FRACTION = 0.55f

    /**
     * The tallest the board may be drawn on a display [displayHeight] px tall.
     *
     * A non-positive or non-finite height is treated as no cap at all, so a display the view cannot
     * yet measure leaves the board at its natural size rather than collapsing it to nothing.
     */
    fun heightCap(displayHeight: Float): Float =
        if (displayHeight <= 0f || !displayHeight.isFinite()) {
            Float.POSITIVE_INFINITY
        } else {
            displayHeight * LANDSCAPE_HEIGHT_FRACTION
        }

    /**
     * The drawn height the board is given in a viewport [viewWidth] wide on a display [displayHeight]
     * px tall: its natural block height, held to [LANDSCAPE_HEIGHT_FRACTION] of the display.
     *
     * Only the block is capped; the view's own padding sits outside it, so the height the board is
     * then placed into is the cap itself — 594 px of the approved 1080 px landscape screen. The cell
     * follows from that height in [place], so the approved 158.28 px cell is derived rather than
     * stated, and in portrait the natural height comes back unchanged because it is under the cap.
     */
    fun cappedBoardHeight(viewWidth: Float, displayHeight: Float): Float =
        min(boardHeight(viewWidth), heightCap(displayHeight))

    /**
     * The block's drawn height for a cell one unit wide — 3.7528, what "3.75 cells" rounds to.
     *
     * Written from the same two numbers the cap is built out of, [CELL_HEIGHT_RATIO] and
     * [HexKeyMetrics.HEIGHT_FRACTION], so "the block is three and three quarter cells tall" and
     * "the cap is a regular hexagon" cannot drift apart: they are two readings of one relation.
     */
    val BLOCK_HEIGHT_IN_CELLS: Float
        get() {
            val rowPitch = CELL_HEIGHT_RATIO
            val cap = rowPitch * HexKeyMetrics.HEIGHT_FRACTION
            return ROWS * rowPitch + (cap - rowPitch)
        }

    /**
     * The largest uniform cell width that fits the board in a viewport of [viewportWidth] x
     * [availableHeight].
     *
     * Width binds in portrait and height in landscape, and neither is special-cased: ten columns of
     * the width-derived cell are exactly as wide as the viewport, so when the frame is shorter than
     * the block those columns want, the height simply takes over. The choice is a `min` over the
     * two, which is why the board can only ever shrink — and why the leftover side width in
     * landscape is left empty instead of being filled with keys nobody asked for.
     */
    fun cellWidthFor(viewportWidth: Float, availableHeight: Float): Float {
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) return 0f
        val byWidth = viewportWidth / COLUMNS
        if (availableHeight <= 0f || !availableHeight.isFinite()) return byWidth
        return min(byWidth, availableHeight / BLOCK_HEIGHT_IN_CELLS)
    }

    /** True when the frame is too short for the width-derived cell, so the height is what binds. */
    fun heightBinds(viewportWidth: Float, availableHeight: Float): Boolean =
        cellWidthFor(viewportWidth, availableHeight) < viewportWidth / COLUMNS

    /** The board as it was placed: its metrics, and the keys themselves. */
    class HexPlacement(
        val cellWidth: Float,
        val boardWidth: Float,
        val boardHeight: Float,
        /** Blank space to the left of the board; [leftInset] * 2 = the total side margin. */
        val leftInset: Float,
        /** Placed width as a share of the width-filling size: 1.0 in portrait, less in landscape. */
        val scale: Float,
        val keys: List<PlacedKey>,
    ) {
        /** True when the board fills the viewport's width and has no side margins. */
        val spansViewport: Boolean get() = leftInset <= 0.001f
    }

    /**
     * Places the board into a viewport, centring it horizontally and scaling it to fit.
     *
     * The scale is applied to the cell and the row height follows from the same ratio, so the caps
     * are regular hexagons at every viewport — a stretched honeycomb is the one outcome the fit
     * rule exists to prevent. The board is centred rather than left-aligned because the empty space
     * either side of a scaled-down board is a margin, and a margin that only appears on the right
     * looks like a bug.
     */
    fun place(
        layout: KeyboardLayout,
        viewportWidth: Float,
        availableHeight: Float,
        topOffset: Float = 0f,
    ): HexPlacement {
        val cellWidth = cellWidthFor(viewportWidth, availableHeight)
        val boardWidth = cellWidth * COLUMNS
        val leftInset = ((viewportWidth - boardWidth) * 0.5f).coerceAtLeast(0f)
        val cellHeight = cellHeight(cellWidth)
        val placed = KeyGeometry.place(
            layout = layout,
            width = boardWidth,
            contentHeight = ROWS * cellHeight,
            // The caps overhang their cells; reserving that here keeps the top row's points inside
            // the view instead of clipped against its edge.
            topOffset = topOffset + topOverhang(cellHeight),
        ).map { it.shiftedBy(leftInset) }
        val naturalCell = if (viewportWidth > 0f) viewportWidth / COLUMNS else cellWidth
        return HexPlacement(
            cellWidth = cellWidth,
            boardWidth = boardWidth,
            // The block is a fixed number of cells tall, so it scales with the cell exactly.
            boardHeight = blockHeight(cellWidth),
            leftInset = leftInset,
            scale = if (naturalCell > 0f) cellWidth / naturalCell else 1f,
            keys = placed,
        )
    }

    /**
     * Each row's start, in cell widths — 0.0 / 0.5 / 0.0 / 0.5 on the approved board.
     *
     * Read back out of the placed keys rather than out of the layout, so the measurement is of what
     * is actually drawn and a layout edited into a different stagger fails a test instead of
     * quietly drifting apart from the drawing.
     *
     * Measured from the board's own left edge, not the viewport's, so the same board placed in
     * portrait and in landscape measures the same: the margin is a margin, not a row indent.
     */
    fun rowOffsetsInCells(keys: List<PlacedKey>, cellWidth: Float): List<Float> {
        if (cellWidth <= 0f || keys.isEmpty()) return emptyList()
        val boardLeft = keys.minOf { it.left }
        return keys.groupBy { it.row }
            .toSortedMap()
            .values
            .map { row -> (row.minOf { it.left } - boardLeft) / cellWidth }
    }

    /**
     * The step between each adjacent pair of rows, in cell widths — the honeycomb invariant.
     *
     * Folded to [0, 0.5] because the stagger is a phase: a row a whole key to the left of its
     * neighbour is aligned with it, not offset by one, and only the fraction matters.
     */
    fun adjacentStaggerStepsInCells(keys: List<PlacedKey>, cellWidth: Float): List<Float> {
        val offsets = rowOffsetsInCells(keys, cellWidth)
        return offsets.zipWithNext { above, below -> staggerStep(above, below) }
    }

    private fun staggerStep(above: Float, below: Float): Float {
        val fraction = (((below - above) % 1f) + 1f) % 1f
        return min(fraction, 1f - fraction)
    }

    /** Rounds a key's width in cells, for the keys (space) that span more than one. */
    fun spanCells(keyWidthWeight: Float): Int = keyWidthWeight.roundToInt().coerceAtLeast(1)

    /** True while [layout] is drawn as the honeycomb. */
    fun isHexBoard(layout: KeyboardLayout): Boolean = layout.hexKeys

    private fun PlacedKey.shiftedBy(dx: Float): PlacedKey =
        if (dx == 0f) this else PlacedKey(key, left + dx, top, right + dx, bottom, row)
}
