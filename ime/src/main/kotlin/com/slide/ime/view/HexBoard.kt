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
 * without becoming a second place the hexagon is defined. That separation is what makes the fit
 * rule safe: the board can be scaled, but there is exactly one description of what is being
 * scaled.
 *
 * The whole board hangs off one per-orientation number, the *hexagon ratio*: cap height over cell
 * width. A row pitch is three quarters of a cap ([PITCH_CAP_FRACTION]), so the block is three
 * pitches plus one cap — `BLOCK_CAP_FACTOR` caps, i.e. `3.25 x ratio` cells. For the regular ratio
 * (2/sqrt(3) = 1.1547, [REGULAR_HEIGHT_RATIO]) that is 3.7528 cells, the "three and three quarter
 * cells" the board was frozen at; the number is derived here and never written down.
 *
 * The ratio is per orientation because the two axes are spent in opposite ways. Portrait is
 * width-bound: QWERTY needs ten columns, so on a 1080 px screen the cell width is fixed and height
 * is the only free axis — the taller hexagon ([HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION] = 1.5) spends
 * it. Landscape is height-bound: the display height share fixes the block, so width is free — there
 * the *regular* hexagon is what fits and the leftover width is left as equal side bands. The letter
 * grid — 10 / 9 / 9 / 7 keys, rows staggered 0.0 / 0.5 / 0.0 / 0.5, one three-cell space — is
 * identical in both; only the cell's aspect moves.
 *
 * One board for both orientations. Portrait is a special case of the same rule as landscape: the
 * cell is the largest that fits, and whichever of width and height binds decides the shape of the
 * empty space around it — none in portrait, the two side bands in landscape. Which of the two is
 * which is decided once, in [sizing].
 */
object HexBoard {

    /** Cells across the widest row: ten single-cell keys, in every orientation. */
    const val COLUMNS = 10

    /** Rows on the approved board — 10 / 9 / 9 / 7 keys. */
    const val ROWS = 4

    /**
     * A row pitch is three quarters of a cap.
     *
     * That fraction is the interlock: a cap overhangs its own cell by an eighth of its height at
     * each end, which is how a row's points reach the shoulder line of the row above. It is the
     * frozen [HexKeyMetrics.HEIGHT_FRACTION] read the other way round — cap / pitch = 4/3, so
     * pitch / cap = 3/4 — and the block depends on it, which is why it is a name and not a `0.75f`
     * in a formula.
     */
    const val PITCH_CAP_FRACTION = 0.75f

    /**
     * The block's height in caps: three row pitches plus one cap — 3 x 0.75 + 1 = 3.25.
     *
     * Written from [PITCH_CAP_FRACTION] rather than as the 3.7528 a regular hexagon rounds to,
     * because the block is `3.25 x ratio` cells and the ratio is per orientation. Hard-coding the
     * regular value is exactly what let a ratio change desync the block from the hexagon it is
     * built out of.
     */
    const val BLOCK_CAP_FACTOR = 3f * PITCH_CAP_FRACTION + 1f

    /**
     * Row pitch as a fraction of the cell width for a *regular* hexagon: sqrt(3)/2.
     *
     * sqrt(3)/2 is not a taste: it is the ratio at which a cap of [HexKeyMetrics.WIDTH_FRACTION]
     * wide and [HexKeyMetrics.HEIGHT_FRACTION] of the pitch tall is a *regular* hexagon. Change it
     * and the hexagons stretch; that is the distortion the fit rule exists to avoid.
     */
    val CELL_HEIGHT_RATIO = sqrt(3f) / 2f

    /**
     * The regular hexagon's hexagon ratio — cap height / cell width — 2/sqrt(3) = 1.1547.
     *
     * Written from the same two numbers the cap is built out of, [CELL_HEIGHT_RATIO] and
     * [HexKeyMetrics.HEIGHT_FRACTION], so "the pitch is sqrt(3)/2 of a cell" and "the cap is 4/3 of
     * the pitch" cannot drift apart: they are two readings of one regular hexagon. Landscape keeps
     * this because there the height is already spent — the caps stay regular and the width is what
     * is given away.
     */
    val REGULAR_HEIGHT_RATIO: Float = CELL_HEIGHT_RATIO * HexKeyMetrics.HEIGHT_FRACTION

    /** The honeycomb's half-key stagger, in cells — the step every adjacent row takes. */
    const val HONEYCOMB_STEP_IN_CELLS = 0.5f

    /**
     * The most of the display's height the board may take in landscape, as a fraction of that
     * height — 0.45, deliberately *less* than half.
     *
     * It was 0.55 and that was too tall. Measured on the device: with the keyboard taking 55% of a
     * 1080 px landscape screen there was no room left to see what was being typed — "because
     * landscape is so big there also is no space on the screen to actually see the words I'm
     * typing". 0.45 gives the block 486 px and leaves 55% of the height, 594 px, to the app above
     * it. The cell only follows: 486 / 3.7528 cells is a 129.5 px cell, ten of which are a
     * 1295 px board with 552.5 px bands either side.
     */
    const val LANDSCAPE_HEIGHT_FRACTION = 0.45f

    /** The board's two sizings. Which axis is spent decides which hexagon ratio it gets. */
    enum class Orientation { PORTRAIT, LANDSCAPE }

    /**
     * One orientation's board geometry: the share of the display height the board may take
     * ([heightFraction]), and the hexagon ratio it is drawn at ([heightRatio], cap height over cell
     * width).
     */
    data class Sizing(val heightFraction: Float, val heightRatio: Float)

    /**
     * The board's geometry for [orientation] — the only place either orientation is described.
     *
     * Landscape is expected to change shape again: the request on the table is a board split down
     * the middle, one half for the left hand and one for the right. When it does, this `when` is the
     * seam — the sizing path reads [Sizing] and never spells landscape out itself — so the split
     * lands here instead of being smeared through the view and the drawing.
     *
     * Portrait's share is 1.0, i.e. no cap: it is width-bound, and its natural block (526.5 px of a
     * 2400 px screen) is nowhere near a sane share. Capping it would be the one thing that could
     * *shrink* the portrait keys, which is the opposite of what portrait was asked for.
     */
    fun sizing(orientation: Orientation): Sizing = when (orientation) {
        Orientation.PORTRAIT -> Sizing(
            heightFraction = 1f,
            heightRatio = HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION,
        )
        Orientation.LANDSCAPE -> Sizing(
            heightFraction = LANDSCAPE_HEIGHT_FRACTION,
            heightRatio = REGULAR_HEIGHT_RATIO,
        )
    }

    /** The hexagon ratio (cap height / cell width) for [orientation]. */
    fun heightRatio(orientation: Orientation): Float = sizing(orientation).heightRatio

    fun cellWidth(viewWidth: Float): Float = viewWidth / COLUMNS

    /**
     * The row pitch — a cell's height — for a cell [cellWidth] wide at hexagon ratio
     * [Sizing.heightRatio]: three quarters of the cap, and the cap is that ratio cells tall.
     */
    fun cellHeight(cellWidth: Float, sizing: Sizing): Float =
        cellWidth * PITCH_CAP_FRACTION * sizing.heightRatio

    fun capHeight(cellHeight: Float): Float = cellHeight * HexKeyMetrics.HEIGHT_FRACTION

    /**
     * How far a cap reaches above its cell's top, and below its bottom.
     *
     * The cap is taller than the cell by design — that overhang is what makes neighbouring rows
     * interlock — so the drawn board starts and ends this far outside the cell block.
     */
    fun topOverhang(cellHeight: Float): Float = (capHeight(cellHeight) - cellHeight) / 2f

    /** The block's drawn height in cells at [sizing]: 3.25 x ratio, exactly. */
    fun blockHeightInCells(sizing: Sizing): Float = BLOCK_CAP_FACTOR * sizing.heightRatio

    /** The drawn height of a block whose cell is [cellWidth], at [sizing]. */
    fun blockHeight(cellWidth: Float, sizing: Sizing): Float =
        cellWidth * blockHeightInCells(sizing)

    /** The drawn height of a block laid across a viewport [viewWidth] wide (three pitches + a cap). */
    fun boardHeight(viewWidth: Float, sizing: Sizing): Float =
        blockHeight(cellWidth(viewWidth), sizing)

    /** Content height that puts the rows at their natural pitch: one cell per row. */
    fun contentHeight(viewWidth: Float, sizing: Sizing): Float =
        ROWS * cellHeight(cellWidth(viewWidth), sizing)

    /**
     * The tallest the board may be drawn on a display [displayHeight] px tall, at [sizing].
     *
     * A non-positive or non-finite height is treated as no cap at all, so a display the view cannot
     * yet measure leaves the board at its natural size rather than collapsing it to nothing.
     */
    fun heightCap(displayHeight: Float, sizing: Sizing): Float =
        if (displayHeight <= 0f || !displayHeight.isFinite()) {
            Float.POSITIVE_INFINITY
        } else {
            displayHeight * sizing.heightFraction
        }

    /**
     * The drawn height the board is given in a viewport [viewWidth] wide on a display [displayHeight]
     * px tall: its natural block height, held to [Sizing.heightFraction] of the display.
     *
     * Only the block is capped; the view's own padding sits outside it, so the height the board is
     * then placed into is the cap itself — 486 px of the 1080 px landscape screen. The cell follows
     * from that height in [place], so the 129.5 px landscape cell is derived rather than stated, and
     * in portrait the natural block comes back unchanged because portrait is uncapped.
     */
    fun cappedBoardHeight(viewWidth: Float, displayHeight: Float, sizing: Sizing): Float =
        min(boardHeight(viewWidth, sizing), heightCap(displayHeight, sizing))

    /**
     * The largest uniform cell width that fits the board in a viewport of [viewportWidth] x
     * [availableHeight], at [sizing].
     *
     * Width binds in portrait and height in landscape, and neither is special-cased: ten columns of
     * the width-derived cell are exactly as wide as the viewport, so when the frame is shorter than
     * the block those columns want, the height simply takes over. The choice is a `min` over the
     * two, which is why the board can only ever shrink — and why the leftover side width in
     * landscape is left empty instead of being filled with keys nobody asked for.
     */
    fun cellWidthFor(viewportWidth: Float, availableHeight: Float, sizing: Sizing): Float {
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) return 0f
        val byWidth = viewportWidth / COLUMNS
        if (availableHeight <= 0f || !availableHeight.isFinite()) return byWidth
        return min(byWidth, availableHeight / blockHeightInCells(sizing))
    }

    /** True when the frame is too short for the width-derived cell, so the height is what binds. */
    fun heightBinds(viewportWidth: Float, availableHeight: Float, sizing: Sizing): Boolean =
        cellWidthFor(viewportWidth, availableHeight, sizing) < viewportWidth / COLUMNS

    /** The board as it was placed: its metrics, and the keys themselves. */
    class HexPlacement(
        val cellWidth: Float,
        val boardWidth: Float,
        val boardHeight: Float,
        /** Blank space to the left of the board; [leftInset] * 2 = the total side margin. */
        val leftInset: Float,
        /** Placed width as a share of the width-filling size: 1.0 in portrait, less in landscape. */
        val scale: Float,
        /** The geometry the cell was sized from. */
        val sizing: Sizing,
        val keys: List<PlacedKey>,
    ) {
        /** True when the board fills the viewport's width and has no side margins. */
        val spansViewport: Boolean get() = leftInset <= 0.001f

        /** The hexagon ratio (cap height / cell width) this placement was drawn at. */
        val heightRatio: Float get() = sizing.heightRatio
    }

    /**
     * Places the board into a viewport, centring it horizontally and scaling it to fit.
     *
     * The scale is applied to the cell and the row height follows from the same ratio, so the caps
     * keep their hexagon — a stretched honeycomb is the one outcome the fit rule exists to prevent.
     * The board is centred rather than left-aligned because the empty space either side of a
     * scaled-down board is a margin, and a margin that only appears on the right looks like a bug.
     */
    fun place(
        layout: KeyboardLayout,
        viewportWidth: Float,
        availableHeight: Float,
        sizing: Sizing,
        topOffset: Float = 0f,
    ): HexPlacement {
        val cellWidth = cellWidthFor(viewportWidth, availableHeight, sizing)
        val boardWidth = cellWidth * COLUMNS
        val leftInset = ((viewportWidth - boardWidth) * 0.5f).coerceAtLeast(0f)
        val cellHeight = cellHeight(cellWidth, sizing)
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
            boardHeight = blockHeight(cellWidth, sizing),
            leftInset = leftInset,
            scale = if (naturalCell > 0f) cellWidth / naturalCell else 1f,
            sizing = sizing,
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
