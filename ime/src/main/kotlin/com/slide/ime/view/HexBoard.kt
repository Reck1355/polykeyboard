package com.slide.ime.view

import com.slide.core.layout.Key
import com.slide.core.layout.KeyboardLayout
import com.slide.core.settings.KeyboardSize
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
 * it, and the ten columns fill the width. Landscape is height-bound: the display height share fixes
 * the block, so width is free — there the *flat* hexagon ([LANDSCAPE_HEIGHT_RATIO] = 0.95) is what
 * it is drawn at, and the free width becomes the channel down the middle of a two-handed board
 * ([SplitHalves]).
 *
 * The letter grid is one board in portrait and two halves in landscape. Portrait keeps the frozen
 * grid — 10 / 9 / 9 / 7 keys, rows staggered 0.0 / 0.5 / 0.0 / 0.5, one three-cell space — because
 * it is untouched. Landscape splits that same grid down the middle: a left half anchored to the
 * left edge and a right half anchored to the right, each row keeping its phase but measured from its
 * half's own outer edge, and every key one single-cell hexagon — the space included, which is one
 * cell per side instead of a fused trio. Neither half has a deformed key, and the space between them
 * is empty: a tap there resolves no key.
 *
 * One seam for both. Which shape the board takes is decided once, in [sizing]: [Sizing.split] is
 * true for landscape and false for portrait, and everything downstream reads it rather than testing
 * the orientation again. The cell is the largest that fits, and whichever of width and height binds
 * decides the rest — none of the width is given away in portrait, and in landscape the leftover
 * width is the channel.
 *
 * The keyboard-size ladder is spent through that same seam. [sizeMultiplier] turns the user's stop
 * ([KeyboardSize.STEPS]) into one multiplier, and each orientation spends it on the axis it has
 * free: portrait multiplies its hexagon ratio, so a bigger step draws *taller* hexagons in the same
 * ten columns; landscape multiplies its share of the display height, so a bigger step lifts the cap
 * and the cell follows it down. There is no second place a size is decided — the view, the settings
 * screen's preview and the tests all read [sizing], and [fittedSizing] is what holds the single
 * board's hexagon to the height it has room for.
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
     * the pitch" cannot drift apart: they are two readings of one regular hexagon. Neither
     * orientation draws at this ratio any more — portrait is the taller 1.5 and landscape the
     * flatter 0.95 — but it stays as the reference hexagon both are read against.
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
     * it. The cell only follows: 486 / 3.0875 cells is a 157.4 px cell, so each half of the split
     * reaches five of those, 787 px, from its own edge, and the channel they leave down the middle is
     * 747 px.
     *
     * This is the *base* share, the approved board's own. The keyboard-size ladder multiplies it
     * ([sizing] at a given step), so the five stops give 0.36 / 0.405 / 0.45 / 0.495 / 0.54 of the
     * display height and nothing restates any of them: the step reaches the board here and only here.
     */
    const val LANDSCAPE_HEIGHT_FRACTION = 0.45f

    /**
     * Landscape's hexagon ratio — cap height / cell width — 0.95: the flat hexagon.
     *
     * He picked it from the flat / regular / tall comparison for the split board: with the height
     * already spent by [LANDSCAPE_HEIGHT_FRACTION] and each half only five columns wide, a cap almost
     * exactly as wide as it is tall reads as a key rather than a tile. Portrait is untouched — it
     * keeps the tall [HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION] = 1.5.
     */
    const val LANDSCAPE_HEIGHT_RATIO = 0.95f

    /** Columns in one half of the split board — QWERTY's ten divided two ways. */
    const val SPLIT_COLUMNS_PER_HALF = COLUMNS / 2

    /**
     * The split board's width in cells — its two halves' spans, 5.5 + 5.0 for QWERTY: the ten columns
     * plus the half-cell step an offset row keeps from its half's own edge.
     *
     * The fit rule is asked before any layout is in hand ([cellWidthFor], [heightBinds]), so it cannot
     * read the halves' spans for itself; this is what it uses instead, and it is *not* free to drift:
     * the halves come from the layout's own row gaps, and the tests assert this equals their span.
     */
    const val SPLIT_WIDTH_IN_CELLS = SPLIT_COLUMNS_PER_HALF * 2f + HONEYCOMB_STEP_IN_CELLS * 2f

    /** The board's two sizings. Which axis is spent decides which hexagon ratio it gets. */
    enum class Orientation { PORTRAIT, LANDSCAPE }

    /**
     * One orientation's board geometry: the share of the display height the board may take
     * ([heightFraction]), the hexagon ratio it is drawn at ([heightRatio], cap height over cell
     * width), and whether the board is the two-handed split ([split]).
     *
     * The split is a field of the sizing rather than a separate object because it is the same kind of
     * fact as the ratio — a property of the orientation's geometry — and carrying it here is what
     * keeps "is this board split?" a question with exactly one answer, read from the same place the
     * cell is.
     */
    data class Sizing(val heightFraction: Float, val heightRatio: Float, val split: Boolean = false)

    /**
     * The keyboard-size ladder's multiplier for a stored [step] — 1 + step/10, so the five stops
     * are 0.8 / 0.9 / 1.0 / 1.1 / 1.2 and [KeyboardSize.DEFAULT_STEP] is exactly 1.
     *
     * One multiplier, one seam. The step is clamped to the ladder first ([KeyboardSize.clampStep]),
     * so a step from outside the range is *held* rather than accepted: a corrupt 7 draws the largest
     * board, never a board bigger than the ladder allows.
     *
     * The two orientations spend this differently and neither is restated downstream — portrait
     * multiplies its hexagon ratio (1.2 .. 1.8), landscape its share of the display height
     * (0.36 .. 0.54) — because the whole point of the ladder is that the size is one number and the
     * board's shape is what each orientation does with it.
     */
    fun sizeMultiplier(step: Int): Float =
        1f + KeyboardSize.clampStep(step) * KeyboardSize.STEP_GAIN

    /**
     * The board's geometry for [orientation] at keyboard-size [step] — the only place either
     * orientation is described.
     *
     * Landscape is the two-handed split: [Sizing.split] is true, so [place] lays the same letters out
     * as a left half anchored to the left edge and a right half anchored to the right, one
     * single-cell hexagon per key, with the empty channel between them. The split landed at this
     * seam, the one the earlier geometry left for it: the view and the drawing read [Sizing] and
     * never spell landscape out, so neither of them had to change.
     *
     * Portrait's share is 1.0, i.e. no cap: it is width-bound, and its natural block (526.5 px of a
     * 2400 px screen) is nowhere near a sane share. Capping it would be the one thing that could
     * *shrink* the portrait keys, which is the opposite of what portrait was asked for. Its split is
     * false, and that is asserted, so the landscape split cannot leak into it.
     *
     * The size ladder is spent on the free axis, per orientation: portrait's ratio is
     * [HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION] x multiplier, so a bigger step makes the hexagons
     * taller and nothing else; landscape's share is [LANDSCAPE_HEIGHT_FRACTION] x multiplier, so a
     * bigger step lifts the cap the flat hexagon is fitted into. [fittedSizing] is what stops the
     * taller portrait block from overrunning a frame that cannot hold it.
     */
    fun sizing(orientation: Orientation, step: Int = KeyboardSize.DEFAULT_STEP): Sizing {
        val multiplier = sizeMultiplier(step)
        return when (orientation) {
            Orientation.PORTRAIT -> Sizing(
                heightFraction = 1f,
                heightRatio = HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION * multiplier,
                split = false,
            )
            Orientation.LANDSCAPE -> Sizing(
                heightFraction = LANDSCAPE_HEIGHT_FRACTION * multiplier,
                heightRatio = LANDSCAPE_HEIGHT_RATIO,
                split = true,
            )
        }
    }

    /**
     * [sizing] as it can actually be drawn in a viewport [viewportWidth] x [availableHeight] — the
     * single board's hexagon held to the height there is room for.
     *
     * Portrait is width-bound: ten columns already spend the whole width, so the only axis a bigger
     * step can spend is the height, and the hexagon ratio is therefore the one number with a ceiling
     * — `BLOCK_CAP_FACTOR x ratio x cell` may never be taller than the frame. When it would be, the
     * ratio comes down to exactly what fits and the cell does not move: the board keeps spanning the
     * width it has always spanned and its hexagons flatten, instead of the board shrinking into a
     * centred strip with margins either side.
     *
     * Only the single board is clamped. The split is height-bound in the opposite direction — its
     * share of the display height *is* the cap and the cell follows from it — so nothing there can
     * overrun and there is nothing to clamp.
     */
    fun fittedSizing(sizing: Sizing, viewportWidth: Float, availableHeight: Float, rows: Int = ROWS): Sizing {
        if (sizing.split) return sizing
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) return sizing
        if (availableHeight <= 0f || !availableHeight.isFinite()) return sizing
        val cellWidth = viewportWidth / widthInCells(sizing)
        if (cellWidth <= 0f) return sizing
        val fittingRatio = availableHeight / (blockCapFactor(rows) * cellWidth)
        if (fittingRatio >= sizing.heightRatio) return sizing
        return sizing.copy(heightRatio = fittingRatio)
    }

    /** The hexagon ratio (cap height / cell width) for [orientation] at keyboard-size [step]. */
    fun heightRatio(orientation: Orientation, step: Int = KeyboardSize.DEFAULT_STEP): Float =
        sizing(orientation, step).heightRatio

    // --- The two-handed split (landscape only) -------------------------------------------------

    /**
     * One row of a [SplitHalf]: its keys, and how far the row is inset from that half's own outer
     * edge ([outerGap], in cells).
     *
     * The inset is read from the row's own layout gap — the leading gap on the left half, the
     * trailing gap on the right — so "the phase is preserved, measured from the half's own edge" is
     * the layout's own statement rather than a number restated here.
     */
    class SplitRow(val keys: List<Key>, val outerGap: Float) {
        /** How far this row reaches across the screen from its half's outer edge, in cells. */
        val spanInCells: Float get() = outerGap + keys.sumOf { it.widthWeight.toDouble() }.toFloat()
    }

    /**
     * One hand's half of the split board: four rows, anchored to that half's own screen edge.
     *
     * [spanInCells] is the half's reach across the screen — its widest row — and that row's inner
     * edge is what the channel opens against.
     */
    class SplitHalf(val rows: List<SplitRow>) {
        val spanInCells: Float get() = rows.maxOfOrNull { it.spanInCells } ?: 0f
    }

    /**
     * The board divided two ways: [left], anchored to the left edge, and [right], anchored to the
     * right.
     *
     * [spanInCells] is what the two halves need of the width between them — the sum of their reaches
     * — so the channel left over is `viewport - spanInCells x cell`. It is derived from the keys
     * rather than written down: change the cap, a half, or a row's phase and the channel moves with
     * it.
     */
    class SplitHalves(val left: SplitHalf, val right: SplitHalf) {
        val spanInCells: Float get() = left.spanInCells + right.spanInCells
    }

    /**
     * The two-handed split of [layout] — the same letters, divided down the middle.
     *
     * Each row is cut at its own *cell* centre rather than by key count, so a row whose keys are not
     * all one cell wide is cut where its cells are: the left half takes every key that starts before
     * that cell, and a key wider than one cell that straddles it — the three-cell space — gives one
     * single cell to each half instead of a fused trio. A single-cell key that straddles the cut
     * stays whole, on the side its left edge is: that is what keeps the odd rows (nine keys) as 5 + 4
     * rather than slicing a letter in two.
     *
     * The left half reads each row's inset from the row's leading gap and the right half from its
     * trailing gap — that is what "mirrored" means here: the same phase, measured from opposite
     * edges.
     */
    fun splitHalves(layout: KeyboardLayout): SplitHalves {
        val leftRows = ArrayList<SplitRow>(layout.rows.size)
        val rightRows = ArrayList<SplitRow>(layout.rows.size)
        layout.rows.forEach { row ->
            val cut = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat() * 0.5f
            val leftKeys = ArrayList<Key>()
            val rightKeys = ArrayList<Key>()
            var x = 0f
            row.keys.forEach { key ->
                val right = x + key.widthWeight
                when {
                    x >= cut -> rightKeys += key
                    right <= cut -> leftKeys += key
                    // A key wider than one cell straddling the cut: half its cells to each half,
                    // rounded up, so the key the two thumbs share is never the smaller for one of
                    // them. The three-cell space therefore gives two cells to each hand, which is what
                    // the landscape space bar is: two joined hexagons per side. The halves reach half
                    // a cell further inward for it, and that comes out of the channel rather than the
                    // cell, because the split's cell is decided by the available height.
                    spanCells(key.widthWeight) > 1 -> {
                        val perHalf = (spanCells(key.widthWeight) + 1) / 2
                        leftKeys += key.copy(widthWeight = perHalf.toFloat())
                        rightKeys += key.copy(widthWeight = perHalf.toFloat())
                    }
                    // A single-cell key that straddles the cut stays whole, on its left edge's side.
                    else -> leftKeys += key
                }
                x = right
            }
            leftRows += SplitRow(leftKeys, row.leadingGap)
            rightRows += SplitRow(rightKeys, row.trailingGap)
        }
        return SplitHalves(SplitHalf(leftRows), SplitHalf(rightRows))
    }

    /**
     * The empty corridor between the two halves of a split board, in view pixels.
     *
     * [start] and [end] are the inner edges of the two halves' widest-reaching rows — the tightest
     * pair of keys across the board — so the corridor is the largest empty span any row has, and a
     * tap anywhere in it is aimed at neither hand. It is empty by construction and by test: no key
     * crosses it, and its centre resolves no key.
     */
    class Channel(val start: Float, val end: Float) {
        /** The channel's width — the board's free width, and the minimum gap between the halves. */
        val width: Float get() = end - start

        val center: Float get() = (start + end) * 0.5f

        fun contains(x: Float): Boolean = x >= start && x <= end
    }

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

    /**
     * The block's drawn height in cells at [sizing]: one cap plus a pitch for every row after the first.
     *
     * [BLOCK_CAP_FACTOR] is the four-row board's number and stays it — 3 x PITCH_CAP_FRACTION + 1, three
     * pitches and a cap. Written as a count of rows it is the same value for four rows and the right one
     * for any other, which matters because a board is not always four rows tall any more: the honeycomb
     * takes a digit row, and a board placed into a box sized for the rows it does not have either
     * overruns that box (the digit row was drawn a full row past the block, so landscape grew by one row)
     * or squashes its keys to fit (portrait's letters came down from 121.5 px to 97.2).
     */
    /** The block's height in cells at ratio 1: one cap plus a pitch for every row after the first. */
    fun blockCapFactor(rows: Int = ROWS): Float = (rows - 1) * PITCH_CAP_FRACTION + 1f

    fun blockHeightInCells(sizing: Sizing, rows: Int = ROWS): Float =
        blockCapFactor(rows) * sizing.heightRatio

    /** The drawn height of a block whose cell is [cellWidth], at [sizing]. */
    fun blockHeight(cellWidth: Float, sizing: Sizing, rows: Int = ROWS): Float =
        cellWidth * blockHeightInCells(sizing, rows)

    /** The drawn height of a block laid across a viewport [viewWidth] wide (three pitches + a cap). */
    fun boardHeight(viewWidth: Float, sizing: Sizing, rows: Int = ROWS): Float =
        blockHeight(cellWidth(viewWidth), sizing, rows)

    /** Content height that puts the rows at their natural pitch: one cell per row. */
    fun contentHeight(viewWidth: Float, sizing: Sizing, rows: Int = ROWS): Float =
        rows * cellHeight(cellWidth(viewWidth), sizing)

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
    fun cappedBoardHeight(viewWidth: Float, displayHeight: Float, sizing: Sizing, rows: Int = ROWS): Float =
        min(boardHeight(viewWidth, sizing, rows), heightCap(displayHeight, sizing))

    /**
     * The capped height, moved by the "Keyboard height" setting.
     *
     * A hexagon has one proportion and no row to stretch, so the only thing a height control can do
     * to this board is resize it: the frame it is measured into gets taller or shorter and
     * [place] scales the whole honeycomb into that frame at the ratio the orientation calls for. The
     * setting is therefore a scale, not a row height, and the shape is never the thing that moves.
     *
     * At the default of 1 this is the capped height unchanged, which is what keeps every approved
     * number the approved number: the landscape cap, the 157.4 px cell, the two five-cell halves and
     * the channel between them all come from [cappedBoardHeight] alone, and the scale is 1 until the
     * user moves a control that until now did nothing at all.
     */
    fun scaledBoardHeight(cappedHeight: Float, scale: Float): Float = cappedHeight * scale

    /**
     * The largest uniform cell width that fits the board in a viewport of [viewportWidth] x
     * [availableHeight], at [sizing].
     *
     * Width binds in portrait and height in landscape, and neither is special-cased: ten columns of
     * the width-derived cell are exactly as wide as the viewport, so when the frame is shorter than
     * the block those columns want, the height simply takes over. The choice is a `min` over the
     * two, which is why the board can only ever shrink — and why the leftover side width in
     * landscape is left empty instead of being filled with keys nobody asked for.
     *
     * The width the columns are measured against is [widthInCells] — the split is half a cell wider
     * than the ten columns, because its outer offset rows keep their half-cell phase from their own
     * edge, so the halves cannot overlap on a narrow screen.
     */
    fun cellWidthFor(viewportWidth: Float, availableHeight: Float, sizing: Sizing, rows: Int = ROWS): Float {
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) return 0f
        val byWidth = viewportWidth / widthInCells(sizing)
        if (availableHeight <= 0f || !availableHeight.isFinite()) return byWidth
        return min(byWidth, availableHeight / blockHeightInCells(sizing, rows))
    }

    /**
     * The board's width in cells at [sizing] — what the viewport's width has to hold.
     *
     * One board is [COLUMNS] columns wide; the split is [SPLIT_WIDTH_IN_CELLS], and the channel is
     * the width left over rather than a reserved band, so nothing but the halves' own spans sets the
     * cell's width bound.
     */
    fun widthInCells(sizing: Sizing): Float =
        if (sizing.split) SPLIT_WIDTH_IN_CELLS else COLUMNS.toFloat()

    /** True when the frame is too short for the width-derived cell, so the height is what binds. */
    fun heightBinds(viewportWidth: Float, availableHeight: Float, sizing: Sizing, rows: Int = ROWS): Boolean =
        cellWidthFor(viewportWidth, availableHeight, sizing, rows) < viewportWidth / widthInCells(sizing)

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
        /** The empty channel between the halves of a split board; null on the single board. */
        val channel: Channel? = null,
    ) {
        /** True when the board fills the viewport's width and has no side margins. */
        val spansViewport: Boolean get() = leftInset <= 0.001f

        /** The hexagon ratio (cap height / cell width) this placement was drawn at. */
        val heightRatio: Float get() = sizing.heightRatio

        /** True when this is the two-handed split — the halves anchored to opposite edges. */
        val split: Boolean get() = sizing.split
    }

    /**
     * Places the board into a viewport: the single board centred horizontally, or — when
     * [Sizing.split] — the two-handed split, each half anchored to its own edge.
     *
     * The scale is applied to the cell and the row height follows from the same ratio, so the caps
     * keep their hexagon — a stretched honeycomb is the one outcome the fit rule exists to prevent.
     * The single board is centred rather than left-aligned because the empty space either side of a
     * scaled-down board is a margin, and a margin that only appears on the right looks like a bug; on
     * the split the empty space is the channel, between the halves and by design.
     *
     * The sizing is passed through [fittedSizing] first, once, and everything below — the cell, the
     * rows, the block the placement reports — is read from what comes back. That is what makes the
     * portrait clamp a single seam rather than a check somebody has to remember: a stepped-up board
     * in a frame too short for it comes out with its ten columns intact and its hexagons flattened,
     * and the placement's own [HexPlacement.sizing] is the geometry it was actually drawn at.
     */
    fun place(
        layout: KeyboardLayout,
        viewportWidth: Float,
        availableHeight: Float,
        sizing: Sizing,
        topOffset: Float = 0f,
    ): HexPlacement {
        val rows = layout.rows.size
        val drawn = fittedSizing(sizing, viewportWidth, availableHeight, rows)
        if (drawn.split) return placeSplit(layout, viewportWidth, availableHeight, drawn, topOffset)
        val cellWidth = cellWidthFor(viewportWidth, availableHeight, drawn, rows)
        val boardWidth = cellWidth * COLUMNS
        val leftInset = ((viewportWidth - boardWidth) * 0.5f).coerceAtLeast(0f)
        val cellHeight = cellHeight(cellWidth, drawn)
        val placed = KeyGeometry.place(
            layout = layout,
            width = boardWidth,
            contentHeight = rows * cellHeight,
            // The caps overhang their cells; reserving that here keeps the top row's points inside
            // the view instead of clipped against its edge.
            topOffset = topOffset + topOverhang(cellHeight),
        ).map { it.shiftedBy(leftInset) }
        val naturalCell = if (viewportWidth > 0f) viewportWidth / COLUMNS else cellWidth
        return HexPlacement(
            cellWidth = cellWidth,
            boardWidth = boardWidth,
            // The block is a fixed number of cells tall, so it scales with the cell exactly.
            boardHeight = blockHeight(cellWidth, drawn, rows),
            leftInset = leftInset,
            scale = if (naturalCell > 0f) cellWidth / naturalCell else 1f,
            sizing = drawn,
            keys = placed,
        )
    }

    /**
     * Places the two-handed split: the halves of [splitHalves] laid out at the cell the available
     * height yields, each anchored to its own screen edge, with the channel left empty between them.
     *
     * The width is not spent on the cell here — the halves are pinned to the edges and the free width
     * becomes the channel — so the only thing the width puts on the cell is that the halves must
     * still not overlap ([splitCellWidth]). At 2400 x 1080 that bound is slack: the height's 486 px
     * block decides a 157.4 px cell, each half reaches 787 px from its edge, and the 747 px channel
     * is whatever is left. The keys span the full viewport (the halves' outer rows sit on the edges),
     * so there is no side margin, and the channel is the whole of the space between them.
     */
    private fun placeSplit(
        layout: KeyboardLayout,
        viewportWidth: Float,
        availableHeight: Float,
        sizing: Sizing,
        topOffset: Float,
    ): HexPlacement {
        val rows = layout.rows.size
        val halves = splitHalves(layout)
        val cellWidth = splitCellWidth(viewportWidth, availableHeight, sizing, halves.spanInCells, rows)
        val cellHeight = cellHeight(cellWidth, sizing)
        val top = topOffset + topOverhang(cellHeight)
        val keys = ArrayList<PlacedKey>(
            halves.left.rows.sumOf { it.keys.size } + halves.right.rows.sumOf { it.keys.size },
        )
        placeHalf(halves.left, keys, fromRight = false, cellWidth, cellHeight, top, viewportWidth)
        placeHalf(halves.right, keys, fromRight = true, cellWidth, cellHeight, top, viewportWidth)
        val naturalCell = if (viewportWidth > 0f) viewportWidth / COLUMNS else cellWidth
        return HexPlacement(
            cellWidth = cellWidth,
            boardWidth = viewportWidth,
            boardHeight = blockHeight(cellWidth, sizing, rows),
            leftInset = 0f,
            scale = if (naturalCell > 0f) cellWidth / naturalCell else 1f,
            sizing = sizing,
            keys = keys,
            // Each half's reach is where its inner edge is, so the channel opens exactly where the
            // tightest pair of keys ends — a future cap or phase change moves both together.
            channel = Channel(
                start = halves.left.spanInCells * cellWidth,
                end = viewportWidth - halves.right.spanInCells * cellWidth,
            ),
        )
    }

    /**
     * Places one half's rows into [out], anchored to that half's own outer edge.
     *
     * A left half grows right from x = 0; a right half grows left from x = [viewportWidth], which is
     * the mirror. Either way each row starts [SplitRow.outerGap] cells in from that edge, so the phase
     * a row had on the single board is the phase it keeps here, only read from the other side.
     */
    private fun placeHalf(
        half: SplitHalf,
        out: MutableList<PlacedKey>,
        fromRight: Boolean,
        cellWidth: Float,
        cellHeight: Float,
        top: Float,
        viewportWidth: Float,
    ) {
        var y = top
        half.rows.forEachIndexed { rowIndex, row ->
            val rowWidth = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat() * cellWidth
            var x = if (fromRight) {
                viewportWidth - row.outerGap * cellWidth - rowWidth
            } else {
                row.outerGap * cellWidth
            }
            row.keys.forEach { key ->
                val width = key.widthWeight * cellWidth
                out += PlacedKey(key, x, y, x + width, y + cellHeight, rowIndex)
                x += width
            }
            y += cellHeight
        }
    }

    /**
     * The largest cell the split board can use: the height's block ([availableHeight] over the
     * block-in-cells), held so the two halves still fit across [viewportWidth] without their inner
     * rows touching. The halves are edge-anchored, so the width is spent on the channel rather than
     * on the cell; this is the only bound the width puts on it.
     */
    private fun splitCellWidth(
        viewportWidth: Float,
        availableHeight: Float,
        sizing: Sizing,
        spanInCells: Float,
        rows: Int = ROWS,
    ): Float {
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) return 0f
        val byWidth = if (spanInCells > 0f) viewportWidth / spanInCells else viewportWidth / COLUMNS
        if (availableHeight <= 0f || !availableHeight.isFinite()) return byWidth
        return min(byWidth, availableHeight / blockHeightInCells(sizing, rows))
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
